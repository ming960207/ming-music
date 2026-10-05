# Ming Music 架构

## 原则
1. Android 优先，播放器内核采用 Media3。
2. Provider 与 UI 解耦；任何单一在线源故障不得导致应用崩溃或阻塞聚合搜索。
3. 搜索“可播放优先”：先返回健康 Provider 的结果，慢源增量补齐。
4. 同一作品聚合为 UnifiedTrack，保存多个 CandidateSource。
5. 不绕过付费、VIP、DRM、地区限制或其他访问控制。

## Provider 分层
默认在线搜索：NetEase、QQ Music、Bilibili、YouTube Music、Audius、Internet Archive、Jamendo、SoundCloud。
特殊 Provider：Radio Browser（电台）、OpenSubsonic/Navidrome（用户自有库）。

## 搜索链路
Query -> AggregatedSearchCoordinator -> Provider 并发搜索 -> Normalize -> Deduplicate -> Availability/Health -> Rank -> UI

## 播放链路
UnifiedTrack -> CandidateSource 排序 -> Resolve -> Media3 -> 成功记录健康度；失败自动尝试下一候选。

## 健康策略
记录成功率、解析耗时、最近错误和连续失败。连续失败触发临时熔断；熔断 Provider 不参与自动解析，但允许手动测试。

## 上游
FuoEvolve 为二次开发基线。优先通过新增模块和小范围适配完成 Ming Music，降低未来同步上游的冲突。
