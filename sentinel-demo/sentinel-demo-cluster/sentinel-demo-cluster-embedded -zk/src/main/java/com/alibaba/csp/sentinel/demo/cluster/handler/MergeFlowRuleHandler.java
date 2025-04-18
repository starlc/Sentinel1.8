package com.alibaba.csp.sentinel.demo.cluster.handler;

import com.alibaba.csp.sentinel.demo.cluster.entity.CustomizedFlowRule;
import com.alibaba.csp.sentinel.demo.cluster.entity.FlowRuleEntity;

public class MergeFlowRuleHandler extends AbstractHandler {
    private static final int MERGE_STATUS = 2;


    @Override
    public int getStatus() {
        return MERGE_STATUS;
    }

    @Override
    protected String getResource(FlowRuleEntity object) {
        if (object instanceof CustomizedFlowRule){
            CustomizedFlowRule flowRule = (CustomizedFlowRule) object;
            return flowRule.getPort()+"|" +flowRule.getResourceVal();
        }
        return null;
    }
}