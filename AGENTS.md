# AGENTS.md

本仓库是一个 Android 应用：录音时持续记录位置变化，并在回放时结合地图查看轨迹。

## 项目约定

- 技术栈：Kotlin、Jetpack Compose、MVVM、Room、Google Maps SDK / Maps Compose。
- 最低版本：`minSdk 26`。
- 编码：所有文本文件使用 UTF-8。
- Git：每次完成一组文件修改后必须提交 commit；提交前先检查 `git status`，不要回退用户未要求回退的改动。
- 如果需求不明确，先向用户确认，再修改代码。

## 架构边界

- UI 层只处理页面状态、权限入口、用户操作和导航。
- ViewModel 只编排用例和暴露 UI 状态，不直接访问 Android framework API。
- 录音、定位、后台持续运行放在 `TrackingForegroundService` 及其协作类中。
- 数据持久化通过 Room，录音会话和位置点分表存储。
- 地图展示通过 Maps Compose；不要手写地图渲染或坐标投影逻辑。

## 后台录音和定位

- 后台持续录音/定位必须使用 Foreground Service。
- Manifest 必须声明录音、前台服务、定位、后台定位和通知相关权限。
- Android 10+ 的后台定位权限需要单独引导用户授权；不要在未授权时静默启动后台采样。
- Android 13+ 启动前台服务通知前需要处理 `POST_NOTIFICATIONS`。
- 服务启动后必须尽快调用 `startForeground()`。

## Google Maps 配置

- API key 通过 Gradle property `MAPS_API_KEY` 注入，不要提交真实 key。
- 本地可在 `local.properties` 中配置：

```properties
MAPS_API_KEY=your_key_here
```

## 录音文件

- 录音文件保存在 app 私有外部目录 `getExternalFilesDir(Environment.DIRECTORY_MUSIC)`。
- 数据库记录文件绝对路径和会话元数据。
- 删除会话时应同时删除音频文件和关联位置点。

## 测试建议

- 数据层优先写 DAO / repository 测试。
- 前台服务和权限流程优先用集成测试或手动测试清单覆盖。
- 地图回放至少验证：无轨迹、单点轨迹、多点轨迹、播放进度同步轨迹点。

