# Ming Music Roadmap

## M0 — Upstream bootstrap
- 引入 FuoEvolve 基线并记录 upstream commit
- Android Debug 构建通过
- 建立 develop 分支和 CI

## M1 — Branding
- 应用名 Ming Music / 明音乐
- applicationId 迁移为 com.ming.music
- 水墨图标、启动页、主题、About
- 保留 GPL-3.0 与第三方声明

## M2 — Provider expansion
- Audius
- Internet Archive
- Jamendo
- SoundCloud
- Radio Browser
- OpenSubsonic/Navidrome

## M3 — Aggregated search
- 并发搜索、首批快速返回
- Normalize / 去重 / 来源合并
- Provider 超时与增量结果

## M4 — Smart failover 2.0
- ProviderHealthStore
- 成功率/延迟评分
- 连续失败熔断
- CandidateSource 自动换源
- 成功源短期缓存

## M5 — Product UI
- 首页、搜索、播放器、音乐库、电台、音乐源管理
- 水墨品牌视觉
- Android 后台播放/通知/锁屏

## M5.5 — Floating Lyrics / 悬浮歌词
- 计划开发 Android 悬浮歌词窗口，可在其他应用上层持续显示当前播放歌词
- 支持单行/双行歌词、当前句高亮、逐行滚动与播放进度同步
- 支持拖动位置、锁定位置、透明度、字号与紧凑模式
- 悬浮框提供最小化播放控制：播放/暂停、上一首、下一首
- 悬浮歌词关闭时不影响后台播放
- 使用 Android 官方悬浮窗权限（SYSTEM_ALERT_WINDOW）并提供明确开关与授权引导
- 未授权时自动退化为应用内歌词/系统媒体通知，不阻塞播放
- 由 Media3 播放状态作为唯一时间基准，避免歌词窗口与播放器各自维护播放状态
- 服务生命周期与现有后台播放服务协调，避免重复常驻 Service
- 后续评估桌面歌词样式、横竖屏/折叠屏适配及 OEM 后台限制兼容

## M6 — Release
- 100 条搜索回归集
- 50 条真实播放回归集
- Debug/Release CI
- APK、变更记录、许可证、SHA-256
