package com.alibaba.csp.sentinel.demo.cluster.handler;

import com.alibaba.csp.sentinel.demo.cluster.entity.CustomizedFlowRule;
import com.alibaba.csp.sentinel.demo.cluster.entity.FlowRuleEntity;

public class SingleFlowRuleHandler extends AbstractHandler {
    private static final int SINGLE_STATUS= 1;


    @Override
    public int getStatus() {
        return SINGLE_STATUS;
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