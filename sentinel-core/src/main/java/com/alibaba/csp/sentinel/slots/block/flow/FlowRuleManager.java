/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.flow;

import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.config.SentinelConfig;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.node.metric.MetricTimerListener;
import com.alibaba.csp.sentinel.property.DynamicSentinelProperty;
import com.alibaba.csp.sentinel.property.PropertyListener;
import com.alibaba.csp.sentinel.property.SentinelProperty;
import com.alibaba.csp.sentinel.slots.block.RuleManager;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.StringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 流量控制规则管理器，负责管理、加载和更新流量控制规则。
 * </p>
 * <p>
 * 一个资源可以有多个规则。这些规则按照以下顺序生效：
 * <ol>
 * <li>来自指定调用者的请求</li>
 * <li>没有指定调用者的请求</li>
 * </ol>
 * </p>
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 * @author Weihua
 */
public class FlowRuleManager {

    /**
     * 存储所有流控规则的管理器
     */
    private static volatile RuleManager<FlowRule> flowRules = new RuleManager<>();

    /**
     * 流控规则属性监听器，用于监听规则变更
     */
    private static final FlowPropertyListener LISTENER = new FlowPropertyListener();
    /**
     * 当前流控规则属性源，默认使用动态属性源
     */
    private static SentinelProperty<List<FlowRule>> currentProperty = new DynamicSentinelProperty<List<FlowRule>>();

    /** 
     * 调度器的核心线程池大小必须设置为1，这样{@link #startMetricTimerListener()}中的任务才能按顺序执行
     **/
    /**
     * 用于执行指标记录任务的调度器
     */
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory("sentinel-metrics-record-task", true));

    /**
     * 静态初始化块，在类加载时执行
     * 1. 为当前属性源添加监听器
     * 2. 启动指标记录定时任务
     */
    static {
        currentProperty.addListener(LISTENER);
        startMetricTimerListener();
    }

    /**
     * <p> 启动指标记录定时任务
     * <ol>
     *     <li>如果刷新间隔大于0，定时器将以刷新间隔为周期运行</li>
     *     <li>如果刷新间隔小于等于0或值无效，则定时器不会启动</li>
     * </ol></p>
     */
    private static void startMetricTimerListener() {
        long flushInterval = SentinelConfig.metricLogFlushIntervalSec();
        if (flushInterval <= 0) {
            RecordLog.info("[FlowRuleManager] The MetricTimerListener isn't started. If you want to start it, "
                    + "please change the value(current: {}) of config({}) more than 0 to start it.", flushInterval,
                SentinelConfig.METRIC_FLUSH_INTERVAL);
            return;
        }
        SCHEDULER.scheduleAtFixedRate(new MetricTimerListener(), 0, flushInterval, TimeUnit.SECONDS);
    }

    /**
     * 监听{@link SentinelProperty}属性源以获取{@link FlowRule}。该属性是{@link FlowRule}的来源。
     * 流控规则也可以通过{@link #loadRules(List)}方法直接设置。
     * 
     * register2Property方法和loadRules方法的主要区别在于：
     * 1. register2Property方法用于注册一个SentinelProperty数据源，使FlowRuleManager能够监听该数据源的变化并自动更新规则。
     * 这是一种长期的订阅关系，适合与动态配置中心（如ZooKeeper、Nacos、Apollo等）集成，实现规则的动态推送。
     * 2. loadRules方法是一次性地加载规则列表，直接替换当前的规则。它实际上是通过调用currentProperty.updateValue(rules)来实现的，
     * 适合于直接编程方式更新规则或从静态配置文件加载规则。
     * 简而言之，register2Property适合动态配置场景（订阅模式），而loadRules适合静态配置或编程式更新场景（命令式）。

     * @param property 要监听的属性源
     */
    public static void register2Property(SentinelProperty<List<FlowRule>> property) {
        AssertUtil.notNull(property, "property cannot be null");
        synchronized (LISTENER) {
            RecordLog.info("[FlowRuleManager] Registering new property to flow rule manager");
            currentProperty.removeListener(LISTENER);
            property.addListener(LISTENER);
            currentProperty = property;
        }
    }

    /**
     * 获取规则的副本。
     *
     * @return 规则的新副本
     */
    public static List<FlowRule> getRules() {
        return flowRules.getRules();
    }

    /**
     * 加载{@link FlowRule}，之前的规则将被替换。
     * 这是更新流控规则的主要方法。
     *
     * @param rules 要加载的新规则
     */
    public static void loadRules(List<FlowRule> rules) {
        currentProperty.updateValue(rules);
    }

    /**
     * 获取指定资源的流控规则列表
     * 
     * @param resource 资源名称
     * @return 资源对应的流控规则列表
     */
    static List<FlowRule> getFlowRules(String resource) {
        return flowRules.getRules(resource);
    }

    /**
     * 检查指定资源是否配置了流控规则
     * 
     * @param resource 资源名称
     * @return 如果资源配置了流控规则则返回true，否则返回false
     */
    public static boolean hasConfig(String resource) {
        return flowRules.hasConfig(resource);
    }

    /**
     * 判断指定来源是否为资源的"其他来源"（即不是资源的限制应用）
     * 
     * @param origin 来源名称
     * @param resourceName 资源名称
     * @return 如果来源不是资源的限制应用则返回true，否则返回false
     */
    public static boolean isOtherOrigin(String origin, String resourceName) {
        if (StringUtil.isEmpty(origin)) {
            return false;
        }

        List<FlowRule> rules = flowRules.getRules(resourceName);

        if (rules != null) {
            for (FlowRule rule : rules) {
                if (origin.equals(rule.getLimitApp())) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 流控规则属性监听器，用于处理规则的更新和加载事件
     */
    private static final class FlowPropertyListener implements PropertyListener<List<FlowRule>> {

        /**
         * 处理配置更新事件
         * 
         * @param value 更新的流控规则列表
         */
        @Override
        public synchronized void configUpdate(List<FlowRule> value) {
            Map<String, List<FlowRule>> rules = FlowRuleUtil.buildFlowRuleMap(value);
            flowRules.updateRules(rules);
            RecordLog.info("[FlowRuleManager] Flow rules received: {}", rules);
        }

        /**
         * 处理配置加载事件
         * 
         * @param conf 加载的流控规则列表
         */
        @Override
        public synchronized void configLoad(List<FlowRule> conf) {
            Map<String, List<FlowRule>> rules = FlowRuleUtil.buildFlowRuleMap(conf);
            flowRules.updateRules(rules);
            RecordLog.info("[FlowRuleManager] Flow rules loaded: {}", rules);
        }
    }

}
