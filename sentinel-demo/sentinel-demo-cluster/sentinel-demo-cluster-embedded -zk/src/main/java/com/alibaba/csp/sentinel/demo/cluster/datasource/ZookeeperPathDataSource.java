package com.alibaba.csp.sentinel.datasource.zookeeper;

import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.datasource.AbstractDataSource;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.util.StringUtil;
import org.apache.curator.framework.AuthInfo;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 基于ZooKeeper实现的目录监听数据源，用于从ZooKeeper中读取Sentinel规则配置。
 * 该类通过ZooKeeper的节点监听机制，实现了对指定路径下所有子节点的监听。
 * 当ZooKeeper中的任何子节点配置发生变化时，会自动触发监听器更新Sentinel中的规则。
 * 每个子节点的数据将被转换为一个FlowRule对象。
 *
 * @author guonanjun
 */
public class ZookeeperPathDataSource extends AbstractDataSource<String, List<FlowRule>> {

    // ZooKeeper客户端连接重试次数
    private static final int RETRY_TIMES = 3;
    // 重试间隔时间（毫秒）
    private static final int SLEEP_TIME = 1000;

    // 静态Map用于缓存ZooKeeper客户端连接，避免重复创建连接
    // key为serverAddr+authInfo的组合，value为对应的ZooKeeper客户端
    private static volatile Map<String, CuratorFramework> zkClientMap = new HashMap<>();
    // 用于同步zkClientMap的锁对象
    private static final Object lock = new Object();

    // 用于处理ZooKeeper节点变更事件的线程池，采用单线程模式
    private final ExecutorService pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(1), new NamedThreadFactory("sentinel-zookeeper-ds-update", true),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    // ZooKeeper节点变更监听器
    private CuratorCacheListener listener;
    // ZooKeeper中存储配置的节点路径
    private final String path;

    // 当前数据源使用的ZooKeeper客户端
    private CuratorFramework zkClient = null;
    // 用于监听节点变化的缓存对象
    private CuratorCache nodeCache = null;

    /**
     * 构造函数，直接指定ZooKeeper服务地址和节点路径
     * 
     * @param serverAddr ZooKeeper服务器地址，格式为host:port
     * @param path 配置存储的节点路径，该路径下的所有子节点将被监听
     * @param parser 将ZooKeeper中存储的字符串转换为FlowRule对象的转换器
     */
    public ZookeeperPathDataSource(final String serverAddr, final String path, Converter<String, FlowRule> parser) {
        super(source -> {
            // 将单个FlowRule转换为List<FlowRule>的适配器
            try {
                if (StringUtil.isBlank(source)) {
                    return new ArrayList<>();
                }
                FlowRule rule = parser.convert(source);
                return Collections.singletonList(rule);
            } catch (Exception e) {
                RecordLog.warn("[ZookeeperPathDataSource] Failed to parse source: " + source, e);
                return new ArrayList<>();
            }
        });
        
        if (StringUtil.isBlank(serverAddr) || StringUtil.isBlank(path)) {
            throw new IllegalArgumentException(String.format("Bad argument: serverAddr=[%s], path=[%s]", serverAddr, path));
        }
        this.path = path;

        init(serverAddr, null);
    }

    /**
     * Nacos风格的构造函数，通过groupId和dataId组合生成节点路径
     * 这种方式与Sentinel中其他数据源保持一致的使用方式，方便用户统一管理不同类型的数据源
     *
     * @param serverAddr ZooKeeper服务器地址
     * @param groupId 配置分组ID，对应ZooKeeper的一级节点
     * @param dataId 配置数据ID，对应ZooKeeper的二级节点
     * @param parser 将ZooKeeper中存储的字符串转换为FlowRule对象的转换器
     */
    public ZookeeperPathDataSource(final String serverAddr, final String groupId, final String dataId,
                               Converter<String, FlowRule> parser) {
        this(serverAddr, getPath(groupId, dataId), parser);
    }

    /**
     * 带认证信息的构造函数，用于连接需要认证的ZooKeeper服务
     * 当ZooKeeper启用ACL访问控制时，需要提供认证信息才能访问受保护的节点
     *
     * @param serverAddr ZooKeeper服务器地址
     * @param authInfos ZooKeeper的认证信息列表
     * @param groupId 配置分组ID
     * @param dataId 配置数据ID
     * @param parser 将ZooKeeper中存储的字符串转换为FlowRule对象的转换器
     */
    public ZookeeperPathDataSource(final String serverAddr, final List<AuthInfo> authInfos, final String groupId, final String dataId,
                               Converter<String, FlowRule> parser) {
        super(source -> {
            // 将单个FlowRule转换为List<FlowRule>的适配器
            try {
                if (StringUtil.isBlank(source)) {
                    return new ArrayList<>();
                }
                FlowRule rule = parser.convert(source);
                return Collections.singletonList(rule);
            } catch (Exception e) {
                RecordLog.warn("[ZookeeperPathDataSource] Failed to parse source: " + source, e);
                return new ArrayList<>();
            }
        });
        
        if (StringUtil.isBlank(serverAddr) || StringUtil.isBlank(groupId) || StringUtil.isBlank(dataId)) {
            throw new IllegalArgumentException(String.format("Bad argument: serverAddr=[%s], authInfos=[%s], groupId=[%s], dataId=[%s]", serverAddr, authInfos, groupId, dataId));
        }
        this.path = getPath(groupId, dataId);

        init(serverAddr, authInfos);
    }

    /**
     * 初始化数据源，包括初始化ZooKeeper连接和加载初始配置
     *
     * @param serverAddr ZooKeeper服务器地址
     * @param authInfos ZooKeeper的认证信息
     */
    private void init(final String serverAddr, final List<AuthInfo> authInfos) {
        // 初始化ZooKeeper连接和监听器
        initZookeeperListener(serverAddr, authInfos);
        // 加载初始配置
        loadInitialConfig();
    }

    /**
     * 加载初始配置
     * 在数据源初始化时，从ZooKeeper中读取配置并更新到Sentinel中
     */
    private void loadInitialConfig() {
        try {
            // 从ZooKeeper中加载配置并转换为目标类型
            List<FlowRule> rules = loadConfig();
            if (rules == null || rules.isEmpty()) {
                RecordLog.warn("[ZookeeperPathDataSource] WARN: initial config is null or empty, you may have to check your data source");
            } else {
                RecordLog.info("[ZookeeperPathDataSource] Loaded {} flow rules", rules.size());
            }
            // 更新配置到Sentinel的内存中
            getProperty().updateValue(rules);
        } catch (Exception ex) {
            RecordLog.warn("[ZookeeperPathDataSource] Error when loading initial config", ex);
        }
    }

    /**
     * 初始化ZooKeeper连接和节点监听器
     * 该方法负责创建ZooKeeper客户端连接，并设置节点变更监听器
     *
     * @param serverAddr ZooKeeper服务器地址
     * @param authInfos ZooKeeper的认证信息
     */
    private void initZookeeperListener(final String serverAddr, final List<AuthInfo> authInfos) {
        try {
            // 创建节点变更监听器，监听所有子节点的变化
            this.listener = CuratorCacheListener.builder().forChanges((oldData, newData) -> {
                try {
                    // 当任何子节点发生变化时，重新加载所有规则
                    List<FlowRule> rules = loadConfig();
                    RecordLog.info("[ZookeeperPathDataSource] Node changed, reloaded {} flow rules for path: {}",
                            rules.size(), path);
                    // 更新新的配置值到Sentinel中
                    getProperty().updateValue(rules);
                } catch (Exception ex) {
                    RecordLog.warn("[ZookeeperPathDataSource] loadConfig exception", ex);
                }
            }).build();
            
            // 生成ZooKeeper客户端的缓存键
            String zkKey = getZkKey(serverAddr, authInfos);
            // 检查是否已存在相同配置的ZooKeeper客户端
            if (zkClientMap.containsKey(zkKey)) {
                // 复用已有的客户端连接
                this.zkClient = zkClientMap.get(zkKey);
            } else {
                // 使用同步锁保证线程安全，避免多线程同时创建客户端
                synchronized (lock) {
                    // 双重检查，防止在等待锁的过程中已有其他线程创建了客户端
                    if (!zkClientMap.containsKey(zkKey)) {
                        CuratorFramework zc = null;
                        // 根据是否有认证信息创建不同的客户端
                        if (authInfos == null || authInfos.size() == 0) {
                            // 创建无认证的客户端
                            zc = CuratorFrameworkFactory.newClient(serverAddr, new ExponentialBackoffRetry(SLEEP_TIME, RETRY_TIMES));
                        } else {
                            // 创建带认证的客户端
                            zc = CuratorFrameworkFactory.builder().
                                    connectString(serverAddr).
                                    retryPolicy(new ExponentialBackoffRetry(SLEEP_TIME, RETRY_TIMES)).
                                    authorization(authInfos).
                                    build();
                        }
                        this.zkClient = zc;
                        // 启动ZooKeeper客户端
                        this.zkClient.start();
                        // 创建新的Map并更新缓存，保证线程安全
                        Map<String, CuratorFramework> newZkClientMap = new HashMap<>(zkClientMap.size());
                        newZkClientMap.putAll(zkClientMap);
                        newZkClientMap.put(zkKey, zc);
                        zkClientMap = newZkClientMap;
                    } else {
                        // 如果在获取锁的过程中已有其他线程创建了客户端，则直接使用
                        this.zkClient = zkClientMap.get(zkKey);
                    }
                }
            }

            // 创建节点缓存，用于监听指定路径及其子节点的变化
            this.nodeCache = CuratorCache.build(this.zkClient, this.path);
            // 添加监听器，使用线程池处理回调
            this.nodeCache.listenable().addListener(this.listener, this.pool);
            // 启动节点缓存
            this.nodeCache.start();
        } catch (Exception e) {
            RecordLog.warn("[ZookeeperPathDataSource] Error occurred when initializing Zookeeper data source", e);
            e.printStackTrace();
        }
    }

    /**
     * 从ZooKeeper中读取配置数据
     * 实现AbstractDataSource中的抽象方法，用于从数据源读取原始字符串配置
     * 该方法会读取指定路径下所有子节点的数据，并将它们合并为一个JSON数组字符串
     *
     * @return 从ZooKeeper节点读取的字符串配置
     * @throws Exception 如果读取过程中发生错误
     */
    @Override
    public String readSource() throws Exception {
        // 检查ZooKeeper客户端是否已初始化
        if (this.zkClient == null) {
            throw new IllegalStateException("Zookeeper has not been initialized or error occurred");
        }
        
        // 获取所有子节点
        List<String> children;
        try {
            children = zkClient.getChildren().forPath(path);
        } catch (Exception e) {
            RecordLog.warn("[ZookeeperPathDataSource] Failed to get children for path: " + path, e);
            return "";
        }
        
        // 如果没有子节点，返回空字符串
        if (children == null || children.isEmpty()) {
            return "";
        }
        
        // 读取每个子节点的数据，并将其作为单独的规则处理
        StringBuilder resultBuilder = new StringBuilder();
        for (String child : children) {
            String childPath = path + "/" + child;
            try {
                byte[] data = zkClient.getData().forPath(childPath);
                if (data != null && data.length > 0) {
                    String ruleData = new String(data);
                    // 处理每个子节点的数据
                    resultBuilder.append(ruleData).append(";");
                }
            } catch (Exception e) {
                RecordLog.warn("[ZookeeperPathDataSource] Failed to get data for path: " + childPath, e);
            }
        }
        
        return resultBuilder.toString();
    }

    /**
     * 关闭数据源，释放资源
     * 实现AutoCloseable接口，用于在数据源不再使用时清理资源
     *
     * @throws Exception 如果关闭过程中发生错误
     */
    @Override
    public void close() throws Exception {
        // 关闭节点缓存，移除监听器
        if (this.nodeCache != null) {
            this.nodeCache.listenable().removeListener(listener);
            this.nodeCache.close();
        }
        // 关闭ZooKeeper客户端
        // 注意：这里不会从zkClientMap中移除客户端，因为可能有其他数据源在使用
        if (this.zkClient != null) {
            this.zkClient.close();
        }
        // 关闭线程池
        pool.shutdown();
    }

    /**
     * 根据groupId和dataId生成ZooKeeper的节点路径
     *
     * @param groupId 配置分组ID
     * @param dataId 配置数据ID
     * @return 格式化的ZooKeeper节点路径，例如：/groupId/dataId
     */
    private static String getPath(String groupId, String dataId) {
        return String.format("/%s/%s", groupId, dataId);
    }

    /**
     * 生成ZooKeeper客户端的缓存键
     * 用于在zkClientMap中标识不同的ZooKeeper连接配置
     *
     * @param serverAddr ZooKeeper服务器地址
     * @param authInfos ZooKeeper的认证信息
     * @return 用于缓存ZooKeeper客户端的键
     */
    private String getZkKey(final String serverAddr, final List<AuthInfo> authInfos) {
        // 如果没有认证信息，直接使用服务器地址作为键
        if (authInfos == null || authInfos.size() == 0) {
            return serverAddr;
        }
        // 有认证信息时，将服务器地址和认证信息组合作为键
        StringBuilder builder = new StringBuilder(64);
        builder.append(serverAddr).append(getAuthInfosKey(authInfos));
        return builder.toString();
    }

    /**
     * 将认证信息列表转换为字符串，用于生成缓存键
     *
     * @param authInfos ZooKeeper的认证信息列表
     * @return 认证信息的字符串表示
     */
    private String getAuthInfosKey(List<AuthInfo> authInfos) {
        StringBuilder builder = new StringBuilder(32);
        for (AuthInfo authInfo : authInfos) {
            if (authInfo == null) {
                builder.append("{}");
            } else {
                // 格式化认证信息，包括认证方案(scheme)和认证数据(auth)
                builder.append("{").append("sc=").append(authInfo.getScheme())
                      .append(",au=").append(Arrays.toString(authInfo.getAuth())).append("}");
            }
        }
        return builder.toString();
    }

    /**
     * 获取当前数据源使用的ZooKeeper客户端
     * 主要用于测试和扩展功能
     *
     * @return 当前使用的CuratorFramework客户端实例
     */
    protected CuratorFramework getZkClient() {
        return this.zkClient;
    }
}
