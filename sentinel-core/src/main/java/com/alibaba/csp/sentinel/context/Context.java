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
package com.alibaba.csp.sentinel.context;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphO;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.node.EntranceNode;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot;

/**
 * This class holds metadata of current invocation:<br/>
 * 该类持有当前调用的元数据信息：<br/>
 *
 * <ul>
 * <li>the {@link EntranceNode}: the root of the current invocation
 * tree.</li>
 * <li>入口节点：当前调用树的根节点。</li>
 * <li>the current {@link Entry}: the current invocation point.</li>
 * <li>当前Entry：当前调用点。</li>
 * <li>the current {@link Node}: the statistics related to the
 * {@link Entry}.</li>
 * <li>当前节点：与当前Entry相关的统计信息节点。</li>
 * <li>the origin: The origin is useful when we want to control different
 * invoker/consumer separately. Usually the origin could be the Service Consumer's app name
 * or origin IP. </li>
 * <li>来源：当我们需要对不同的调用方/消费者进行分别控制时，来源信息非常有用。通常来源可以是服务消费者的应用名称或来源IP。</li>
 * </ul>
 * <p>
 * Each {@link SphU}#entry() or {@link SphO}#entry() should be in a {@link Context},
 * if we don't invoke {@link ContextUtil}#enter() explicitly, DEFAULT context will be used.
 * </p>
 * <p>
 * 每个 {@link SphU}#entry() 或 {@link SphO}#entry() 调用都应该在一个 {@link Context} 中，
 * 如果我们没有显式调用 {@link ContextUtil}#enter()，将使用默认上下文。
 * </p>
 * <p>
 * A invocation tree will be created if we invoke {@link SphU}#entry() multi times in
 * the same context.
 * </p>
 * <p>
 * 如果我们在同一个上下文中多次调用 {@link SphU}#entry()，将创建一个调用树。
 * </p>
 * <p>
 * Same resource in different context will count separately, see {@link NodeSelectorSlot}.
 * </p>
 * <p>
 * 不同上下文中的相同资源将被分别统计，参见 {@link NodeSelectorSlot}。
 * </p>
 *
 * @author jialiang.linjl
 * @author leyou(lihao)
 * @author Eric Zhao
 * @see ContextUtil
 * @see NodeSelectorSlot
 */
public class Context {

    /**
     * Context name.
     * 上下文名称。
     */
    private final String name;

    /**
     * The entrance node of current invocation tree.
     * 当前调用树的入口节点。
     */
    private DefaultNode entranceNode;

    /**
     * Current processing entry.
     * 当前正在处理的Entry。
     */
    private Entry curEntry;

    /**
     * The origin of this context (usually indicate different invokers, e.g. service consumer name or origin IP).
     * 此上下文的来源（通常表示不同的调用者，例如服务消费者名称或来源IP）。
     */
    private String origin = "";

    /**
     * Whether this context is async.
     * 此上下文是否为异步。
     */
    private final boolean async;

    /**
     * Create a new async context.
     * 创建一个新的异步上下文。
     *
     * @param entranceNode entrance node of the context 上下文的入口节点
     * @param name context name 上下文名称
     * @return the new created context 新创建的上下文
     * @since 0.2.0
     */
    public static Context newAsyncContext(DefaultNode entranceNode, String name) {
        return new Context(name, entranceNode, true);
    }

    public Context(DefaultNode entranceNode, String name) {
        this(name, entranceNode, false);
    }

    public Context(String name, DefaultNode entranceNode, boolean async) {
        this.name = name;
        this.entranceNode = entranceNode;
        this.async = async;
    }

    /**
     * Check if this context is async.
     * 检查此上下文是否为异步。
     * 
     * @return true if this context is async
     */
    public boolean isAsync() {
        return async;
    }

    /**
     * Get the name of this context.
     * 获取此上下文的名称。
     * 
     * @return name of this context
     */
    public String getName() {
        return name;
    }

    /**
     * Get current node of this context.
     * 获取此上下文的当前节点。
     * 
     * @return current node
     */
    public Node getCurNode() {
        return curEntry == null ? null : curEntry.getCurNode();
    }

    /**
     * Set current node of this context.
     * 设置此上下文的当前节点。
     * 
     * @param node the node to set
     * @return this context
     */
    public Context setCurNode(Node node) {
        this.curEntry.setCurNode(node);
        return this;
    }

    /**
     * Get current entry of this context.
     * 获取此上下文的当前Entry。
     * 
     * @return current entry
     */
    public Entry getCurEntry() {
        return curEntry;
    }

    /**
     * Set current entry of this context.
     * 设置此上下文的当前Entry。
     * 
     * @param curEntry the entry to set
     * @return this context
     */
    public Context setCurEntry(Entry curEntry) {
        this.curEntry = curEntry;
        return this;
    }

    /**
     * Get the origin of this context.
     * 获取此上下文的来源。
     * 
     * @return origin of this context
     */
    public String getOrigin() {
        return origin;
    }

    /**
     * Set the origin of this context.
     * 设置此上下文的来源。
     * 
     * @param origin the origin to set
     * @return this context
     */
    public Context setOrigin(String origin) {
        this.origin = origin;
        return this;
    }

    /**
     * Get total QPS of origin node.
     * 获取来源节点的总QPS。
     * 
     * @return total QPS of origin node
     */
    public double getOriginTotalQps() {
        return getOriginNode() == null ? 0 : getOriginNode().totalQps();
    }

    /**
     * Get block QPS of origin node.
     * 获取来源节点的拦截QPS。
     * 
     * @return block QPS of origin node
     */
    public double getOriginBlockQps() {
        return getOriginNode() == null ? 0 : getOriginNode().blockQps();
    }

    /**
     * Get success QPS of origin node.
     * 获取来源节点的成功QPS。
     * 
     * @return success QPS of origin node
     */
    public double getOriginPassReqQps() {
        return getOriginNode() == null ? 0 : getOriginNode().successQps();
    }

    /**
     * Get pass QPS of origin node.
     * 获取来源节点的通过QPS。
     * 
     * @return pass QPS of origin node
     */
    public double getOriginPassQps() {
        return getOriginNode() == null ? 0 : getOriginNode().passQps();
    }

    /**
     * Get total request count of origin node.
     * 获取来源节点的总请求数。
     * 
     * @return total request count of origin node
     */
    public long getOriginTotalRequest() {
        return getOriginNode() == null ? 0 : getOriginNode().totalRequest();
    }

    /**
     * Get block request count of origin node.
     * 获取来源节点的拦截请求数。
     * 
     * @return block request count of origin node
     */
    public long getOriginBlockRequest() {
        return getOriginNode() == null ? 0 : getOriginNode().blockRequest();
    }

    /**
     * Get average response time of origin node.
     * 获取来源节点的平均响应时间。
     * 
     * @return average response time of origin node
     */
    public double getOriginAvgRt() {
        return getOriginNode() == null ? 0 : getOriginNode().avgRt();
    }

    /**
     * Get current thread count of origin node.
     * 获取来源节点的当前线程数。
     * 
     * @return current thread count of origin node
     */
    public int getOriginCurThreadNum() {
        return getOriginNode() == null ? 0 : getOriginNode().curThreadNum();
    }

    /**
     * Get the entrance node of this context.
     * 获取此上下文的入口节点。
     * 
     * @return entrance node of this context
     */
    public DefaultNode getEntranceNode() {
        return entranceNode;
    }

    /**
     * Get the parent {@link Node} of the current.
     * 获取当前节点的父节点。
     *
     * @return the parent node of the current. 当前节点的父节点
     */
    public Node getLastNode() {
        if (curEntry != null && curEntry.getLastNode() != null) {
            return curEntry.getLastNode();
        } else {
            return entranceNode;
        }
    }

    /**
     * Get the origin node of this context.
     * 获取此上下文的来源节点。
     * 
     * @return origin node of this context, may be null if no origin specified
     */
    public Node getOriginNode() {
        return curEntry == null ? null : curEntry.getOriginNode();
    }

    /**
     * Returns a string representation of this context.
     * 返回此上下文的字符串表示形式。
     * 
     * @return a string representation of this context
     */
    @Override
    public String toString() {
        return "Context{" +
            "name='" + name + '\'' +
            ", entranceNode=" + entranceNode +
            ", curEntry=" + curEntry +
            ", origin='" + origin + '\'' +
            ", async=" + async +
            '}';
    }
}
