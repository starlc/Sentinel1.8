# Sentinel-Core 核心接口及组件说明

## 1. 核心接口概述

Sentinel-Core是阿里巴巴开源的流量控制组件，其核心设计理念是通过资源保护的方式来实现流量控制、熔断降级、系统负载保护等功能。以下是Sentinel-Core的核心接口及其作用：

## 2. 入口类

### 2.1 SphU

`SphU`是Sentinel的核心入口类，提供了各种静态方法来创建资源保护的入口点。

**主要功能**：
- 提供同步资源调用的入口点（`entry`方法）
- 提供异步资源调用的入口点（`asyncEntry`方法）
- 支持不同类型的资源（字符串名称或Method对象）
- 支持入站/出站流量的区分（EntryType.IN/OUT）

**核心方法**：
- `entry(String name)`: 最常用的资源入口方法，使用资源名称进行限流控制
- `asyncEntry(String name)`: 异步调用场景下的资源入口方法
- `entry(String name, EntryType type, int count, Object... args)`: 完整的入口方法，支持指定入站/出站类型、令牌数量和额外参数

### 2.2 SphO

`SphO`是`SphU`的变体，提供了基于布尔值返回的资源调用方式，而不是抛出异常。

**主要功能**：
- 提供同步资源调用的入口点，但使用布尔返回值而非异常
- 适用于不希望使用try-catch处理限流的场景

## 3. 核心接口

### 3.1 Sph

`Sph`是Sentinel核心处理逻辑的抽象接口，定义了创建Entry的各种方法。

**主要功能**：
- 定义了创建资源保护入口点的各种方法
- 是SphU和SphO底层调用的接口

### 3.2 Entry

`Entry`接口代表一次资源调用的记录，持有当前调用的统计信息。

**主要功能**：
- 记录资源调用的创建时间、当前节点、来源节点等信息
- 提供`exit()`方法用于结束资源调用
- 支持自动关闭（实现AutoCloseable接口）

### 3.3 AsyncEntry

`AsyncEntry`是`Entry`的扩展，专门用于异步资源调用场景。

**主要功能**：
- 创建异步上下文，用于在不同线程中关联调用链
- 提供`getAsyncContext()`方法获取异步上下文
- 支持在回调中使用`ContextUtil.runOnContext()`关联异步调用

## 4. 实现类

### 4.1 CtSph

`CtSph`是`Sph`接口的具体实现类，负责处理资源的入口逻辑。

**主要功能**：
- 实现`Sph`接口定义的所有方法
- 管理资源的`ProcessorSlotChain`责任链
- 处理同步和异步资源调用的核心逻辑

**核心方法**：
- `entry(ResourceWrapper resourceWrapper, int count, Object... args)`: 处理资源调用的核心方法
- `asyncEntryWithPriorityInternal(ResourceWrapper resourceWrapper, int count, boolean prioritized, Object... args)`: 处理异步资源调用的核心方法
- `lookProcessChain(ResourceWrapper resourceWrapper)`: 获取或创建资源的处理责任链

### 4.2 CtEntry

`CtEntry`是`Entry`接口的具体实现类，代表一次具体的资源调用。

**主要功能**：
- 实现`Entry`接口定义的方法
- 维护资源调用的上下文和统计信息
- 处理资源调用的退出逻辑

## 5. 责任链模式

### 5.1 ProcessorSlotChain

`ProcessorSlotChain`是Sentinel的核心责任链接口，用于串联各个处理插槽。

**主要功能**：
- 将多个`ProcessorSlot`串联成一个责任链
- 提供`addFirst`和`addLast`方法添加处理插槽

### 5.2 ProcessorSlot

`ProcessorSlot`是责任链中的处理插槽接口，定义了处理资源调用的方法。

**主要功能**：
- 定义`entry`和`exit`方法处理资源的进入和退出
- 定义`fireEntry`和`fireExit`方法传递给下一个插槽

### 5.3 DefaultSlotChainBuilder

`DefaultSlotChainBuilder`是默认的`SlotChainBuilder`实现，负责构建标准的处理插槽链。

**主要功能**：
- 按照预定义的顺序构建处理插槽链
- 默认包含以下插槽：
  - NodeSelectorSlot: 负责收集资源的路径，并构建调用树
  - ClusterBuilderSlot: 负责构建资源的ClusterNode
  - LogSlot: 负责记录日志
  - StatisticSlot: 负责统计实时的调用数据
  - AuthoritySlot: 负责授权规则检查
  - SystemSlot: 负责系统保护规则检查
  - FlowSlot: 负责流量控制规则检查
  - DegradeSlot: 负责熔断降级规则检查

## 6. 资源包装

### 6.1 ResourceWrapper

`ResourceWrapper`是资源包装器的抽象类，封装了资源的名称和类型。

**主要功能**：
- 封装资源的名称、入站/出站类型和资源类型
- 提供`getName`和`getShowName`方法获取资源名称

### 6.2 StringResourceWrapper

`StringResourceWrapper`是基于字符串名称的资源包装器实现。

**主要功能**：
- 使用字符串作为资源的标识
- 适用于大多数常见的资源保护场景

### 6.3 MethodResourceWrapper

`MethodResourceWrapper`是基于Method对象的资源包装器实现。

**主要功能**：
- 使用Java方法对象作为资源的标识
- 适用于方法级别的资源保护场景

## 7. 上下文管理

### 7.1 ContextUtil

`ContextUtil`是Sentinel的上下文工具类，用于创建和管理调用上下文。

**主要功能**：
- 提供`enter`方法创建调用上下文
- 提供`exit`方法退出调用上下文
- 提供`runOnContext`方法在指定上下文中执行代码块
- 管理调用来源（origin）信息

### 7.2 Context

`Context`类表示调用链路的上下文，记录当前调用链路的信息。

**主要功能**：
- 维护当前调用链路的入口节点
- 维护当前的Entry栈
- 记录调用来源信息
- 支持创建异步上下文

## 8. 统计节点

### 8.1 Node

`Node`接口是统计节点的抽象，定义了流量统计的基本方法。

**主要功能**：
- 定义增加成功、异常、阻塞计数的方法
- 定义获取各类统计数据的方法
- 支持滑动窗口统计

### 8.2 StatisticNode

`StatisticNode`是基础统计节点实现，实现了秒级和分钟级的统计。

**主要功能**：
- 实现秒级滑动窗口统计
- 实现分钟级滑动窗口统计
- 记录实时的QPS、响应时间等指标

### 8.3 ClusterNode

`ClusterNode`是集群统计节点，统计同一个资源的所有调用数据。

**主要功能**：
- 统计资源在所有调用链路上的聚合数据
- 维护资源的调用来源（origin）统计

### 8.4 DefaultNode

`DefaultNode`是默认节点实现，代表调用树中的一个节点。

**主要功能**：
- 关联资源的ClusterNode
- 维护当前调用路径的统计数据

## 9. 规则管理

### 9.1 Rule

`Rule`接口是所有规则的基础接口，定义了规则的基本属性。

### 9.2 FlowRule

`FlowRule`是流量控制规则，用于限制资源的QPS或并发线程数。

**主要功能**：
- 支持基于QPS或并发线程数的限流
- 支持直接拒绝、冷启动、匀速排队等控制效果
- 支持基于调用关系的限流策略

### 9.3 DegradeRule

`DegradeRule`是熔断降级规则，用于在资源不稳定时自动熔断。

**主要功能**：
- 支持基于异常比例、异常数和响应时间的熔断策略
- 支持熔断恢复的半开状态

### 9.4 SystemRule

`SystemRule`是系统保护规则，用于保护系统整体稳定性。

**主要功能**：
- 支持基于系统负载、CPU使用率、平均RT、QPS和线程数的保护机制
- 只对入站流量（EntryType.IN）生效

## 10. 异步支持

### 10.1 异步调用模式

Sentinel支持异步调用场景，主要通过以下方式实现：

1. **AsyncEntry**: 异步资源调用的入口点
2. **异步上下文**: 通过`AsyncEntry.getAsyncContext()`获取
3. **上下文传递**: 通过`ContextUtil.runOnContext()`在异步回调中关联上下文

**使用模式**：
```java
try {
    AsyncEntry entry = SphU.asyncEntry(resourceName);
    // 异步调用
    doAsync(param, result -> {
        try {
            // 在异步回调中使用上下文
            ContextUtil.runOnContext(entry.getAsyncContext(), () -> {
                // 处理结果
            });
        } finally {
            // 退出entry
            entry.exit();
        }
    });
} catch (BlockException ex) {
    // 处理限流异常
}
```

## 11. 总结

Sentinel-Core通过责任链模式和资源保护的设计理念，实现了灵活、高性能的流量控制系统。核心组件包括：

1. **入口类**: SphU, SphO
2. **核心接口**: Sph, Entry, AsyncEntry
3. **责任链**: ProcessorSlotChain, ProcessorSlot
4. **资源包装**: ResourceWrapper
5. **上下文管理**: ContextUtil, Context
6. **统计节点**: Node, StatisticNode, ClusterNode
7. **规则管理**: FlowRule, DegradeRule, SystemRule

通过这些组件的协作，Sentinel能够实现实时的流量控制、熔断降级、系统负载保护等功能，保障应用的稳定性和可用性。