# Development

## Branches
- main: 可发布版本
- develop: 日常集成
- feature/*: 独立功能

## Android baseline
FuoEvolve 上游当前 Android 构建入口为：

```bash
./gradlew :androidApp:assembleDebug
```

Ming Music 引入上游后必须首先保持该链路可构建，再做包名和品牌迁移。

## Definition of Done
Provider 功能只有在真实设备完成“搜索 -> resolve -> Media3 播放”后才算完成。禁止仅用 mock 或仅验证 HTTP 200 宣布接入完成。

## Secrets
所有第三方 Client ID / OAuth / 用户凭据通过本地配置、CI Secret 或安全存储注入，禁止提交到 Git。
