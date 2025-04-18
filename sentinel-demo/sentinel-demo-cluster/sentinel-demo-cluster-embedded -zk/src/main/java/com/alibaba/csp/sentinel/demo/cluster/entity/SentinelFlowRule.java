package com.alibaba.csp.sentinel.demo.cluster.entity;

public class SentinelFlowRule implements FlowRuleEntity {
    private Long flowId;

    private String resource;

    private int count;

    private int grade;

    private int strategy;

    private int clusterMode;
    
    private int status;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;  
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getGrade() {
        return grade;
    }

    public void setGrade(int grade) {
        this.grade = grade;
    }

    public int getStrategy() {
        return strategy;
    }

    public void setStrategy(int strategy) {
        this.strategy = strategy;
    }

    public int getClusterMode() {
        return clusterMode;
    }

    public void setClusterMode(int clusterMode) {
        this.clusterMode = clusterMode;
    }
}
