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
package com.alibaba.csp.sentinel.slots.block.flow;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.spi.Spi;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.function.Function;

import java.util.Collection;

/**
 * <p>
 * Combined the runtime statistics collected from the previous
 * slots (NodeSelectorSlot, ClusterNodeBuilderSlot, and StatisticSlot), FlowSlot
 * will use pre-set rules to decide whether the incoming requests should be
 * blocked.
 * </p>
 *
 * <p>
 * {@code SphU.entry(resourceName)} will throw {@code FlowException} if any rule is
 * triggered. Users can customize their own logic by catching {@code FlowException}.
 * </p>
 *
 * <p>
 * One resource can have multiple flow rules. FlowSlot traverses these rules
 * until one of them is triggered or all rules have been traversed.
 * </p>
 *
 * <p>
 * Each {@link FlowRule} is mainly composed of these factors: grade, strategy, path. We
 * can combine these factors to achieve different effects.
 * </p>
 *
 * <p>
 * The grade is defined by the {@code grade} field in {@link FlowRule}. Here, 0 for thread
 * isolation and 1 for request count shaping (QPS). Both thread count and request
 * count are collected in real runtime, and we can view these statistics by
 * following command:
 * </p>
 *
 * <pre>
 * curl http://localhost:8719/tree
 *
 * idx id    thread pass  blocked   success total aRt   1m-pass   1m-block   1m-all   exception
 * 2   abc647 0      460    46          46   1    27      630       276        897      0
 * </pre>
 *
 * <ul>
 * <li>{@code thread} for the count of threads that is currently processing the resource</li>
 * <li>{@code pass} for the count of incoming request within one second</li>
 * <li>{@code blocked} for the count of requests blocked within one second</li>
 * <li>{@code success} for the count of the requests successfully handled by Sentinel within one second</li>
 * <li>{@code RT} for the average response time of the requests within a second</li>
 * <li>{@code total} for the sum of incoming requests and blocked requests within one second</li>
 * <li>{@code 1m-pass} is for the count of incoming requests within one minute</li>
 * <li>{@code 1m-block} is for the count of a request blocked within one minute</li>
 * <li>{@code 1m-all} is the total of incoming and blocked requests within one minute</li>
 * <li>{@code exception} is for the count of business (customized) exceptions in one second</li>
 * </ul>
 *
 * This stage is usually used to protect resources from occupying. If a resource
 * takes long time to finish, threads will begin to occupy. The longer the
 * response takes, the more threads occupy.
 *
 * Besides counter, thread pool or semaphore can also be used to achieve this.
 *
 * - Thread pool: Allocate a thread pool to handle these resource. When there is
 * no more idle thread in the pool, the request is rejected without affecting
 * other resources.
 *
 * - Semaphore: Use semaphore to control the concurrent count of the threads in
 * this resource.
 *
 * The benefit of using thread pool is that, it can walk away gracefully when
 * time out. But it also bring us the cost of context switch and additional
 * threads. If the incoming requests is already served in a separated thread,
 * for instance, a Servlet HTTP request, it will almost double the threads count if
 * using thread pool.
 *
 * <h3>Traffic Shaping</h3>
 * <p>
 * When QPS exceeds the threshold, Sentinel will take actions to control the incoming request,
 * and is configured by {@code controlBehavior} field in flow rules.
 * </p>
 * <ol>
 * <li>Immediately reject ({@code RuleConstant.CONTROL_BEHAVIOR_DEFAULT})</li>
 * <p>
 * This is the default behavior. The exceeded request is rejected immediately
 * and the FlowException is thrown
 * </p>
 *
 * <li>Warmup ({@code RuleConstant.CONTROL_BEHAVIOR_WARM_UP})</li>
 * <p>
 * If the load of system has been low for a while, and a large amount of
 * requests comes, the system might not be able to handle all these requests at
 * once. However if we steady increase the incoming request, the system can warm
 * up and finally be able to handle all the requests.
 * This warmup period can be configured by setting the field {@code warmUpPeriodSec} in flow rules.
 * </p>
 *
 * <li>Uniform Rate Limiting ({@code RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER})</li>
 * <p>
 * This strategy strictly controls the interval between requests.
 * In other words, it allows requests to pass at a stable, uniform rate.
 * </p>
 * <img src="https://raw.githubusercontent.com/wiki/alibaba/Sentinel/image/uniform-speed-queue.png" style="max-width:
 * 60%;"/>
 * <p>
 * This strategy is an implement of <a href="https://en.wikipedia.org/wiki/Leaky_bucket">leaky bucket</a>.
 * It is used to handle the request at a stable rate and is often used in burst traffic (e.g. message handling).
 * When a large number of requests beyond the system’s capacity arrive
 * at the same time, the system using this strategy will handle requests and its
 * fixed rate until all the requests have been processed or time out.
 * </p>
 * </ol>
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */
/**
 * FlowSlot是Sentinel的核心流量控制组件，负责根据预设的流控规则决定请求是否应该被阻塞。
 * 它结合了前面插槽（NodeSelectorSlot、ClusterNodeBuilderSlot和StatisticSlot）收集的运行时统计信息，
 * 使用FlowRuleChecker来检查流控规则并决定是否阻塞请求。
 * 
 * 流控规则可以基于QPS或线程数进行控制，并支持多种控制行为：
 * 1. 直接拒绝（默认）：超过阈值的请求会被立即拒绝并抛出FlowException
 * 2. 预热（Warm Up）：系统从低水位到高水位逐渐增加通过的请求数
 * 3. 匀速排队：严格控制请求通过的间隔时间，以稳定的速率处理请求
 */
@Spi(order = Constants.ORDER_FLOW_SLOT)
public class FlowSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    private final FlowRuleChecker checker;

    public FlowSlot() {
        this(new FlowRuleChecker());
    }

    /**
     * Package-private for test.
     *
     * @param checker flow rule checker
     * @since 1.6.1
     */
    FlowSlot(FlowRuleChecker checker) {
        AssertUtil.notNull(checker, "flow checker should not be null");
        this.checker = checker;
    }

    /**
     * 入口方法，当资源被访问时调用
     * 首先检查流控规则，如果通过则继续执行责任链中的下一个插槽
     * 
     * @param context 当前调用的上下文环境
     * @param resourceWrapper 资源包装器，包含资源名称等信息
     * @param node 当前资源对应的节点
     * @param count 请求占用的资源数量，默认为1
     * @param prioritized 是否为优先级较高的请求
     * @param args 额外参数
     * @throws Throwable 如果流控规则检查不通过，会抛出FlowException
     */
    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count,
                      boolean prioritized, Object... args) throws Throwable {
        checkFlow(resourceWrapper, context, node, count, prioritized);

        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    /**
     * 检查流控规则
     * 该方法委托给FlowRuleChecker执行实际的流控规则检查
     * 如果任何一条规则被触发，将抛出FlowException异常
     * 
     * @param resource 资源包装器
     * @param context 调用上下文
     * @param node 当前资源的节点
     * @param count 请求占用的资源数量
     * @param prioritized 是否为优先级较高的请求
     * @throws BlockException 如果流控规则检查不通过，会抛出FlowException
     */
    void checkFlow(ResourceWrapper resource, Context context, DefaultNode node, int count, boolean prioritized)
        throws BlockException {
        checker.checkFlow(ruleProvider, resource, context, node, count, prioritized);
    }

    /**
     * 资源访问结束时的退出方法
     * 将退出事件传递给责任链中的下一个插槽
     * 
     * @param context 当前调用的上下文环境
     * @param resourceWrapper 资源包装器
     * @param count 结束的资源数量
     * @param args 额外参数
     */
    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        fireExit(context, resourceWrapper, count, args);
    }

    /**
     * 流控规则提供者，用于根据资源名称获取对应的流控规则集合
     * 实际是从FlowRuleManager中获取规则，FlowRuleManager负责规则的加载和更新
     */
    private final Function<String, Collection<FlowRule>> ruleProvider = new Function<String, Collection<FlowRule>>() {
        @Override
        public Collection<FlowRule> apply(String resource) {
            return FlowRuleManager.getFlowRules(resource);
        }
    };
}
