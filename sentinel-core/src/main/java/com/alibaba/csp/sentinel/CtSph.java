/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.context.NullContext;
import com.alibaba.csp.sentinel.slotchain.MethodResourceWrapper;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slotchain.SlotChainProvider;
import com.alibaba.csp.sentinel.slotchain.StringResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.Rule;

/**
 * CtSph是Sentinel框架的核心实现类，实现了Sph接口，负责资源的统计和规则检查。
 * 
 * <p>该类是Sentinel的核心处理器，管理着资源与处理器槽链(ProcessorSlotChain)的映射关系。
 * 每个资源都会关联一个全局唯一的ProcessorSlotChain，用于执行资源的规则检查和统计。</p>
 * 
 * <p>CtSph处理的主要职责包括：</p>
 * <ul>
 *   <li>创建和管理资源的处理器槽链</li>
 *   <li>执行资源的规则检查（限流、熔断等）</li>
 *   <li>处理同步和异步资源的调用</li>
 *   <li>维护资源的上下文关系</li>
 * </ul>
 *
 * <p>该类是线程安全的，通过volatile和同步机制确保在高并发环境下的正确性。</p>
 *
 * @author jialiang.linjl
 * @author leyou(lihao)
 * @author Eric Zhao
 * @see Sph
 */
public class CtSph implements Sph {

    private static final Object[] OBJECTS0 = new Object[0];

    /**
     * 资源与处理器槽链的映射关系。
     * 
     * <p>相同的资源({@link ResourceWrapper#equals(Object)})将共享相同的
     * {@link ProcessorSlotChain}，无论在哪个{@link Context}中。这确保了对同一资源的
     * 规则检查和统计是全局一致的。</p>
     */
    private static volatile Map<ResourceWrapper, ProcessorSlotChain> chainMap
        = new HashMap<ResourceWrapper, ProcessorSlotChain>();

    private static final Object LOCK = new Object();

    /**
     * 创建一个没有处理器槽链的异步Entry。
     * 
     * <p>当系统无法执行规则检查时（如上下文数量超过阈值、全局开关关闭等情况），
     * 将创建一个不执行规则检查的异步Entry。</p>
     * 
     * @param resourceWrapper 资源包装器
     * @param context 当前上下文
     * @return 异步Entry对象
     */
    private AsyncEntry asyncEntryWithNoChain(ResourceWrapper resourceWrapper, Context context) {
        AsyncEntry entry = new AsyncEntry(resourceWrapper, null, context);
        entry.initAsyncContext();
        // The async entry will be removed from current context as soon as it has been created.
        entry.cleanCurrentEntryInLocal();
        return entry;
    }

    /**
     * 创建一个带优先级的异步Entry的内部实现。
     * 
     * <p>该方法处理异步资源的规则检查，支持优先级设置。异步Entry创建后会从当前上下文中移除，
     * 以避免异步调用阻塞当前上下文。</p>
     * 
     * @param resourceWrapper 资源包装器
     * @param count 请求的令牌数量
     * @param prioritized 是否优先处理
     * @param args 用户方法调用的参数
     * @return 异步Entry对象
     * @throws BlockException 如果规则检查不通过
     */
    private AsyncEntry asyncEntryWithPriorityInternal(ResourceWrapper resourceWrapper, int count, boolean prioritized,
                                                      Object... args) throws BlockException {
        Context context = ContextUtil.getContext();
        if (context instanceof NullContext) {
            // The {@link NullContext} indicates that the amount of context has exceeded the threshold,
            // so here init the entry only. No rule checking will be done.
            return asyncEntryWithNoChain(resourceWrapper, context);
        }
        if (context == null) {
            // Using default context.
            context = InternalContextUtil.internalEnter(Constants.CONTEXT_DEFAULT_NAME);
        }

        // Global switch is turned off, so no rule checking will be done.
        if (!Constants.ON) {
            return asyncEntryWithNoChain(resourceWrapper, context);
        }

        ProcessorSlot<Object> chain = lookProcessChain(resourceWrapper);

        // Means processor cache size exceeds {@link Constants.MAX_SLOT_CHAIN_SIZE}, so no rule checking will be done.
        if (chain == null) {
            return asyncEntryWithNoChain(resourceWrapper, context);
        }

        AsyncEntry asyncEntry = new AsyncEntry(resourceWrapper, chain, context, count, args);
        try {
            chain.entry(context, resourceWrapper, null, count, prioritized, args);
            // Initiate the async context only when the entry successfully passed the slot chain.
            asyncEntry.initAsyncContext();
            // The asynchronous call may take time in background, and current context should not be hanged on it.
            // So we need to remove current async entry from current context.
            asyncEntry.cleanCurrentEntryInLocal();
        } catch (BlockException e1) {
            // When blocked, the async entry will be exited on current context.
            // The async context will not be initialized.
            asyncEntry.exitForContext(context, count, args);
            throw e1;
        } catch (Throwable e1) {
            // This should not happen, unless there are errors existing in Sentinel internal.
            // When this happens, async context is not initialized.
            RecordLog.warn("Sentinel unexpected exception in asyncEntryInternal", e1);

            asyncEntry.cleanCurrentEntryInLocal();
        }
        return asyncEntry;
    }

    /**
     * 创建一个异步Entry的内部实现。
     * 
     * <p>该方法是异步资源处理的简化版本，不考虑优先级。</p>
     * 
     * @param resourceWrapper 资源包装器
     * @param count 请求的令牌数量
     * @param args 用户方法调用的参数
     * @return 异步Entry对象
     * @throws BlockException 如果规则检查不通过
     */
    private AsyncEntry asyncEntryInternal(ResourceWrapper resourceWrapper, int count, Object... args)
        throws BlockException {
        return asyncEntryWithPriorityInternal(resourceWrapper, count, false, args);
    }

    /**
     * 创建一个带优先级的Entry。
     * 
     * <p>该方法是同步资源处理的核心实现，支持优先级设置。它会检查上下文状态、全局开关，
     * 并查找或创建资源的处理器槽链，然后执行规则检查。</p>
     * 
     * @param resourceWrapper 资源包装器
     * @param count 请求的令牌数量
     * @param prioritized 是否优先处理
     * @param args 用户方法调用的参数
     * @return Entry对象
     * @throws BlockException 如果规则检查不通过
     */
    private Entry entryWithPriority(ResourceWrapper resourceWrapper, int count, boolean prioritized, Object... args)
        throws BlockException {
        //一个请求绑定一个线程，绑定一个Context
        Context context = ContextUtil.getContext();
        if (context instanceof NullContext) {
            //如果出现NullContext 表示当前系统中的context数量已经超出阈值。
            //意味着请求数量超过阈值 默认是2000。此时直接返回一个无需做规则检测的资源操作对象
            // The {@link NullContext} indicates that the amount of context has exceeded the threshold,
            // so here init the entry only. No rule checking will be done.
            return new CtEntry(resourceWrapper, null, context);
        }

        if (context == null) {
            // Using default context. 默认创建名称为sentinel_default_context 的context 并放入ThreadLocal
            //同时存放在ContextUtil contextNameNodeMap 以及 Constants.ROOT的子节点中
            context = InternalContextUtil.internalEnter(Constants.CONTEXT_DEFAULT_NAME);
        }

        // Global switch is close, no rule checking will do. 检测全局开关
        if (!Constants.ON) {
            return new CtEntry(resourceWrapper, null, context);
        }

        //查找SlotChain
        ProcessorSlot<Object> chain = lookProcessChain(resourceWrapper);

        /*
         * Means amount of resources (slot chain) exceeds {@link Constants.MAX_SLOT_CHAIN_SIZE},
         * so no rule checking will be done.
         * 如果为null 意味着chain数量超出了最大限制
         */
        if (chain == null) {
            return new CtEntry(resourceWrapper, null, context);
        }

        //创建一个资源操作对象
        Entry e = new CtEntry(resourceWrapper, chain, context, count, args);
        try {
            chain.entry(context, resourceWrapper, null, count, prioritized, args);
        } catch (BlockException e1) {
            e.exit(count, args);
            throw e1;
        } catch (Throwable e1) {
            // This should not happen, unless there are errors existing in Sentinel internal.
            RecordLog.info("Sentinel unexpected exception", e1);
        }
        return e;
    }

    /**
     * 执行资源的所有规则检查。
     *
     * <p>每个不同的资源将使用一个{@link ProcessorSlot}来执行规则检查。相同的资源将在全局范围内
     * 使用相同的{@link ProcessorSlot}。这确保了对同一资源的限流、熔断等规则在全局范围内是一致的。</p>
     *
     * <p>注意，处理器槽链的总数不能超过{@link Constants#MAX_SLOT_CHAIN_SIZE}，
     * 否则将不执行规则检查。在这种情况下，所有请求将直接通过，不进行检查或抛出异常。</p>
     *
     * <p>该方法是Sentinel资源保护的核心入口，通过它可以对资源进行限流、熔断等保护措施。</p>
     *
     * @param resourceWrapper 资源包装器
     * @param count           需要的令牌数量
     * @param args            用户方法调用的参数
     * @return {@link Entry} 代表此次调用的对象
     * @throws BlockException 如果超过任何规则的阈值
     */
    public Entry entry(ResourceWrapper resourceWrapper, int count, Object... args) throws BlockException {
        return entryWithPriority(resourceWrapper, count, false, args);
    }

    /**
     * 获取资源的处理器槽链。如果资源尚未关联处理器槽链，将创建一个新的。
     *
     * <p>相同的资源({@link ResourceWrapper#equals(Object)})将在全局范围内共享相同的
     * {@link ProcessorSlotChain}，无论在哪个{@link Context}中。这确保了对同一资源的
     * 规则检查和统计是全局一致的。</p>
     *
     * <p>该方法是Sentinel槽链管理的核心，它负责创建和维护资源与处理器槽链的映射关系。
     * 处理器槽链包含了一系列的处理器槽，如NodeSelectorSlot、ClusterBuilderSlot、
     * StatisticSlot、FlowSlot、DegradeSlot等，它们共同实现了资源的统计和规则检查。</p>
     *
     * <p>
     * 注意，处理器槽链的总数不能超过{@link Constants#MAX_SLOT_CHAIN_SIZE}，
     * 否则将返回null。
     * </p>
     *
     * @param resourceWrapper 目标资源
     * @return 资源的{@link ProcessorSlotChain}
     */
    ProcessorSlot<Object> lookProcessChain(ResourceWrapper resourceWrapper) {
        //从缓存Map中获取SlotChain
        ProcessorSlotChain chain = chainMap.get(resourceWrapper);
        //DCL 防止并发创建
        if (chain == null) {
            synchronized (LOCK) {
                chain = chainMap.get(resourceWrapper);
                if (chain == null) {
                    // Entry size limit. SLOT最大值 6000
                    if (chainMap.size() >= Constants.MAX_SLOT_CHAIN_SIZE) {
                        return null;
                    }

                    //通过SPI创建SlotChain，责任链
                    chain = SlotChainProvider.newSlotChain();
                    //Map典型操作 防止迭代稳定性问题。
                    Map<ResourceWrapper, ProcessorSlotChain> newMap = new HashMap<ResourceWrapper, ProcessorSlotChain>(
                        chainMap.size() + 1);
                    newMap.putAll(chainMap);
                    newMap.put(resourceWrapper, chain);
                    chainMap = newMap;
                }
            }
        }
        return chain;
    }

    /**
     * 获取已创建的槽链数量。
     * 
     * <p>该方法可用于监控系统中资源的数量，以及判断是否接近最大槽链数量限制。</p>
     *
     * @return 已创建的槽链数量
     * @since 0.2.0
     */
    public static int entrySize() {
        return chainMap.size();
    }

    /**
     * 重置槽链映射。仅用于内部测试。
     * 
     * <p>该方法会清空所有资源与处理器槽链的映射关系，通常只在测试环境中使用。</p>
     *
     * @since 0.2.0
     */
    static void resetChainMap() {
        chainMap.clear();
    }

    /**
     * 获取槽链映射。仅用于内部测试。
     * 
     * <p>该方法返回资源与处理器槽链的映射关系，通常只在测试环境中使用。</p>
     *
     * @return 资源与处理器槽链的映射关系
     * @since 0.2.0
     */
    static Map<ResourceWrapper, ProcessorSlotChain> getChainMap() {
        return chainMap;
    }

    /**
     * 内部上下文工具类，用于跳过上下文名称检查。
     * 
     * <p>该类继承自ContextUtil，提供了创建上下文的内部方法，不进行名称合法性检查，
     * 用于CtSph内部创建默认上下文。</p>
     */
    private final static class InternalContextUtil extends ContextUtil {
        static Context internalEnter(String name) {
            return trueEnter(name, "");
        }

        static Context internalEnter(String name, String origin) {
            return trueEnter(name, origin);
        }
    }

    @Override
    public Entry entry(String name) throws BlockException {
        StringResourceWrapper resource = new StringResourceWrapper(name, EntryType.OUT);
        return entry(resource, 1, OBJECTS0);
    }

    @Override
    public Entry entry(Method method) throws BlockException {
        MethodResourceWrapper resource = new MethodResourceWrapper(method, EntryType.OUT);
        return entry(resource, 1, OBJECTS0);
    }

    @Override
    public Entry entry(Method method, EntryType type) throws BlockException {
        MethodResourceWrapper resource = new MethodResourceWrapper(method, type);
        return entry(resource, 1, OBJECTS0);
    }

    @Override
    public Entry entry(String name, EntryType type) throws BlockException {
        StringResourceWrapper resource = new StringResourceWrapper(name, type);
        return entry(resource, 1, OBJECTS0);
    }

    @Override
    public Entry entry(Method method, EntryType type, int count) throws BlockException {
        MethodResourceWrapper resource = new MethodResourceWrapper(method, type);
        return entry(resource, count, OBJECTS0);
    }

    @Override
    public Entry entry(String name, EntryType type, int count) throws BlockException {
        StringResourceWrapper resource = new StringResourceWrapper(name, type);
        return entry(resource, count, OBJECTS0);
    }

    @Override
    public Entry entry(Method method, int count) throws BlockException {
        MethodResourceWrapper resource = new MethodResourceWrapper(method, EntryType.OUT);
        return entry(resource, count, OBJECTS0);
    }

    @Override
    public Entry entry(String name, int count) throws BlockException {
        StringResourceWrapper resource = new StringResourceWrapper(name, EntryType.OUT);
        return entry(resource, count, OBJECTS0);
    }

    @Override
    public Entry entry(Method method, EntryType type, int count, Object... args) throws BlockException {
        MethodResourceWrapper resource = new MethodResourceWrapper(method, type);
        return entry(resource, count, args);
    }

    @Override
    public Entry entry(String name, EntryType type, int count, Object... args) throws BlockException {
        StringResourceWrapper resource = new StringResourceWrapper(name, type);
        return entry(resource, count, args);
    }

    @Override
    public AsyncEntry asyncEntry(String name, EntryType type, int count, Object... args) throws BlockException {
        StringResourceWrapper resource = new StringResourceWrapper(name, type);
        return asyncEntryInternal(resource, count, args);
    }

    @Override
    public Entry entryWithPriority(String name, EntryType type, int count, boolean prioritized) throws BlockException {
        StringResourceWrapper resource = new StringResourceWrapper(name, type);
        return entryWithPriority(resource, count, prioritized);
    }

    @Override
    public Entry entryWithPriority(String name, EntryType type, int count, boolean prioritized, Object... args)
        throws BlockException {
        StringResourceWrapper resource = new StringResourceWrapper(name, type);
        return entryWithPriority(resource, count, prioritized, args);
    }

    @Override
    public Entry entryWithType(String name, int resourceType, EntryType entryType, int count, Object[] args)
        throws BlockException {
        return entryWithType(name, resourceType, entryType, count, false, args);
    }

    @Override
    public Entry entryWithType(String name, int resourceType, EntryType entryType, int count, boolean prioritized,
                               Object[] args) throws BlockException {
        StringResourceWrapper resource = new StringResourceWrapper(name, entryType, resourceType);
        return entryWithPriority(resource, count, prioritized, args);
    }

    @Override
    public AsyncEntry asyncEntryWithType(String name, int resourceType, EntryType entryType, int count,
                                         boolean prioritized, Object[] args) throws BlockException {
        StringResourceWrapper resource = new StringResourceWrapper(name, entryType, resourceType);
        return asyncEntryWithPriorityInternal(resource, count, prioritized, args);
    }
}
