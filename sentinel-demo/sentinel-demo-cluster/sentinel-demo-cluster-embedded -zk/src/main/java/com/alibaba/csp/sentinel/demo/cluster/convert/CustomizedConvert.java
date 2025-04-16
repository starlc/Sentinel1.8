package com.alibaba.csp.sentinel.demo.cluster.convert;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.demo.cluster.entity.CustomizedFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;

import java.util.List;

public class CustomizedConvert implements Converter<List<CustomizedFlowRule>, List<FlowRule>>{
    @Override
    public List<FlowRule> convert(List<CustomizedFlowRule> sentinelFlowRules) {
        return null;
    }
}
