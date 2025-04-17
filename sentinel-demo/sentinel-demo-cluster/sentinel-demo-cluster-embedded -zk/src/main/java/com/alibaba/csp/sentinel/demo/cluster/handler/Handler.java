package com.alibaba.csp.sentinel.demo.cluster.handler;

/**
 * Handler接口，提供获取资源名称和状态的方法
 */
public interface Handler {
    
    /**
     * 获取资源名称
     * 
     * @return 资源名称
     */
    String getResource();
    
    /**
     * 获取状态
     * 
     * @return 状态码
     */
    int getStatus();
}