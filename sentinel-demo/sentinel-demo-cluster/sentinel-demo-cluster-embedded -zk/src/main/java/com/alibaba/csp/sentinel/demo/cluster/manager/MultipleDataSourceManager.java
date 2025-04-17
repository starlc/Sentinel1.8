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
package com.alibaba.csp.sentinel.demo.cluster.manager;

import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.property.DynamicSentinelProperty;
import com.alibaba.csp.sentinel.property.PropertyListener;
import com.alibaba.csp.sentinel.property.SentinelProperty;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.util.AssertUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多数据源管理器，支持注册多个数据源，并在数据发生变化时将结果同步到FlowRuleManager
 *
 * @author Your Name
 */
public class MultipleDataSourceManager {

    private static final MultipleDataSourceManager INSTANCE = new MultipleDataSourceManager();

    /**
     * 存储所有注册的数据源及其对应的监听器
     */
    private final Map<String, DataSourceWrapper> dataSourceMap = new ConcurrentHashMap<>();
    
    /**
     * 合并后的规则属性
     */
    private final SentinelProperty<List<FlowRule>> mergedProperty = new DynamicSentinelProperty<>();
    
    /**
     * 存储每个数据源最新的规则
     */
    private final Map<String, List<FlowRule>> ruleMap = new ConcurrentHashMap<>();

    private MultipleDataSourceManager() {
        // 注册合并后的属性到FlowRuleManager
        FlowRuleManager.register2Property(mergedProperty);
    }

    public static MultipleDataSourceManager getInstance() {
        return INSTANCE;
    }

    /**
     * 注册数据源
     *
     * @param dataSourceId 数据源ID
     * @param dataSource 数据源
     */
    public synchronized void registerDataSource(String dataSourceId, ReadableDataSource<String, List<FlowRule>> dataSource)
            throws Exception {
        AssertUtil.notNull(dataSourceId, "dataSourceId cannot be null");
        AssertUtil.notNull(dataSource, "dataSource cannot be null");
        
        // 如果已经注册过，先移除
        if (dataSourceMap.containsKey(dataSourceId)) {
            unregisterDataSource(dataSourceId);
        }
        
        // 创建监听器
        PropertyListener<List<FlowRule>> listener = new PropertyListener<List<FlowRule>>() {
            @Override
            public void configUpdate(List<FlowRule> value) {
                // 更新规则
                updateRules(dataSourceId, value);
            }

            @Override
            public void configLoad(List<FlowRule> value) {
                // 加载规则
                updateRules(dataSourceId, value);
            }
        };
        
        // 注册监听器
        SentinelProperty<List<FlowRule>> property = dataSource.getProperty();
        property.addListener(listener);
        
        // 保存数据源和监听器
        dataSourceMap.put(dataSourceId, new DataSourceWrapper(dataSource, property, listener));
        
        // 初始化规则
        List<FlowRule> rules = dataSource.loadConfig();
        if (rules != null) {
            updateRules(dataSourceId, rules);
        }
    }

    /**
     * 取消注册数据源
     *
     * @param dataSourceId 数据源ID
     */
    public synchronized void unregisterDataSource(String dataSourceId) {
        DataSourceWrapper wrapper = dataSourceMap.remove(dataSourceId);
        if (wrapper != null) {
            wrapper.property.removeListener(wrapper.listener);
            ruleMap.remove(dataSourceId);
            // 重新计算合并后的规则
            mergeAllRules();
        }
    }

    /**
     * 更新指定数据源的规则
     *
     * @param dataSourceId 数据源ID
     * @param rules 规则列表
     */
    private synchronized void updateRules(String dataSourceId, List<FlowRule> rules) {
        ruleMap.put(dataSourceId, rules);
        mergeAllRules();
    }

    /**
     * 合并所有数据源的规则
     */
    private void mergeAllRules() {
        List<FlowRule> mergedRules = new ArrayList<>();
        for (List<FlowRule> rules : ruleMap.values()) {
            if (rules != null) {
                mergedRules.addAll(rules);
            }
        }
        mergedProperty.updateValue(mergedRules);
    }

    /**
     * 数据源包装类，保存数据源及其对应的监听器
     */
    private static class DataSourceWrapper {
        final ReadableDataSource<String, List<FlowRule>> dataSource;
        final SentinelProperty<List<FlowRule>> property;
        final PropertyListener<List<FlowRule>> listener;

        DataSourceWrapper(ReadableDataSource<String, List<FlowRule>> dataSource, 
                          SentinelProperty<List<FlowRule>> property,
                          PropertyListener<List<FlowRule>> listener) {
            this.dataSource = dataSource;
            this.property = property;
            this.listener = listener;
        }
    }
}