package com.alibaba.csp.sentinel.demo.cluster.handler;

import com.alibaba.csp.sentinel.demo.cluster.entity.CustomizedFlowRule;

public class CustomizedFlowRuleHandler extends AbstractHandler  {
    private CustomizedFlowRule rule;

    public CustomizedFlowRuleHandler(CustomizedFlowRule rule) {
        this.rule = rule;
    }

    @Override
    public String getResource() {
        if (rule.getStatus() == 1) {
            return rule.getPort() + "|" + rule.getResourceVal();
        }
        return rule.getResource();
    }

    @Override
    public int getStatus() {
        return rule.getStatus();
    }
}