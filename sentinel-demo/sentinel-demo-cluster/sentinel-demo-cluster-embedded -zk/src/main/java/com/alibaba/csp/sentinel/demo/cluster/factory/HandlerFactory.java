package com.alibaba.csp.sentinel.demo.cluster.factory;

import com.alibaba.csp.sentinel.demo.cluster.handler.Handler;
import com.alibaba.csp.sentinel.demo.cluster.handler.CustomizedFlowRuleHandler;
import com.alibaba.csp.sentinel.demo.cluster.handler.SentinelFlowRuleHandler;
import com.alibaba.csp.sentinel.demo.cluster.entity.CustomizedFlowRule;
import com.alibaba.csp.sentinel.demo.cluster.entity.SentinelFlowRule;

import java.util.HashMap;
import java.util.Map;

public class HandlerFactory {
    private static final Map<Class<?>, Handler> handlerRegistry = new HashMap<>();

    static {
        // Register default handlers
        handlerRegistry.put(CustomizedFlowRule.class, new CustomizedFlowRuleHandler(null));
        handlerRegistry.put(SentinelFlowRule.class, new SentinelFlowRuleHandler(null));
    }

    public static Handler getHandler(Object rule) {
        return handlerRegistry.get(rule.getClass());
    }

    public static void registerHandler(Class<?> ruleClass, Handler handler) {
        handlerRegistry.put(ruleClass, handler);
    }
}