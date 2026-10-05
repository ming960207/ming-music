# Upstream policy

Upstream: feeluown/FuoEvolve

Ming Music 采用“上游基线 + 产品层增量”的维护策略。不要无必要重写 Provider API、播放内核和通用模型。

同步流程：
1. 记录当前 FuoEvolve upstream commit。
2. 在专用 upstream-sync 分支完成同步。
3. 执行 Android 构建、单元测试、Provider 回归。
4. 通过后再合入 develop。
5. develop 稳定后通过 PR 合入 main。

任何复制或修改的 GPL-3.0 上游代码必须保留对应许可证和版权义务。
