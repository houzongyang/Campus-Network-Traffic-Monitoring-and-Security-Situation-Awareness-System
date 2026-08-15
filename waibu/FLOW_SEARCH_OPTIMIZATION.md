# Flow Search 大数据优化说明

## 目标

让 `Flow Search` 在百万级到千万级流量数据下，仍然能在系统启动后稳定显示并可检索。

## 原问题

旧实现把时间窗口内的流量先读入 Java，再做：

- IP / CIDR 过滤
- 端口过滤
- 协议过滤
- 排序
- 分页

在 `280+` 万条记录下，这会导致：

- 查询超时
- JVM 内存压力过大
- 页面首屏卡顿

## 本次方案

### 1. 时间窗口锚定到最新导入数据

新增 `LatestDataTimeService`，让默认检索窗口围绕数据库最新流量时间计算，而不是围绕“现在”。

这样即使导入的是历史 `pcap`，重启后页面也不会空白。

### 2. Flow Search 改为数据库原生动态检索

新增 `FlowSearchJdbcRepository`，使用 `NamedParameterJdbcTemplate` 构建 SQL，把复杂过滤全部下推给 PostgreSQL：

- 时间范围过滤
- `*` 通配的 IP 文本匹配
- `CIDR` 网络段匹配
- 源 / 目的端口过滤
- 目的端口范围过滤
- 协议 / 应用协议过滤
- `ORDER BY timestamp DESC, id DESC`
- `LIMIT / OFFSET` 分页

### 3. 用 PostgreSQL 的网络类型能力做 CIDR 匹配

核心过滤不再走 Java 手写逐条判断，而是直接使用：

`src_ip::inet <<= CAST(:srcCidr AS cidr)`

这属于数据库原生网络检索能力，性能和准确性都更适合大数据量场景。

### 4. 默认页大小降低

前端 `Flow Search` 默认每页由 `100` 调整到 `50`，减少首屏传输和渲染压力。

## 创新点

### Hybrid Search Engine

这次将搜索链路升级成“混合检索架构”：

- 仪表盘：数据库聚合
- 流量明细：数据库原生检索
- 时间窗口：最新导入数据锚定

应用层不再承担海量流记录的逐条筛选职责。

### Network-aware Query

CIDR 检索不再是通用字符串处理，而是基于 PostgreSQL `inet/cidr` 的网络语义检索。

这比普通字符串前缀匹配更接近真正的网络分析系统。

## 当前效果

已完成：

- 首页总览稳定显示导入数据
- 首页指标稳定显示导入数据
- `Flow Search` 改为数据库原生筛选路径
- 默认页大小优化为 `50`

## 后续可继续升级

### Keyset Pagination

如果后续要支持非常深的翻页，建议把 `OFFSET` 翻页升级成基于 `(timestamp, id)` 的游标翻页。

### 表达式索引

可进一步增加：

- `src_ip::inet`
- `dst_ip::inet`
- `(timestamp DESC, id DESC)`

等表达式索引，继续提升检索性能。
