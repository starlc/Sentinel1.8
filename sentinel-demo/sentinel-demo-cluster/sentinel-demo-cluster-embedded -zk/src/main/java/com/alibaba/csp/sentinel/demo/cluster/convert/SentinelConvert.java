package com.alibaba.csp.sentinel.demo.cluster.convert;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.demo.cluster.entity.SentinelFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;

import java.util.List;

public class SentinelConvert implements Converter<List<SentinelFlowRule>, List<FlowRule>>{
    @Override
    public List<FlowRule> convert(List<SentinelFlowRule> sentinelFlowRules) {
        return null;
    }
}
