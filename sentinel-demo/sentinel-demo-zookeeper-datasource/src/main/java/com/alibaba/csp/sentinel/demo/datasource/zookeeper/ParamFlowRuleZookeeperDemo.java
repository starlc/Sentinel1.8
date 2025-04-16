package com.alibaba.csp.sentinel.demo.datasource.zookeeper;

import java.util.List;

import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.zookeeper.ZookeeperDataSource;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

/**
 * Zookeeper ReadableDataSource Demo for Parameter Flow Control
 *
 * @author your_name
 */
public class ParamFlowRuleZookeeperDemo {

    public static void main(String[] args) {
        // 加载热点参数流控规则
        loadParamFlowRules();
        
        // 也可以使用另一种方式加载
        // loadParamFlowRulesWithGroupAndDataId();
        
        // 这里可以添加业务逻辑
        System.out.println("Parameter flow control rules loaded from ZooKeeper");
    }

    /**
     * 直接使用路径方式加载热点参数流控规则
     */
    private static void loadParamFlowRules() {
        final String remoteAddress = "localhost:2181"; // ZooKeeper服务器地址
        final String path = "/Sentinel-Demo/SYSTEM-CODE-DEMO-PARAM-FLOW"; // ZooKeeper中存储规则的路径

        // 创建ZooKeeper数据源，并指定转换器将JSON字符串转换为ParamFlowRule列表
        ReadableDataSource<String, List<ParamFlowRule>> paramFlowRuleDataSource = 
            new ZookeeperDataSource<>(remoteAddress, path,
                source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>() {}));
        
        // 将数据源注册到ParamFlowRuleManager
        ParamFlowRuleManager.register2Property(paramFlowRuleDataSource.getProperty());
    }

    /**
     * 使用groupId和dataId方式加载热点参数流控规则
     * 这种方式更便于与其他数据源(如Nacos)进行切换
     */
    private static void loadParamFlowRulesWithGroupAndDataId() {
        final String remoteAddress = "localhost:2181"; // ZooKeeper服务器地址
        final String groupId = "Sentinel-Demo"; // 规则分组ID
        final String dataId = "SYSTEM-CODE-DEMO-PARAM-FLOW"; // 规则数据ID

        // 创建ZooKeeper数据源，使用groupId和dataId方式
        // 规则会持久化到ZooKeeper的/groupId/dataId节点
        ReadableDataSource<String, List<ParamFlowRule>> paramFlowRuleDataSource = 
            new ZookeeperDataSource<>(remoteAddress, groupId, dataId,
                source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>() {}));
        
        // 将数据源注册到ParamFlowRuleManager
        ParamFlowRuleManager.register2Property(paramFlowRuleDataSource.getProperty());
    }
    
    /**
     * 在ZooKeeper中存储的热点参数流控规则JSON格式示例：
     * [
     *   {
     *     "resource": "resourceName",
     *     "grade": 1,         // 限流阈值类型，1表示QPS模式
     *     "paramIdx": 0,      // 热点参数的索引
     *     "count": 5,         // 限流阈值
     *     "controlBehavior": 0,// 流控效果，0表示快速失败
     *     "maxQueueingTimeMs": 0,
     *     "burstCount": 0,
     *     "durationInSec": 1,
     *     "paramFlowItemList": [ // 特例项，可以针对特定参数值单独设置阈值
     *       {
     *         "object": "100", // 参数值
     *         "classType": "java.lang.Integer", // 参数类型
     *         "count": 10      // 该特定参数值的阈值
     *       }
     *     ]
     *   }
     * ]
     */
}