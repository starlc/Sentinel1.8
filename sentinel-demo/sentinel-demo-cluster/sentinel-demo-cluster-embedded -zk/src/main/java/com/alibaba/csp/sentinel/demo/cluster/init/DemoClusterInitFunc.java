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
package com.alibaba.csp.sentinel.demo.cluster.init;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterParamFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.demo.cluster.DemoConstants;
import com.alibaba.csp.sentinel.demo.cluster.entity.ClusterGroupEntity;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.transport.config.TransportConfig;
import com.alibaba.csp.sentinel.util.AppNameUtil;
import com.alibaba.csp.sentinel.util.HostNameUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

/**
 * @author Eric Zhao
 */
public class DemoClusterInitFunc implements InitFunc {

    private static final String APP_NAME = AppNameUtil.getAppName();

    private final String remoteAddress = "localhost:8848";
    private final String groupId = "SENTINEL_GROUP";

    private final String flowDataId = APP_NAME + DemoConstants.FLOW_POSTFIX;
    private final String paramDataId = APP_NAME + DemoConstants.PARAM_FLOW_POSTFIX;
    private final String configDataId = APP_NAME + "-cluster-client-config";
    private final String clusterMapDataId = APP_NAME + DemoConstants.CLUSTER_MAP_POSTFIX;

    @Override
    public void init() throws Exception {
        // Register client dynamic rule data source.
        // 初始化动态规则属性，从Nacos加载流控规则和参数流控规则
//        initDynamicRuleProperty();
//
//        // Register token client related data source.
//        // Token client common config:
//        // 初始化客户端配置属性，包括请求超时时间等
//        initClientConfigProperty();
//        // Token client assign config (e.g. target token server) retrieved from assign map:
//        // 初始化客户端服务器分配属性，确定当前客户端应该连接哪个Token Server
//        initClientServerAssignProperty();
//
//        // Register token server related data source.
//        // Register dynamic rule data source supplier for token server:
//        // 注册集群规则提供者，为Token Server提供动态规则数据源
//        registerClusterRuleSupplier();
//        // Token server transport config extracted from assign map:
//        // 初始化服务器传输配置属性，包括服务器端口等
//        initServerTransportConfigProperty();
//
//        // Init cluster state property for extracting mode from cluster map data source.
//        // 初始化集群状态属性，从集群映射数据源中提取模式（客户端/服务器/未启动）
//        initStateProperty();
    }

    private void initDynamicRuleProperty() {
        // 创建流控规则的Nacos数据源，从Nacos配置中心读取流控规则
        // flowDataId格式为：${appName}-flow-rules
        ReadableDataSource<String, List<FlowRule>> ruleSource = new NacosDataSource<>(remoteAddress, groupId,
            flowDataId, source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
        // 将数据源注册到FlowRuleManager，用于单机模式下的流控规则管理
        FlowRuleManager.register2Property(ruleSource.getProperty());

        // 创建参数流控规则的Nacos数据源，从Nacos配置中心读取参数流控规则
        // paramDataId格式为：${appName}-param-rules
        ReadableDataSource<String, List<ParamFlowRule>> paramRuleSource = new NacosDataSource<>(remoteAddress, groupId,
            paramDataId, source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>() {}));
        // 将数据源注册到ParamFlowRuleManager，用于单机模式下的参数流控规则管理
        ParamFlowRuleManager.register2Property(paramRuleSource.getProperty());
    }

    private void initClientConfigProperty() {
        // 创建客户端配置的Nacos数据源，从Nacos配置中心读取客户端配置
        // configDataId格式为：${appName}-cluster-client-config
        ReadableDataSource<String, ClusterClientConfig> clientConfigDs = new NacosDataSource<>(remoteAddress, groupId,
            configDataId, source -> JSON.parseObject(source, new TypeReference<ClusterClientConfig>() {}));
        // 将数据源注册到ClusterClientConfigManager，用于管理客户端配置（如请求超时时间等）
        ClusterClientConfigManager.registerClientConfigProperty(clientConfigDs.getProperty());
    }

    private void initServerTransportConfigProperty() {
        // 创建服务器传输配置的Nacos数据源，从集群映射中提取服务器传输配置
        // clusterMapDataId格式为：${appName}-cluster-map
        ReadableDataSource<String, ServerTransportConfig> serverTransportDs = new NacosDataSource<>(remoteAddress, groupId,
            clusterMapDataId, source -> {
            // 解析集群映射数据，获取集群分组列表
            List<ClusterGroupEntity> groupList = JSON.parseObject(source, new TypeReference<List<ClusterGroupEntity>>() {});
            // 从集群分组列表中提取当前机器作为服务器的传输配置（如端口等）
            return Optional.ofNullable(groupList)
                .flatMap(this::extractServerTransportConfig)
                .orElse(null);
        });
        // 将数据源注册到ClusterServerConfigManager，用于管理服务器传输配置
        ClusterServerConfigManager.registerServerTransportProperty(serverTransportDs.getProperty());
    }

    private void registerClusterRuleSupplier() {
        // 注册集群流控规则属性提供者，该提供者根据命名空间创建数据源
        // 这里使用setPropertySupplier而不是register2Property的原因是：
        // 1. 在集群模式下，Token Server需要为多个命名空间提供流控规则
        // 2. 每个命名空间的规则需要动态创建和加载，而不是一次性加载所有规则
        // 3. setPropertySupplier提供了一个函数，可以根据命名空间动态创建对应的数据源
        // Flow rule dataId format: ${namespace}-flow-rules
        ClusterFlowRuleManager.setPropertySupplier(namespace -> {
            // 为每个命名空间创建一个Nacos数据源，从Nacos配置中心读取该命名空间的流控规则
            ReadableDataSource<String, List<FlowRule>> ds = new NacosDataSource<>(remoteAddress, groupId,
                namespace + DemoConstants.FLOW_POSTFIX, source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
            return ds.getProperty();
        });
        // 注册集群参数流控规则属性提供者，同样根据命名空间创建数据源
        ClusterParamFlowRuleManager.setPropertySupplier(namespace -> {
            // 为每个命名空间创建一个Nacos数据源，从Nacos配置中心读取该命名空间的参数流控规则
            ReadableDataSource<String, List<ParamFlowRule>> ds = new NacosDataSource<>(remoteAddress, groupId,
                namespace + DemoConstants.PARAM_FLOW_POSTFIX, source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>() {}));
            return ds.getProperty();
        });
    }

    private void initClientServerAssignProperty() {
        // 创建客户端服务器分配配置的Nacos数据源，从集群映射中提取客户端应该连接的服务器信息
        // Cluster map format:
        // [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
        // machineId: <ip@commandPort>, commandPort for port exposed to Sentinel dashboard (transport module)
        ReadableDataSource<String, ClusterClientAssignConfig> clientAssignDs = new NacosDataSource<>(remoteAddress, groupId,
            clusterMapDataId, source -> {
            // 解析集群映射数据，获取集群分组列表
            List<ClusterGroupEntity> groupList = JSON.parseObject(source, new TypeReference<List<ClusterGroupEntity>>() {});
            // 从集群分组列表中提取当前机器作为客户端应该连接的服务器配置（IP和端口）
            return Optional.ofNullable(groupList)
                .flatMap(this::extractClientAssignment)
                .orElse(null);
        });
        // 将数据源注册到ClusterClientConfigManager，用于管理客户端服务器分配配置
        ClusterClientConfigManager.registerServerAssignProperty(clientAssignDs.getProperty());
    }

    private void initStateProperty() {
        // 创建集群状态的Nacos数据源，从集群映射中提取当前机器的集群模式（客户端/服务器/未启动）
        // Cluster map format:
        // [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
        // machineId: <ip@commandPort>, commandPort for port exposed to Sentinel dashboard (transport module)
        ReadableDataSource<String, Integer> clusterModeDs = new NacosDataSource<>(remoteAddress, groupId,
            clusterMapDataId, source -> {
            // 解析集群映射数据，获取集群分组列表
            List<ClusterGroupEntity> groupList = JSON.parseObject(source, new TypeReference<List<ClusterGroupEntity>>() {});
            // 从集群分组列表中提取当前机器的集群模式
            return Optional.ofNullable(groupList)
                .map(this::extractMode)
                .orElse(ClusterStateManager.CLUSTER_NOT_STARTED);
        });
        // 将数据源注册到ClusterStateManager，用于管理集群状态
        ClusterStateManager.registerProperty(clusterModeDs.getProperty());
    }

    private int extractMode(List<ClusterGroupEntity> groupList) {
        // 从集群分组列表中提取当前机器的集群模式
        // If any server group machineId matches current, then it's token server.
        // 如果当前机器的machineId与任何服务器组的machineId匹配，则它是Token Server
        if (groupList.stream().anyMatch(this::machineEqual)) {
            return ClusterStateManager.CLUSTER_SERVER;
        }
        // If current machine belongs to any of the token server group, then it's token client.
        // Otherwise it's unassigned, should be set to NOT_STARTED.
        // 如果当前机器属于任何Token Server组的客户端集合，则它是Token Client
        // 否则它是未分配的，应该设置为NOT_STARTED
        boolean canBeClient = groupList.stream()
            .flatMap(e -> e.getClientSet().stream())
            .filter(Objects::nonNull)
            .anyMatch(e -> e.equals(getCurrentMachineId()));
        return canBeClient ? ClusterStateManager.CLUSTER_CLIENT : ClusterStateManager.CLUSTER_NOT_STARTED;
    }

    private Optional<ServerTransportConfig> extractServerTransportConfig(List<ClusterGroupEntity> groupList) {
        // 从集群分组列表中提取当前机器作为服务器的传输配置
        // 如果当前机器是Token Server，则返回对应的服务器传输配置（端口和空闲超时时间）
        return groupList.stream()
            .filter(this::machineEqual)
            .findAny()
            .map(e -> new ServerTransportConfig().setPort(e.getPort()).setIdleSeconds(600));
    }

    private Optional<ClusterClientAssignConfig> extractClientAssignment(List<ClusterGroupEntity> groupList) {
        // 从集群分组列表中提取当前机器作为客户端应该连接的服务器配置
        // 如果当前机器是Token Server，则不需要客户端分配配置
        if (groupList.stream().anyMatch(this::machineEqual)) {
            return Optional.empty();
        }
        // Build client assign config from the client set of target server group.
        // 从目标服务器组的客户端集合中构建客户端分配配置
        for (ClusterGroupEntity group : groupList) {
            // 如果当前机器在某个服务器组的客户端集合中，则返回该服务器组的IP和端口
            if (group.getClientSet().contains(getCurrentMachineId())) {
                String ip = group.getIp();
                Integer port = group.getPort();
                return Optional.of(new ClusterClientAssignConfig(ip, port));
            }
        }
        return Optional.empty();
    }

    private boolean machineEqual(/*@Valid*/ ClusterGroupEntity group) {
        // 判断当前机器的machineId是否与给定集群组的machineId相等
        return getCurrentMachineId().equals(group.getMachineId());
    }

    private String getCurrentMachineId() {
        // 获取当前机器的machineId，格式为：IP@commandPort
        // commandPort是暴露给Sentinel控制台的端口（transport模块）
        // Note: this may not work well for container-based env.
        return HostNameUtil.getIp() + SEPARATOR + TransportConfig.getRuntimePort();
    }

    private static final String SEPARATOR = "@";
}