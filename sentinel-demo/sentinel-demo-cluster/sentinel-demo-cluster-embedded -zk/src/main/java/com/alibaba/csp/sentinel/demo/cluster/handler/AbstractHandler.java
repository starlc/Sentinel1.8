package com.alibaba.csp.sentinel.demo.cluster.handler;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.ClusterFlowConfig;
import com.alibaba.csp.sentinel.slots.block.ClusterRuleConstant;
import com.alibaba.csp.sentinel.demo.cluster.entity.SentinelFlowRule;
import com.alibaba.csp.sentinel.demo.cluster.entity.CustomizedFlowRule;
import com.alibaba.csp.sentinel.demo.cluster.entity.FlowRuleEntity;

import java.util.List;
import java.util.Map;

/**
 * Handler抽象类，实现Handler接口，提供创建FlowRule的方法
 */
public abstract class AbstractHandler  {

    private static final String ENABLE_YES = "1";
    private static final String ENABLE_NO= "0";
    public abstract int getStatus();

    /**
     * 通用处理规则方法，接受任何实现了FlowRuleEntity接口的实体
     * 
     * @param <T> 实现了FlowRuleEntity接口的类型
     * @param flowRuleEntity 流控规则实体
     * @param mergedCache 合并缓存
     * @return 流控规则
     */
    public <T extends FlowRuleEntity> FlowRule handlerRule(T flowRuleEntity, Map<String, List<String>> mergedCache){
        if (ENABLE_YES.equals(String.valueOf(flowRuleEntity.getStatus()))){
            return createFlowRule(flowRuleEntity);
        }
        return null;
    }
    /**
     * 创建流控规则，支持任何实现了FlowRuleEntity接口的实体
     * 
     * @param <T> 实现了FlowRuleEntity接口的类型
     * @param flowRuleEntity 流控规则实体
     * @return 创建的流控规则
     */
    public <T extends FlowRuleEntity> FlowRule createFlowRule(T flowRuleEntity) {
        FlowRule flowRule = new FlowRule(flowRuleEntity.getResource());
        flowRule.setCount(flowRuleEntity.getCount());
        flowRule.setGrade(flowRuleEntity.getGrade());
        flowRule.setStrategy(flowRuleEntity.getStrategy());
        
        // 处理特殊情况
        if (flowRuleEntity instanceof CustomizedFlowRule) {
            CustomizedFlowRule customizedRule = (CustomizedFlowRule) flowRuleEntity;
            // 当grade为QPS模式时，启用集群模式
            if (customizedRule.getGrade() == RuleConstant.FLOW_GRADE_QPS) {
                configureClusterMode(flowRule, customizedRule.getFlowId());
            }
        } else if (flowRuleEntity instanceof SentinelFlowRule) {
            SentinelFlowRule sentinelRule = (SentinelFlowRule) flowRuleEntity;
            // 当grade为QPS模式且启用集群模式时，创建ClusterFlowConfig对象
            if (sentinelRule.getGrade() == RuleConstant.FLOW_GRADE_QPS && sentinelRule.getClusterMode() == 1) {
                configureClusterMode(flowRule, sentinelRule.getFlowId());
            }
        }
        
        return flowRule;
    }

    /**
     * 配置集群模式的通用方法
     * 
     * @param flowRule 流控规则
     * @param flowId 流控ID
     */
    private void configureClusterMode(FlowRule flowRule, Long flowId) {
        flowRule.setClusterMode(true);
        ClusterFlowConfig clusterConfig = new ClusterFlowConfig();
        clusterConfig.setFlowId(flowId);
        clusterConfig.setThresholdType(ClusterRuleConstant.FLOW_THRESHOLD_GLOBAL);
        clusterConfig.setSampleCount(1);
        clusterConfig.setFallbackToLocalWhenFail(true);
        flowRule.setClusterConfig(clusterConfig);
    }
    protected abstract String getResource(FlowRuleEntity entity);
}