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

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.Rule;
import com.alibaba.csp.sentinel.slots.system.SystemRule;

/**
 * <p>Sentinel的核心API，用于记录资源统计信息并执行规则检查。</p>
 * <p>
 * 从概念上讲，需要保护的物理或逻辑资源应该被entry包围。当满足任何限制条件时，
 * 对该资源的请求将被阻塞，例如当任何{@link Rule}的阈值被超过时。一旦被阻塞，
 * 将抛出{@link BlockException}异常。
 * </p>
 * <p>
 * 要配置限制条件，我们可以使用<code>XxxRuleManager.loadRules()</code>来加载规则。
 * </p>
 *
 * <p>
 * 以下代码是一个示例，{@code "abc"}代表受保护资源的唯一名称：
 * </p>
 *
 * <pre>
 *  public void foo() {
 *     Entry entry = null;
 *     try {
 *        entry = SphU.entry("abc");
 *        // 需要保护的资源
 *     } catch (BlockException blockException) {
 *         // 当进入此处时，表示被阻塞
 *         // 在此添加被阻塞的处理逻辑
 *     } catch (Throwable bizException) {
 *         // 业务异常
 *         Tracer.trace(bizException);
 *     } finally {
 *         // 确保finally被执行
 *         if (entry != null){
 *             entry.exit();
 *         }
 *     }
 *  }
 * </pre>
 *
 * <p>
 * 确保{@code SphU.entry()}和{@link Entry#exit()}在同一个线程中配对使用，
 * 否则将抛出{@link ErrorEntryFreeException}异常。
 * </p>
 */
public class SphU {

    private static final Object[] OBJECTS0 = new Object[0];

    private SphU() {}

    /**
     * 记录统计信息并对给定资源执行规则检查。
     *
     * @param name 受保护资源的唯一名称
     * @return 此调用的{@link Entry}（用于标记调用完成并获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如，指标超过任何规则的阈值）
     */
    public static Entry entry(String name) throws BlockException {
        return Env.sph.entry(name, EntryType.OUT, 1, OBJECTS0);
    }

    /**
     * 检查关于受保护方法的所有{@link Rule}。
     *
     * @param method 受保护的方法
     * @return 此调用的{@link Entry}（用于标记调用完成并获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如，指标超过任何规则的阈值）
     */
    public static Entry entry(Method method) throws BlockException {
        return Env.sph.entry(method, EntryType.OUT, 1, OBJECTS0);
    }

    /**
     * 检查关于受保护方法的所有{@link Rule}。
     *
     * @param method     受保护的方法
     * @param batchCount 调用中的请求数量（例如，batchCount=2表示请求2个令牌）
     * @return 此调用的{@link Entry}（用于标记调用完成并获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如，指标超过任何规则的阈值）
     */
    public static Entry entry(Method method, int batchCount) throws BlockException {
        return Env.sph.entry(method, EntryType.OUT, batchCount, OBJECTS0);
    }

    /**
     * 记录统计信息并对给定资源执行规则检查。
     *
     * @param name       资源的唯一字符串
     * @param batchCount 调用中的请求数量（例如，batchCount=2表示请求2个令牌）
     * @return 此调用的{@link Entry}（用于标记调用完成并获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如，指标超过任何规则的阈值）
     */
    public static Entry entry(String name, int batchCount) throws BlockException {
        return Env.sph.entry(name, EntryType.OUT, batchCount, OBJECTS0);
    }

    /**
     * 检查关于受保护方法的所有{@link Rule}。
     *
     * @param method      受保护的方法
     * @param trafficType 流量类型（入站、出站或内部）。用于标记系统不稳定时是否可以被阻塞，
     *                    只有入站流量可以被{@link SystemRule}阻塞
     * @throws BlockException 如果满足阻塞条件（例如，指标超过任何规则的阈值）
     */
    public static Entry entry(Method method, EntryType trafficType) throws BlockException {
        return Env.sph.entry(method, trafficType, 1, OBJECTS0);
    }

    /**
     * 记录统计信息并对给定资源执行规则检查。
     *
     * @param name        受保护资源的唯一名称
     * @param trafficType 流量类型（入站、出站或内部）。用于标记系统不稳定时是否可以被阻塞，
     *                    只有入站流量可以被{@link SystemRule}阻塞
     * @throws BlockException 如果满足阻塞条件（例如，指标超过任何规则的阈值）
     */
    public static Entry entry(String name, EntryType trafficType) throws BlockException {
        return Env.sph.entry(name, trafficType, 1, OBJECTS0);
    }

    /**
     * 检查关于受保护方法的所有{@link Rule}。
     *
     * @param method      受保护的方法
     * @param trafficType 流量类型（入站、出站或内部）。用于标记系统不稳定时是否可以被阻塞，
     *                    只有入站流量可以被{@link SystemRule}阻塞
     * @param batchCount  调用中的请求数量（例如，batchCount=2表示请求2个令牌）
     * @throws BlockException 如果满足阻塞条件（例如，指标超过任何规则的阈值）
     */
    public static Entry entry(Method method, EntryType trafficType, int batchCount) throws BlockException {
        return Env.sph.entry(method, trafficType, batchCount, OBJECTS0);
    }

    /**
     * 记录统计信息并对给定资源执行规则检查。
     *
     * @param name        受保护资源的唯一名称
     * @param trafficType 流量类型（入站、出站或内部）。用于标记系统不稳定时是否可以被阻塞，
     *                    只有入站流量可以被{@link SystemRule}阻塞
     * @param batchCount  调用中的请求数量（例如，batchCount=2表示请求2个令牌）
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     */
    public static Entry entry(String name, EntryType trafficType, int batchCount) throws BlockException {
        return Env.sph.entry(name, trafficType, batchCount, OBJECTS0);
    }

    /**
     * Checking all {@link Rule}s about the protected method.
     *
     * @param method      the protected method
     * @param trafficType the traffic type (inbound, outbound or internal). This is used
     *                    to mark whether it can be blocked when the system is unstable,
     *                    only inbound traffic could be blocked by {@link SystemRule}
     * @param batchCount  the amount of calls within the invocation (e.g. batchCount=2 means request for 2 tokens)
     * @param args        args for parameter flow control or customized slots
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     */
    public static Entry entry(Method method, EntryType trafficType, int batchCount, Object... args)
        throws BlockException {
        return Env.sph.entry(method, trafficType, batchCount, args);
    }

    /**
     * Record statistics and perform rule checking for the given resource.
     *
     * @param name        the unique name for the protected resource
     * @param trafficType the traffic type (inbound, outbound or internal). This is used
     *                    to mark whether it can be blocked when the system is unstable,
     *                    only inbound traffic could be blocked by {@link SystemRule}
     * @param batchCount  the amount of calls within the invocation (e.g. batchCount=2 means request for 2 tokens)
     * @param args        args for parameter flow control
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     */
    public static Entry entry(String name, EntryType trafficType, int batchCount, Object... args)
        throws BlockException {
        return Env.sph.entry(name, trafficType, batchCount, args);
    }

    /**
     * Record statistics and check all rules of the resource that indicates an async invocation.
     *
     * @param name the unique name of the protected resource
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     * @since 0.2.0
     */
    public static AsyncEntry asyncEntry(String name) throws BlockException {
        return Env.sph.asyncEntry(name, EntryType.OUT, 1, OBJECTS0);
    }

    /**
     * Record statistics and check all rules of the resource that indicates an async invocation.
     *
     * @param name        the unique name for the protected resource
     * @param trafficType the traffic type (inbound, outbound or internal). This is used
     *                    to mark whether it can be blocked when the system is unstable,
     *                    only inbound traffic could be blocked by {@link SystemRule}
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     * @since 0.2.0
     */
    public static AsyncEntry asyncEntry(String name, EntryType trafficType) throws BlockException {
        return Env.sph.asyncEntry(name, trafficType, 1, OBJECTS0);
    }

    /**
     * Record statistics and check all rules of the resource that indicates an async invocation.
     *
     * @param name        the unique name for the protected resource
     * @param trafficType the traffic type (inbound, outbound or internal). This is used
     *                    to mark whether it can be blocked when the system is unstable,
     *                    only inbound traffic could be blocked by {@link SystemRule}
     * @param batchCount  the amount of calls within the invocation (e.g. batchCount=2 means request for 2 tokens)
     * @param args        args for parameter flow control
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     * @since 0.2.0
     */
    public static AsyncEntry asyncEntry(String name, EntryType trafficType, int batchCount, Object... args)
        throws BlockException {
        return Env.sph.asyncEntry(name, trafficType, batchCount, args);
    }

    /**
     * Record statistics and perform rule checking for the given resource. The entry is prioritized.
     *
     * @param name the unique name for the protected resource
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     * @since 1.4.0
     */
    public static Entry entryWithPriority(String name) throws BlockException {
        return Env.sph.entryWithPriority(name, EntryType.OUT, 1, true);
    }

    /**
     * Record statistics and perform rule checking for the given resource. The entry is prioritized.
     *
     * @param name        the unique name for the protected resource
     * @param trafficType the traffic type (inbound, outbound or internal). This is used
     *                    to mark whether it can be blocked when the system is unstable,
     *                    only inbound traffic could be blocked by {@link SystemRule}
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     * @since 1.4.0
     */
    public static Entry entryWithPriority(String name, EntryType trafficType) throws BlockException {
        return Env.sph.entryWithPriority(name, trafficType, 1, true);
    }

    /**
     * Record statistics and perform rule checking for the given resource.
     *
     * @param name         the unique name for the protected resource
     * @param resourceType classification of the resource (e.g. Web or RPC)
     * @param trafficType  the traffic type (inbound, outbound or internal). This is used
     *                     to mark whether it can be blocked when the system is unstable,
     *                     only inbound traffic could be blocked by {@link SystemRule}
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     * @since 1.7.0
     */
    public static Entry entry(String name, int resourceType, EntryType trafficType) throws BlockException {
        return Env.sph.entryWithType(name, resourceType, trafficType, 1, OBJECTS0);
    }

    /**
     * Record statistics and perform rule checking for the given resource.
     *
     * @param name         the unique name for the protected resource
     * @param trafficType  the traffic type (inbound, outbound or internal). This is used
     *                     to mark whether it can be blocked when the system is unstable,
     *                     only inbound traffic could be blocked by {@link SystemRule}
     * @param resourceType classification of the resource (e.g. Web or RPC)
     * @param args         args for parameter flow control or customized slots
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     * @since 1.7.0
     */
    public static Entry entry(String name, int resourceType, EntryType trafficType, Object[] args)
        throws BlockException {
        return Env.sph.entryWithType(name, resourceType, trafficType, 1, args);
    }

    /**
     * Record statistics and perform rule checking for the given resource that indicates an async invocation.
     *
     * @param name         the unique name for the protected resource
     * @param trafficType  the traffic type (inbound, outbound or internal). This is used
     *                     to mark whether it can be blocked when the system is unstable,
     *                     only inbound traffic could be blocked by {@link SystemRule}
     * @param resourceType classification of the resource (e.g. Web or RPC)
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     * @since 1.7.0
     */
    public static AsyncEntry asyncEntry(String name, int resourceType, EntryType trafficType)
        throws BlockException {
        return Env.sph.asyncEntryWithType(name, resourceType, trafficType, 1, false, OBJECTS0);
    }

    /**
     * Record statistics and perform rule checking for the given resource that indicates an async invocation.
     *
     * @param name         the unique name for the protected resource
     * @param trafficType  the traffic type (inbound, outbound or internal). This is used
     *                     to mark whether it can be blocked when the system is unstable,
     *                     only inbound traffic could be blocked by {@link SystemRule}
     * @param resourceType classification of the resource (e.g. Web or RPC)
     * @param args         args for parameter flow control or customized slots
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     * @since 1.7.0
     */
    public static AsyncEntry asyncEntry(String name, int resourceType, EntryType trafficType, Object[] args)
        throws BlockException {
        return Env.sph.asyncEntryWithType(name, resourceType, trafficType, 1, false, args);
    }

    /**
     * Record statistics and perform rule checking for the given resource that indicates an async invocation.
     *
     * @param name         the unique name for the protected resource
     * @param trafficType  the traffic type (inbound, outbound or internal). This is used
     *                     to mark whether it can be blocked when the system is unstable,
     *                     only inbound traffic could be blocked by {@link SystemRule}
     * @param resourceType classification of the resource (e.g. Web or RPC)
     * @param batchCount   the amount of calls within the invocation (e.g. batchCount=2 means request for 2 tokens)
     * @param args         args for parameter flow control or customized slots
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException if the block criteria is met (e.g. metric exceeded the threshold of any rules)
     * @since 1.7.0
     */
    public static AsyncEntry asyncEntry(String name, int resourceType, EntryType trafficType, int batchCount,
                                        Object[] args) throws BlockException {
        return Env.sph.asyncEntryWithType(name, resourceType, trafficType, batchCount, false, args);
    }
}
