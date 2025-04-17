package com.alibaba.csp.sentinel.demo.cluster.convert;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.demo.cluster.entity.CustomizedFlowRule;
import com.alibaba.csp.sentinel.demo.cluster.factory.HandlerFactory;
import com.alibaba.csp.sentinel.demo.cluster.handler.AbstractHandler;
import com.alibaba.csp.sentinel.demo.cluster.handler.Handler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.ClusterFlowConfig;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.ClusterRuleConstant;

import java.util.ArrayList;
import java.util.List;

public class CustomizedConvert implements Converter<List<CustomizedFlowRule>, List<FlowRule>>{
    @Override
    public List<FlowRule> convert(List<CustomizedFlowRule> customizedFlowRules) {
        if (customizedFlowRules == null || customizedFlowRules.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<FlowRule> flowRules = new ArrayList<>(customizedFlowRules.size());
        for (CustomizedFlowRule rule : customizedFlowRules) {
            AbstractHandler handler = (AbstractHandler) HandlerFactory.getHandler(rule);
            FlowRule flowRule = handler.createFlowRule(rule);
            flowRule.setCount(rule.getCount());
            flowRule.setGrade(rule.getGrade());
            flowRule.setStrategy(rule.getStrategy());
            
            // 设置关联资源，如果有的话
            if (rule.getStrategy() == RuleConstant.STRATEGY_RELATE && rule.getResourceVal() != null) {
                flowRule.setRefResource(rule.getResourceVal());
            }
            
            // 当grade为QPS模式时，创建ClusterModeConfig对象
            if (rule.getGrade() == RuleConstant.FLOW_GRADE_QPS) {
                flowRule.setClusterMode(true);
                ClusterFlowConfig clusterConfig = new ClusterFlowConfig();
                clusterConfig.setFlowId(rule.getFlowId());
                clusterConfig.setThresholdType(ClusterRuleConstant.FLOW_THRESHOLD_GLOBAL);
                clusterConfig.setSampleCount(1);
                clusterConfig.setFallbackToLocalWhenFail(true);
                flowRule.setClusterConfig(clusterConfig);
            }
            
            flowRules.add(flowRule);
        }
        
        return flowRules;
    }
}
