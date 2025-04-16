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
package com.alibaba.csp.sentinel.property;

import com.alibaba.csp.sentinel.log.RecordLog;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 动态Sentinel属性类，是Sentinel框架中配置属性管理的核心实现类。
 * <p>
 * 该类实现了SentinelProperty接口，负责管理配置属性值并在配置更新时通知所有注册的监听器。
 * 它采用观察者模式设计，通过维护一个PropertyListener监听器集合，当配置发生变更时通知所有监听器。
 * </p>
 * <p>
 * 主要功能：
 * 1. 存储和管理配置属性值
 * 2. 提供监听器的注册和移除机制
 * 3. 在配置更新时通知所有监听器
 * 4. 确保只有在配置值真正变化时才通知监听器
 * </p>
 * 
 * @param <T> 配置属性值的类型
 */
public class DynamicSentinelProperty<T> implements SentinelProperty<T> {

    /**
     * 监听器集合，使用CopyOnWriteArraySet保证线程安全
     * 当配置更新时，将通知此集合中的所有监听器
     */
    protected Set<PropertyListener<T>> listeners = new CopyOnWriteArraySet<>();
    
    /**
     * 当前存储的配置属性值
     */
    private T value = null;

    /**
     * 默认构造函数，初始化一个空的动态属性对象，属性值为null
     */
    public DynamicSentinelProperty() {
    }

    /**
     * 带初始值的构造函数
     * 
     * @param value 初始的配置属性值
     */
    public DynamicSentinelProperty(T value) {
        super();
        this.value = value;
    }

    /**
     * 添加配置监听器
     * <p>
     * 将监听器添加到监听器集合中，并立即触发监听器的configLoad方法，
     * 使其能够立即加载当前的配置值，而不需要等待配置更新
     * </p>
     * 
     * @param listener 要添加的配置监听器
     */
    @Override
    public void addListener(PropertyListener<T> listener) {
        listeners.add(listener);
        listener.configLoad(value);
    }

    /**
     * 移除配置监听器
     * <p>
     * 将指定的监听器从监听器集合中移除，移除后该监听器将不再接收配置更新通知
     * </p>
     * 
     * @param listener 要移除的配置监听器
     */
    @Override
    public void removeListener(PropertyListener<T> listener) {
        listeners.remove(listener);
    }

    /**
     * 更新配置属性值并通知所有监听器
     * <p>
     * 该方法首先比较新旧值是否相等，只有在值确实发生变化时才进行更新和通知操作，
     * 这样可以避免不必要的通知，提高性能
     * </p>
     * 
     * @param newValue 新的配置属性值
     * @return 如果值被更新则返回true，如果新值与旧值相等则返回false
     */
    @Override
    public boolean updateValue(T newValue) {
        if (isEqual(value, newValue)) {
            return false;
        }
        RecordLog.info("[DynamicSentinelProperty] Config will be updated to: {}", newValue);

        value = newValue;
        for (PropertyListener<T> listener : listeners) {
            listener.configUpdate(newValue);
        }
        return true;
    }

    /**
     * 比较两个配置值是否相等
     * <p>
     * 该方法处理了null值的情况，确保在比较时不会发生NullPointerException
     * </p>
     * 
     * @param oldValue 旧的配置值
     * @param newValue 新的配置值
     * @return 如果两个值相等返回true，否则返回false
     */
    private boolean isEqual(T oldValue, T newValue) {
        if (oldValue == null && newValue == null) {
            return true;
        }

        if (oldValue == null) {
            return false;
        }

        return oldValue.equals(newValue);
    }

    /**
     * 关闭并清理资源
     * <p>
     * 清空所有监听器，通常在不再需要此属性对象时调用，
     * 以避免内存泄漏和不必要的通知
     * </p>
     */
    public void close() {
        listeners.clear();
    }
}
