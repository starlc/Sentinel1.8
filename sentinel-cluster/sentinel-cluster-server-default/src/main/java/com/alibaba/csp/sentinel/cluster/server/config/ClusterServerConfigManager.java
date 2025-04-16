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
package com.alibaba.csp.sentinel.cluster.server.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.csp.sentinel.cluster.ClusterConstants;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterParamFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.statistic.ClusterMetricStatistics;
import com.alibaba.csp.sentinel.cluster.flow.statistic.ClusterParamMetricStatistics;
import com.alibaba.csp.sentinel.cluster.flow.statistic.limit.GlobalRequestLimiter;
import com.alibaba.csp.sentinel.cluster.registry.ConfigSupplierRegistry;
import com.alibaba.csp.sentinel.cluster.server.ServerConstants;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.property.DynamicSentinelProperty;
import com.alibaba.csp.sentinel.property.PropertyListener;
import com.alibaba.csp.sentinel.property.SentinelProperty;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleUtil;
import com.alibaba.csp.sentinel.util.AssertUtil;

/**
 * Sentinel集群服务器配置管理器，负责管理Token Server的全局配置。
 * 该类主要管理以下几类配置：
 * 1. 传输配置：如端口号、空闲超时时间等
 * 2. 命名空间集合：管理服务器可以处理的命名空间
 * 3. 流控配置：如最大QPS、统计时间窗口等
 * 
 * 该类通过属性监听器模式实现配置的动态更新，当配置变更时会通知相关组件。
 * 
 * @author Eric Zhao
 * @since 1.4.0
 */
public final class ClusterServerConfigManager {

    /**
     * 标识Token Server是否为嵌入式模式
     * 嵌入式模式下，服务器本身也是服务的一部分
     */
    private static boolean embedded = false;

    /**
     * 服务器全局传输和作用域配置
     */
    /**
     * Token Server监听的端口号
     */
    private static volatile int port = ClusterConstants.DEFAULT_CLUSTER_SERVER_PORT;
    /**
     * 连接空闲超时时间（秒）
     */
    private static volatile int idleSeconds = ServerTransportConfig.DEFAULT_IDLE_SECONDS;
    /**
     * 服务器管理的命名空间集合，默认包含一个默认命名空间
     */
    private static volatile Set<String> namespaceSet = Collections.singleton(ServerConstants.DEFAULT_NAMESPACE);

    /**
     * 服务器全局流控配置
     */
    /**
     * 超额请求数量，用于流量控制
     */
    private static volatile double exceedCount = ServerFlowConfig.DEFAULT_EXCEED_COUNT;
    /**
     * 最大占用比率，用于流量控制
     */
    private static volatile double maxOccupyRatio = ServerFlowConfig.DEFAULT_MAX_OCCUPY_RATIO;
    /**
     * 统计时间窗口长度（毫秒）
     */
    private static volatile int intervalMs = ServerFlowConfig.DEFAULT_INTERVAL_MS;
    /**
     * 采样数量，用于滑动窗口统计
     */
    private static volatile int sampleCount = ServerFlowConfig.DEFAULT_SAMPLE_COUNT;
    /**
     * 服务器允许的最大QPS
     */
    private static volatile double maxAllowedQps = ServerFlowConfig.DEFAULT_MAX_ALLOWED_QPS;

    /**
     * 命名空间特定的流控配置，用于Token Server
     * 格式: (命名空间, 配置)
     */
    private static final Map<String, ServerFlowConfig> NAMESPACE_CONF = new ConcurrentHashMap<>();

    /**
     * 传输配置观察者列表，当传输配置变更时会通知这些观察者
     */
    private static final List<ServerTransportConfigObserver> TRANSPORT_CONFIG_OBSERVERS = new ArrayList<>();

    /**
     * 集群服务器全局传输配置的动态属性
     * 通过SentinelProperty机制实现配置的动态更新
     */
    private static SentinelProperty<ServerTransportConfig> transportConfigProperty = new DynamicSentinelProperty<>();
    /**
     * 集群服务器命名空间集合的动态属性
     */
    private static SentinelProperty<Set<String>> namespaceSetProperty = new DynamicSentinelProperty<>();
    /**
     * 集群服务器全局流控配置的动态属性
     */
    private static SentinelProperty<ServerFlowConfig> globalFlowProperty = new DynamicSentinelProperty<>();

    /**
     * 服务器全局传输配置的属性监听器
     * 当传输配置发生变化时会触发相应的处理逻辑
     */
    private static final PropertyListener<ServerTransportConfig> TRANSPORT_PROPERTY_LISTENER
        = new ServerGlobalTransportPropertyListener();
    /**
     * 服务器全局流控配置的属性监听器
     */
    private static final PropertyListener<ServerFlowConfig> GLOBAL_FLOW_PROPERTY_LISTENER
        = new ServerGlobalFlowPropertyListener();
    /**
     * 服务器命名空间集合的属性监听器
     */
    private static final PropertyListener<Set<String>> NAMESPACE_SET_PROPERTY_LISTENER
        = new ServerNamespaceSetPropertyListener();

    static {
        transportConfigProperty.addListener(TRANSPORT_PROPERTY_LISTENER);
        globalFlowProperty.addListener(GLOBAL_FLOW_PROPERTY_LISTENER);
        namespaceSetProperty.addListener(NAMESPACE_SET_PROPERTY_LISTENER);
    }

    /**
     * 注册集群服务器命名空间集合的动态属性
     * 该方法用于设置命名空间集合的数据源，当数据源中的命名空间集合发生变化时，
     * 会通过监听器机制自动更新服务器的命名空间集合
     *
     * @param property 服务器命名空间集合的动态属性数据源
     */
    public static void registerNamespaceSetProperty(SentinelProperty<Set<String>> property) {
        AssertUtil.notNull(property, "namespace set dynamic property cannot be null");
        synchronized (NAMESPACE_SET_PROPERTY_LISTENER) {
            RecordLog.info(
                "[ClusterServerConfigManager] Registering new namespace set dynamic property to Sentinel server "
                    + "config manager");
            namespaceSetProperty.removeListener(NAMESPACE_SET_PROPERTY_LISTENER);
            property.addListener(NAMESPACE_SET_PROPERTY_LISTENER);
            namespaceSetProperty = property;
        }
    }

    /**
     * 注册集群服务器传输配置的动态属性
     * 该方法用于设置传输配置的数据源，当数据源中的传输配置发生变化时，
     * 会通过监听器机制自动更新服务器的传输配置（如端口号等）
     *
     * @param property 服务器传输配置的动态属性数据源
     */
    public static void registerServerTransportProperty(SentinelProperty<ServerTransportConfig> property) {
        AssertUtil.notNull(property, "cluster server transport config dynamic property cannot be null");
        synchronized (TRANSPORT_PROPERTY_LISTENER) {
            RecordLog.info(
                "[ClusterServerConfigManager] Registering new server transport dynamic property to Sentinel server "
                    + "config manager");
            transportConfigProperty.removeListener(TRANSPORT_PROPERTY_LISTENER);
            property.addListener(TRANSPORT_PROPERTY_LISTENER);
            transportConfigProperty = property;
        }
    }

    /**
     * 注册集群服务器全局统计（流控）配置的动态属性
     * 该方法用于设置流控配置的数据源，当数据源中的流控配置发生变化时，
     * 会通过监听器机制自动更新服务器的流控配置（如最大QPS等）
     *
     * @param property 服务器流控配置的动态属性数据源
     */
    public static void registerGlobalServerFlowProperty(SentinelProperty<ServerFlowConfig> property) {
        AssertUtil.notNull(property, "cluster server flow config dynamic property cannot be null");
        synchronized (GLOBAL_FLOW_PROPERTY_LISTENER) {
            RecordLog.info(
                "[ClusterServerConfigManager] Registering new server global flow dynamic property "
                    + "to Sentinel server config manager");
            globalFlowProperty.removeListener(GLOBAL_FLOW_PROPERTY_LISTENER);
            property.addListener(GLOBAL_FLOW_PROPERTY_LISTENER);
            globalFlowProperty = property;
        }
    }

    /**
     * 加载提供的服务器命名空间集合到内存中的属性
     * 该方法直接更新内存中的命名空间集合配置，会触发相应的监听器处理逻辑
     *
     * @param namespaceSet 有效的命名空间集合
     */
    public static void loadServerNamespaceSet(Set<String> namespaceSet) {
        namespaceSetProperty.updateValue(namespaceSet);
    }

    /**
     * 加载提供的服务器传输配置到内存中的属性
     * 该方法直接更新内存中的传输配置，会触发相应的监听器处理逻辑
     *
     * @param config 有效的集群服务器传输配置
     */
    public static void loadGlobalTransportConfig(ServerTransportConfig config) {
        transportConfigProperty.updateValue(config);
    }

    /**
     * 加载提供的服务器全局统计（流控）配置到内存中的属性
     * 该方法直接更新内存中的流控配置，会触发相应的监听器处理逻辑
     *
     * @param config 有效的集群服务器全局流控配置
     */
    public static void loadGlobalFlowConfig(ServerFlowConfig config) {
        globalFlowProperty.updateValue(config);
    }

    /**
     * 为特定命名空间加载服务器流控配置
     * 注意：目前该方法实际上更新的是全局流控配置，命名空间特定的配置尚未实现
     *
     * @param namespace 有效的命名空间
     * @param config    该命名空间的有效流控配置
     */
    public static void loadFlowConfig(String namespace, ServerFlowConfig config) {
        AssertUtil.notEmpty(namespace, "namespace cannot be empty");
        // TODO: Support namespace-scope server flow config.
        globalFlowProperty.updateValue(config);
    }

    /**
     * Add a transport config observer. The observers will be called as soon as
     * there are some changes in transport config (e.g. token server port).
     *
     * @param observer a valid transport config observer
     */
    public static void addTransportConfigChangeObserver(ServerTransportConfigObserver observer) {
        AssertUtil.notNull(observer, "observer cannot be null");
        TRANSPORT_CONFIG_OBSERVERS.add(observer);
    }

    /**
     * 服务器命名空间集合属性监听器
     * 负责处理命名空间集合变更事件
     */
    private static class ServerNamespaceSetPropertyListener implements PropertyListener<Set<String>> {

        @Override
        public synchronized void configLoad(Set<String> set) {
            if (set == null || set.isEmpty()) {
                RecordLog.warn("[ClusterServerConfigManager] WARN: empty initial server namespace set");
                return;
            }
            applyNamespaceSetChange(set);
        }

        @Override
        public synchronized void configUpdate(Set<String> set) {
            // TODO: should debounce?
            applyNamespaceSetChange(set);
        }
    }

    /**
     * 应用命名空间集合的变更
     * 该方法处理命名空间集合变更的核心逻辑：
     * 1. 确保默认命名空间始终存在
     * 2. 在嵌入式模式下添加应用自身的命名空间
     * 3. 清理不再使用的命名空间的规则属性
     * 4. 为新的命名空间注册规则属性和初始化QPS限制器
     * 
     * @param newSet 新的命名空间集合
     */
    private static void applyNamespaceSetChange(Set<String> newSet) {
        if (newSet == null) {
            return;
        }
        RecordLog.info("[ClusterServerConfigManager] Server namespace set will be update to: {}", newSet);
        if (newSet.isEmpty()) {
            ClusterServerConfigManager.namespaceSet = Collections.singleton(ServerConstants.DEFAULT_NAMESPACE);
            return;
        }

        newSet = new HashSet<>(newSet);
        // Always add the `default` namespace to the namespace set.
        newSet.add(ServerConstants.DEFAULT_NAMESPACE);

        if (embedded) {
            // In embedded server mode, the server itself is also a part of service,
            // so it should be added to namespace set.
            // By default, the added namespace is the appName.
            newSet.add(ConfigSupplierRegistry.getNamespaceSupplier().get());
        }

        Set<String> oldSet = ClusterServerConfigManager.namespaceSet;
        if (oldSet != null && !oldSet.isEmpty()) {
            for (String ns : oldSet) {
                // Remove the cluster rule property for deprecated namespace set.
                if (!newSet.contains(ns)) {
                    ClusterFlowRuleManager.removeProperty(ns);
                    ClusterParamFlowRuleManager.removeProperty(ns);
                }
            }
        }

        ClusterServerConfigManager.namespaceSet = newSet;
        for (String ns : newSet) {
            // Register the rule property if needed.
            ClusterFlowRuleManager.registerPropertyIfAbsent(ns);
            ClusterParamFlowRuleManager.registerPropertyIfAbsent(ns);
            // Initialize the global QPS limiter for the namespace.
            GlobalRequestLimiter.initIfAbsent(ns);
        }
    }

    /**
     * 服务器全局传输配置属性监听器
     * 负责处理传输配置变更事件
     */
    private static class ServerGlobalTransportPropertyListener implements PropertyListener<ServerTransportConfig> {

        @Override
        public void configLoad(ServerTransportConfig config) {
            if (config == null) {
                RecordLog.warn("[ClusterServerConfigManager] Empty initial server transport config");
                return;
            }
            applyConfig(config);
        }

        @Override
        public void configUpdate(ServerTransportConfig config) {
            applyConfig(config);
        }

        private synchronized void applyConfig(ServerTransportConfig config) {
            if (!isValidTransportConfig(config)) {
                RecordLog.warn(
                    "[ClusterServerConfigManager] Invalid cluster server transport config, ignoring: {}", config);
                return;
            }
            RecordLog.info("[ClusterServerConfigManager] Updating new server transport config: {}", config);
            if (config.getIdleSeconds() != idleSeconds) {
                idleSeconds = config.getIdleSeconds();
            }
            updateTokenServer(config);
        }
    }

    /**
     * 更新Token服务器配置
     * 主要处理端口变更，并通知所有观察者
     * 
     * @param config 新的传输配置
     */
    private static void updateTokenServer(ServerTransportConfig config) {
        int newPort = config.getPort();
        AssertUtil.isTrue(newPort > 0, "token server port should be valid (positive)");
        if (newPort == port) {
            return;
        }
        ClusterServerConfigManager.port = newPort;

        for (ServerTransportConfigObserver observer : TRANSPORT_CONFIG_OBSERVERS) {
            observer.onTransportConfigChange(config);
        }
    }

    /**
     * 服务器全局流控配置属性监听器
     * 负责处理流控配置变更事件
     */
    private static class ServerGlobalFlowPropertyListener implements PropertyListener<ServerFlowConfig> {

        @Override
        public void configUpdate(ServerFlowConfig config) {
            applyGlobalFlowConfig(config);
        }

        @Override
        public void configLoad(ServerFlowConfig config) {
            applyGlobalFlowConfig(config);
        }

        private synchronized void applyGlobalFlowConfig(ServerFlowConfig config) {
            if (!isValidFlowConfig(config)) {
                RecordLog.warn(
                    "[ClusterServerConfigManager] Invalid cluster server global flow config, ignoring: {}", config);
                return;
            }
            RecordLog.info("[ClusterServerConfigManager] Updating new server global flow config: {}", config);
            if (config.getExceedCount() != exceedCount) {
                exceedCount = config.getExceedCount();
            }
            if (config.getMaxOccupyRatio() != maxOccupyRatio) {
                maxOccupyRatio = config.getMaxOccupyRatio();
            }
            if (config.getMaxAllowedQps() != maxAllowedQps) {
                maxAllowedQps = config.getMaxAllowedQps();
                GlobalRequestLimiter.applyMaxQpsChange(maxAllowedQps);
            }
            int newIntervalMs = config.getIntervalMs();
            int newSampleCount = config.getSampleCount();
            if (newIntervalMs != intervalMs || newSampleCount != sampleCount) {
                if (newIntervalMs <= 0 || newSampleCount <= 0 || newIntervalMs % newSampleCount != 0) {
                    RecordLog.warn("[ClusterServerConfigManager] Ignoring invalid flow interval or sample count");
                } else {
                    intervalMs = newIntervalMs;
                    sampleCount = newSampleCount;
                    // Reset all the metrics.
                    ClusterMetricStatistics.resetFlowMetrics();
                    ClusterParamMetricStatistics.resetFlowMetrics();
                }
            }
        }
    }

    /**
     * 验证传输配置是否有效
     * 有效的传输配置必须包含有效的端口号（1-65535）
     * 
     * @param config 要验证的传输配置
     * @return 配置是否有效
     */
    public static boolean isValidTransportConfig(ServerTransportConfig config) {
        return config != null && config.getPort() > 0 && config.getPort() <= 65535;
    }

    /**
     * 验证流控配置是否有效
     * 有效的流控配置必须满足：
     * 1. 最大占用比率、超额数量和最大QPS均不为负数
     * 2. 窗口配置（采样数量和时间间隔）有效
     * 
     * @param config 要验证的流控配置
     * @return 配置是否有效
     */
    public static boolean isValidFlowConfig(ServerFlowConfig config) {
        return config != null && config.getMaxOccupyRatio() >= 0 && config.getExceedCount() >= 0
            && config.getMaxAllowedQps() >= 0
            && FlowRuleUtil.isWindowConfigValid(config.getSampleCount(), config.getIntervalMs());
    }

    /**
     * 获取指定命名空间的超额数量配置
     * 如果命名空间有特定配置则返回特定值，否则返回全局配置值
     * 
     * @param namespace 命名空间
     * @return 超额数量配置值
     */
    public static double getExceedCount(String namespace) {
        AssertUtil.notEmpty(namespace, "namespace cannot be empty");
        ServerFlowConfig config = NAMESPACE_CONF.get(namespace);
        if (config != null) {
            return config.getExceedCount();
        }
        return exceedCount;
    }

    public static double getMaxOccupyRatio(String namespace) {
        AssertUtil.notEmpty(namespace, "namespace cannot be empty");
        ServerFlowConfig config = NAMESPACE_CONF.get(namespace);
        if (config != null) {
            return config.getMaxOccupyRatio();
        }
        return maxOccupyRatio;
    }

    public static int getIntervalMs(String namespace) {
        AssertUtil.notEmpty(namespace, "namespace cannot be empty");
        ServerFlowConfig config = NAMESPACE_CONF.get(namespace);
        if (config != null) {
            return config.getIntervalMs();
        }
        return intervalMs;
    }

    /**
     * 获取指定命名空间的采样数量配置
     * 采样数量用于滑动窗口统计，影响统计的精度和内存占用
     *
     * @param namespace 有效的命名空间
     * @return 命名空间的采样数量；如果命名空间没有自定义值，则使用全局值
     */
    public static int getSampleCount(String namespace) {
        AssertUtil.notEmpty(namespace, "namespace cannot be empty");
        ServerFlowConfig config = NAMESPACE_CONF.get(namespace);
        if (config != null) {
            return config.getSampleCount();
        }
        return sampleCount;
    }

    public static double getMaxAllowedQps() {
        return maxAllowedQps;
    }

    public static double getMaxAllowedQps(String namespace) {
        AssertUtil.notEmpty(namespace, "namespace cannot be empty");
        ServerFlowConfig config = NAMESPACE_CONF.get(namespace);
        if (config != null) {
            return config.getMaxAllowedQps();
        }
        return maxAllowedQps;
    }

    public static double getExceedCount() {
        return exceedCount;
    }

    public static double getMaxOccupyRatio() {
        return maxOccupyRatio;
    }

    public static Set<String> getNamespaceSet() {
        return namespaceSet;
    }

    public static int getPort() {
        return port;
    }

    public static int getIdleSeconds() {
        return idleSeconds;
    }

    public static int getIntervalMs() {
        return intervalMs;
    }

    public static int getSampleCount() {
        return sampleCount;
    }

    public static void setNamespaceSet(Set<String> namespaceSet) {
        applyNamespaceSetChange(namespaceSet);
    }

    /**
     * 判断当前Token服务器是否运行在嵌入式模式
     * 在嵌入式模式下，服务器本身也是服务的一部分
     * 
     * @return 是否为嵌入式模式
     */
    public static boolean isEmbedded() {
        return embedded;
    }

    /**
     * <p>设置Token服务器的嵌入式模式标志</p>
     * <p>
     * 注意：开发者不应手动调用此方法。
     * 嵌入式标志应由Sentinel在启动Token服务器时初始化。
     * </p>
     * <p>
     * 在嵌入式模式下，服务器会自动将自身的应用名称添加到命名空间集合中，
     * 这样服务器本身也可以参与集群流控。
     * </p>
     *
     * @param embedded Token服务器是否运行在嵌入式模式
     */
    public static void setEmbedded(boolean embedded) {
        ClusterServerConfigManager.embedded = embedded;
    }

    public static void setMaxAllowedQps(double maxAllowedQps) {
        ClusterServerConfigManager.maxAllowedQps = maxAllowedQps;
    }

    private ClusterServerConfigManager() {}
}
