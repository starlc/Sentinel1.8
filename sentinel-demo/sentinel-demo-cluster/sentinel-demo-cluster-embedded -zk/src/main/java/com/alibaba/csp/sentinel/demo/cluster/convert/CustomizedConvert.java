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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomizedConvert implements Converter<List<CustomizedFlowRule>, List<FlowRule>>{
    @Override
    public List<FlowRule> convert(List<CustomizedFlowRule> customizedFlowRules) {
        if (customizedFlowRules == null || customizedFlowRules.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String,List<String>> cache = new ConcurrentHashMap<>();
        List<FlowRule> flowRules = new ArrayList<>(customizedFlowRules.size());
        for (CustomizedFlowRule rule : customizedFlowRules) {
            AbstractHandler handler = HandlerFactory.getHandler(rule);
            FlowRule flowRule = handler.handlerRule(rule,cache);
            flowRules.add(flowRule);
        }
        
        return flowRules;
    }
}
