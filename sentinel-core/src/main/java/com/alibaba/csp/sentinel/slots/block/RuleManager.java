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
package com.alibaba.csp.sentinel.slots.block;

import com.alibaba.csp.sentinel.util.function.Function;
import com.alibaba.csp.sentinel.util.function.Predicate;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 统一规则管理工具，主要用于正则规则和简单规则的匹配和缓存。
 * 该类是Sentinel框架中规则管理的核心组件，负责：
 * 1. 管理和存储原始规则集合
 * 2. 将规则分为正则规则和简单规则两类进行高效管理
 * 3. 提供正则规则的匹配结果缓存，避免重复匹配带来的性能损失
 * 4. 支持规则的动态更新和查询
 * 
 * @author quguai
 * @date 2023/10/9 20:35
 */
public class RuleManager<R> {

    /**
     * 存储原始规则集合，key为资源名称，value为该资源对应的规则列表
     */
    private Map<String, List<R>> originalRules = new HashMap<>();
    
    /**
     * 存储正则表达式规则，key为编译后的Pattern对象，value为对应的规则列表
     */
    private Map<Pattern, List<R>> regexRules = new HashMap<>();
    
    /**
     * 正则规则匹配结果的缓存，用于提高性能
     * key为资源名称，value为该资源匹配到的所有正则规则列表
     */
    private Map<String, List<R>> regexCacheRules = new HashMap<>();
    
    /**
     * 存储简单规则（非正则规则），key为资源名称，value为规则列表
     */
    private Map<String, List<R>> simpleRules = new HashMap<>();
    
    /**
     * 规则生成器，用于在匹配正则规则时对规则进行转换或处理
     * 默认为恒等函数，即不做任何处理直接返回原规则列表
     */
    private Function<List<R>, List<R>> generator = Function.identity();

    /**
     * 规则判断器，用于判断一个规则是否为正则规则
     */
    private final Predicate<R> predicate;

    /**
     * 默认构造函数，使用默认的规则判断器
     * 默认判断逻辑：规则是AbstractRule类型且isRegex()返回true
     */
    public RuleManager() {
        predicate = r -> r instanceof AbstractRule && ((AbstractRule) r).isRegex();
    }

    /**
     * 带参数的构造函数，允许自定义规则生成器和规则判断器
     * 
     * @param generator 自定义规则生成器，用于处理匹配到的规则列表
     * @param predicate 自定义规则判断器，用于判断规则是否为正则规则
     */
    public RuleManager(Function<List<R>, List<R>> generator, Predicate<R> predicate) {
        this.generator = generator;
        this.predicate = predicate;
    }

    /**
     * 从数据源更新规则，将规则按照正则表达式拆分，
     * 重建正则规则缓存以减少发布规则时带来的性能损失。
     * 
     * 该方法是规则更新的核心方法，主要流程：
     * 1. 更新原始规则集合
     * 2. 将规则分为正则规则和简单规则两类
     * 3. 重建正则规则和简单规则的映射关系
     * 4. 通过setRules方法重建正则规则缓存
     *
     * @param rulesMap 原始规则映射，key为资源名称，value为规则列表
     */
    public void updateRules(Map<String, List<R>> rulesMap) {
        originalRules = rulesMap;
        Map<Pattern, List<R>> regexRules = new HashMap<>();
        Map<String, List<R>> simpleRules = new HashMap<>();
        for (Map.Entry<String, List<R>> entry : rulesMap.entrySet()) {
            String resource = entry.getKey();
            List<R> rules = entry.getValue();

            List<R> rulesOfSimple = new ArrayList<>();
            List<R> rulesOfRegex = new ArrayList<>();
            for (R rule : rules) {
                if (predicate.test(rule)) {
                    rulesOfRegex.add(rule);
                } else {
                    rulesOfSimple.add(rule);
                }
            }
            if (!rulesOfRegex.isEmpty()) {
                regexRules.put(Pattern.compile(resource), rulesOfRegex);
            }
            if (!rulesOfSimple.isEmpty()) {
                simpleRules.put(resource, rulesOfSimple);
            }
        }
        // rebuild regex cache rules
        setRules(regexRules, simpleRules);
    }

    /**
     * 根据资源名称获取规则，将正则匹配后的规则列表保存到缓存中以提高性能
     * 
     * 该方法是规则查询的核心方法，查询流程：
     * 1. 首先获取资源对应的简单规则
     * 2. 如果没有正则规则，直接返回简单规则
     * 3. 检查正则规则缓存中是否已有该资源的匹配结果
     * 4. 如果缓存中没有，则进行正则匹配并将结果存入缓存
     * 5. 合并简单规则和正则规则的匹配结果返回
     * 
     * @param resource 资源名称
     * @return 匹配的规则列表
     */
    public List<R> getRules(String resource) {
        List<R> result = new ArrayList<>(simpleRules.getOrDefault(resource, Collections.emptyList()));
        if (regexRules.isEmpty()) {
            return result;
        }
        if (regexCacheRules.containsKey(resource)) {
            result.addAll(regexCacheRules.get(resource));
            return result;
        }
        synchronized (this) {
            if (regexCacheRules.containsKey(resource)) {
                result.addAll(regexCacheRules.get(resource));
                return result;
            }
            List<R> compilers = matcherFromRegexRules(resource);
            regexCacheRules.put(resource, compilers);
            result.addAll(compilers);
            return result;
        }
    }

    /**
     * 获取所有规则，包括正则规则和简单规则
     * 
     * 该方法会遍历所有的正则规则和简单规则，将它们合并到一个列表中返回
     * 
     * @return 所有规则的列表
     */
    public List<R> getRules() {
        List<R> rules = new ArrayList<>();
        for (Map.Entry<Pattern, List<R>> entry : regexRules.entrySet()) {
            rules.addAll(entry.getValue());
        }
        for (Map.Entry<String, List<R>> entry : simpleRules.entrySet()) {
            rules.addAll(entry.getValue());
        }
        return rules;
    }

    /**
     * 获取原始规则集合，包括正则规则和简单规则
     * 
     * 与getRules()方法不同，该方法直接返回原始的规则映射关系，保留了资源名称与规则列表的对应关系
     * 
     * @return 原始规则映射
     */
    public Map<String, List<R>> getOriginalRules() {
        return originalRules;
    }

    /**
     * 判断指定资源是否配置了规则
     * 
     * 该方法通过调用getRules(resource)方法获取资源对应的规则列表，
     * 然后判断列表是否为空来确定是否存在规则配置
     * 
     * @param resource 资源名称
     * @return 是否存在规则配置
     */

    public boolean hasConfig(String resource) {
        if (resource == null) {
            return false;
        }
        return !getRules(resource).isEmpty();
    }

    /**
     * 检查规则的正则表达式是否有效
     * 
     * 该方法用于验证规则中的正则表达式是否合法：
     * 1. 如果规则不是正则规则，则直接返回true
     * 2. 如果是正则规则，则尝试编译正则表达式，编译成功返回true，否则返回false
     * 
     * @param rule 待检查的规则
     * @return 正则表达式是否有效
     */
    public static boolean checkRegexResourceField(AbstractRule rule) {
        if (!rule.isRegex()) {
            return true;
        }
        String resourceName = rule.getResource();
        try {
            Pattern.compile(resourceName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从正则规则中匹配资源名称，返回匹配到的规则列表
     * 
     * 该方法遍历所有正则规则，找出与给定资源名称匹配的规则：
     * 1. 遍历所有正则规则的Pattern和对应的规则列表
     * 2. 使用Pattern.matcher().matches()方法判断资源名称是否匹配正则表达式
     * 3. 对匹配到的规则列表应用generator函数进行处理
     * 4. 将处理后的规则添加到结果列表中
     * 
     * @param resource 资源名称
     * @return 匹配到的规则列表
     */
    private List<R> matcherFromRegexRules(String resource) {
        List<R> compilers = new ArrayList<>();
        for (Map.Entry<Pattern, List<R>> entry : regexRules.entrySet()) {
            if (entry.getKey().matcher(resource).matches()) {
                compilers.addAll(generator.apply(entry.getValue()));
            }
        }
        return compilers;
    }

    /**
     * 设置规则并重建正则规则缓存
     * 
     * 该方法是内部方法，用于在规则更新时重建正则规则缓存：
     * 1. 更新正则规则和简单规则的映射关系
     * 2. 如果没有正则规则，则清空正则规则缓存
     * 3. 如果有正则规则，则重建正则规则缓存，对已缓存的资源重新进行正则匹配
     * 
     * 该方法使用synchronized关键字确保线程安全，防止并发更新导致的问题
     * 
     * @param regexRules 正则规则映射
     * @param simpleRules 简单规则映射
     */
    private synchronized void setRules(Map<Pattern, List<R>> regexRules, Map<String, List<R>> simpleRules) {
        this.regexRules = regexRules;
        this.simpleRules = simpleRules;
        if (regexRules.isEmpty()) {
            this.regexCacheRules = Collections.emptyMap();
            return;
        }
        // 重建正则规则缓存
        Map<String, List<R>> rebuildCacheRule = new HashMap<>(regexCacheRules.size());
        for (String resource : regexCacheRules.keySet()) {
            rebuildCacheRule.put(resource, matcherFromRegexRules(resource));
        }
        this.regexCacheRules = rebuildCacheRule;
    }
}
