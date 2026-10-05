# Ming Music / 明音乐

基于 FuoEvolve 思路与架构进行 Android 优先的多源音乐播放器开发。

## 产品目标
- 多 Provider 聚合搜索，优先返回真正可播放结果
- 同曲去重与候选源自动换源
- Provider 健康度、超时、熔断与成功源缓存
- Android Media3 后台播放与系统媒体控制
- 水墨风 Ming 品牌界面
- 免费/开放资源优先，不绕过付费、VIP、DRM 或访问控制

## Provider 规划
现有/兼容方向：NetEase、QQ Music、Bilibili、YouTube Music。
新增方向：Audius、Internet Archive、Jamendo、SoundCloud、Radio Browser、OpenSubsonic/Navidrome。

> Radio Browser 为电台源；OpenSubsonic/Navidrome 为用户自有音乐库。需要 API 凭据或账号授权的来源必须由用户配置，不在仓库中提交密钥。

## 开发策略
主分支 `main` 保持可发布；功能开发进入 `develop`。优先完成 Android 可编译基线，再逐步接入 Provider 和聚合搜索。

## 上游与许可证
项目二次开发方向参考 FuoEvolve。合并/复用 GPL-3.0 上游代码时必须保留对应版权和 GPL-3.0 义务。第三方 Provider/API 依各自服务条款使用。
