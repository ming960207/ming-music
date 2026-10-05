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

## M6 — Release
- 100 条搜索回归集
- 50 条真实播放回归集
- Debug/Release CI
- APK、变更记录、许可证、SHA-256
