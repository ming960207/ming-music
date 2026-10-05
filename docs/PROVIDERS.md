# Provider 计划

| Provider | 类型 | 默认状态 | 作用 |
|---|---|---:|---|
| NetEase | 在线音乐 | 开启 | 华语主源/元数据 |
| QQ Music | 在线音乐 | 开启 | 华语主源/元数据 |
| Bilibili | 视频/音频 | 开启 | 中文长尾、OST、Live |
| YouTube Music | 在线音乐 | 可选 | 全球补充 |
| Audius | 开放音乐 | 开启 | 全球独立音乐 |
| Internet Archive | 开放档案 | 开启 | 公版、Live、历史录音 |
| Jamendo | 开放/授权音乐 | 配置后开启 | 独立音乐/BGM |
| SoundCloud | 在线音乐 | 授权后开启 | Remix/电子/独立 |
| Radio Browser | 网络电台 | 开启 | 独立电台页 |
| OpenSubsonic/Navidrome | 私人曲库 | 配置后开启 | 用户自有资源 |

## Provider 验收
“能搜到”不算完成。每个 Provider 至少验证：搜索、详情映射、合法可播放 URL、Media3 首播、错误处理、超时、分页/增量（如支持）。

## 凭据
API key、OAuth token、Cookie、用户名密码不得硬编码或提交仓库。需要凭据的 Provider 使用安全存储并由用户自行配置。
