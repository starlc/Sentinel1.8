package com.alibaba.csp.sentinel.demo.cluster.factory;

import com.alibaba.csp.sentinel.demo.cluster.handler.AbstractHandler;
import com.alibaba.csp.sentinel.demo.cluster.handler.CustomizedFlowRuleHandler;
import com.alibaba.csp.sentinel.demo.cluster.handler.MergeFlowRuleHandler;
import com.alibaba.csp.sentinel.demo.cluster.handler.OverAllFlowRuleHandler;
import com.alibaba.csp.sentinel.demo.cluster.handler.SingleFlowRuleHandler;

import java.util.HashMap;
import java.util.Map;

public class HandlerFactory {
    private static final Map<Integer, AbstractHandler> handlerRegistry = new HashMap<>();

    static {
        // Register default handlers
        registerHandler(new CustomizedFlowRuleHandler());
        registerHandler(new SingleFlowRuleHandler());
        registerHandler(new OverAllFlowRuleHandler());
        registerHandler(new MergeFlowRuleHandler());
    }

    public static AbstractHandler getHandler(Object rule) {
        return handlerRegistry.get(rule.getClass());
    }

    public static void registerHandler(AbstractHandler handler) {
        handlerRegistry.put(handler.getStatus(), handler);
    }
}