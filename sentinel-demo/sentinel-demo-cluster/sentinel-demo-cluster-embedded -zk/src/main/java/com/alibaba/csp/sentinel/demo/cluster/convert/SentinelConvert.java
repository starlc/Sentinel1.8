package com.alibaba.csp.sentinel.demo.cluster.convert;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.demo.cluster.entity.SentinelFlowRule;
import com.alibaba.csp.sentinel.demo.cluster.factory.HandlerFactory;
import com.alibaba.csp.sentinel.demo.cluster.handler.AbstractHandler;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;

import java.util.ArrayList;
import java.util.List;

public class SentinelConvert implements Converter<List<SentinelFlowRule>, List<FlowRule>>{
    @Override
    public List<FlowRule> convert(List<SentinelFlowRule> sentinelFlowRules) {
        if (sentinelFlowRules == null || sentinelFlowRules.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<FlowRule> flowRules = new ArrayList<>(sentinelFlowRules.size());
        for (SentinelFlowRule rule : sentinelFlowRules) {
            AbstractHandler handler = (AbstractHandler) HandlerFactory.getHandler(rule);
            FlowRule flowRule = handler.createFlowRule(rule);
            flowRules.add(flowRule);
        }
        
        return flowRules;
    }
}
