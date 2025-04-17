package com.alibaba.csp.sentinel.demo.cluster.handler;

import com.alibaba.csp.sentinel.demo.cluster.entity.SentinelFlowRule;

public class SentinelFlowRuleHandler extends AbstractHandler {
    private SentinelFlowRule rule;

    public SentinelFlowRuleHandler(SentinelFlowRule rule) {
        this.rule = rule;
    }

    @Override
    public String getResource() {
        return rule.getResource();
    }

    @Override
    public int getStatus() {
        return rule.getStatus();
    }
}