package com.alibaba.csp.sentinel.demo.cluster.handler;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.ClusterFlowConfig;
import com.alibaba.csp.sentinel.slots.block.ClusterRuleConstant;
import com.alibaba.csp.sentinel.demo.cluster.entity.SentinelFlowRule;
import com.alibaba.csp.sentinel.demo.cluster.entity.CustomizedFlowRule;

/**
 * Handler抽象类，实现Handler接口，提供创建FlowRule的方法
 */
public abstract class AbstractHandler implements Handler {
    
    /**
     * 创建流控规则
     * 
     * @param sentinelFlowRule SentinelFlowRule对象
     * @return 创建的流控规则
     */
    public FlowRule createFlowRule(SentinelFlowRule sentinelFlowRule) {
        FlowRule flowRule = new FlowRule(getResource());
        flowRule.setCount(sentinelFlowRule.getCount());
        flowRule.setGrade(sentinelFlowRule.getGrade());
        flowRule.setStrategy(sentinelFlowRule.getStrategy());
        
        // 当grade为QPS模式且启用集群模式时，创建ClusterFlowConfig对象
        if (sentinelFlowRule.getGrade() == RuleConstant.FLOW_GRADE_QPS && sentinelFlowRule.getClusterMode() == 1) {
            flowRule.setClusterMode(true);
            ClusterFlowConfig clusterConfig = new ClusterFlowConfig();
            clusterConfig.setFlowId(sentinelFlowRule.getFlowId());
            clusterConfig.setThresholdType(ClusterRuleConstant.FLOW_THRESHOLD_GLOBAL);
            clusterConfig.setSampleCount(1);
            clusterConfig.setFallbackToLocalWhenFail(true);
            flowRule.setClusterConfig(clusterConfig);
        }
        
        return flowRule;
    }
    
    
    /**
     * 创建流控规则，支持CustomizedFlowRule实体
     * clusterConfig.setSampleCount(1) 这行代码设置了集群流控配置中的采样数量（sample count）参数。
     * 在 Sentinel 的集群流控实现中， sampleCount 属性用于滑动窗口统计算法中的采样窗口数量。它决定了滑动窗口被划分为多少个采样窗口（bucket）。
     * 具体作用：
     * 1. 当设置为 1 时，表示整个滑动窗口只有一个采样桶，相当于使用计数器算法进行统计
     * 2. 当设置为更大的值时（如默认值 2），滑动窗口会被分为多个采样桶，可以更精确地反映流量的变化趋势
     * 在这个例子中，设置 sampleCount 为 1 意味着：
     * - 使用最简单的计数器方式进行统计
     * - 降低了统计精度，但也减少了内存占用和计算开销
     * - 适合对精确度要求不高的场景
     * 这个参数与 windowIntervalMs （窗口时间长度）一起决定了流量统计的精度和资源消耗。在集群模式下，这些参数会影响 Token Server 对资源使用情况的统计方式。
     * 
     * @param customizedFlowRule CustomizedFlowRule对象
     * @return 创建的流控规则
     */
    public FlowRule createFlowRule(CustomizedFlowRule customizedFlowRule) {
        FlowRule flowRule = new FlowRule(getResource());
        flowRule.setCount(customizedFlowRule.getCount());
        flowRule.setGrade(customizedFlowRule.getGrade());
        flowRule.setStrategy(customizedFlowRule.getStrategy());
        
        // 设置关联资源，如果策略是关联模式且有resourceVal值
        if (customizedFlowRule.getStrategy() == RuleConstant.STRATEGY_RELATE && customizedFlowRule.getResourceVal() != null) {
            flowRule.setRefResource(customizedFlowRule.getResourceVal());
        }
        
        // 当grade为QPS模式时，启用集群模式
        if (customizedFlowRule.getGrade() == RuleConstant.FLOW_GRADE_QPS) {
            flowRule.setClusterMode(true);
            ClusterFlowConfig clusterConfig = new ClusterFlowConfig();
            clusterConfig.setFlowId(customizedFlowRule.getFlowId());
            clusterConfig.setThresholdType(ClusterRuleConstant.FLOW_THRESHOLD_GLOBAL);
            clusterConfig.setSampleCount(1);
            clusterConfig.setFallbackToLocalWhenFail(true);
            flowRule.setClusterConfig(clusterConfig);
        }
        
        return flowRule;
    }
}