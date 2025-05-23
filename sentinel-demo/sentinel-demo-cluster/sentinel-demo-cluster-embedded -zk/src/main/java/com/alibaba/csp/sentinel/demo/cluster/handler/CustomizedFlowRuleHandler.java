package com.alibaba.csp.sentinel.demo.cluster.handler;

import com.alibaba.csp.sentinel.demo.cluster.entity.CustomizedFlowRule;
import com.alibaba.csp.sentinel.demo.cluster.entity.FlowRuleEntity;

public class CustomizedFlowRuleHandler extends AbstractHandler  {

    private static final int STATUS_DEFALUT = 5;


    @Override
    public int getStatus() {
        return STATUS_DEFALUT;
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