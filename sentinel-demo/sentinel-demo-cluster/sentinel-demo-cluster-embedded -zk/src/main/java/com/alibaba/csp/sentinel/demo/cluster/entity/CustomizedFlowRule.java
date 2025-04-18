package com.alibaba.csp.sentinel.demo.cluster.entity;

public class CustomizedFlowRule implements FlowRuleEntity {
    private Long flowId;
    
    private String resource;

    private int count;

    private int grade;

    private int strategy;

    private int port;

    private String resourceVal;

    private String releationKeys;

    private int status;
    
    public int getPort() {
        return port; 
    }

    public void setPort(int port) {
        this.port = port; 
    }

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

    public String getResourceVal() {
        return resourceVal;
    }

    public void setResourceVal(String resourceVal) {
        this.resourceVal = resourceVal;
    }

    public String getReleationKeys() {
        return releationKeys;
    }

    public void setReleationKeys(String releationKeys) {
        this.releationKeys = releationKeys;
    }
}
