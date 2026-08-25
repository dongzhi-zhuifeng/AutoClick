# AutoClick

Android 定时自动点击应用：到点后按项目里的点位链执行点击。用无障碍服务点屏幕，用精确闹钟准时触发。

适合需要在固定时刻点固定位置的场景（例如定时签到、到点确认），由用户自己配置坐标与时间。

## 功能

- **点击项目**：一个项目 = 开始时刻 + 重复轮数 + 一条点位链
- **选点**：悬浮准星拖到目标位置，按屏幕比例保存，适配不同分辨率
- **到点执行**：精确闹钟触发后，按点与点之间的延迟依次点击；可重复多轮
- **倒计时浮层**：到点前约 30 秒显示倒计时和第一点标记
- **开机恢复**：重启后自动重排已启用项目的闹钟

## 环境

| 项 | 值 |
|---|---|
| minSdk | 25 |
| targetSdk / compileSdk | 36 |
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose Material3 |
| 构建 | Android Gradle Plugin 9.3，JDK 11+ |

## 构建

用 [Android Studio](https://developer.android.com/studio) 打开本目录，或：

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
```

产物：`app/build/outputs/apk/debug/`。`local.properties` 里的 `sdk.dir` 不要提交。

## 使用前需要的权限

应用不能在未授权时点别人的屏幕。首次使用请在系统设置中开启：

1. **无障碍服务** — 真正执行点击
2. **悬浮窗** — 选点和倒计时浮层
3. **精确闹钟** — Android 12+ 准时触发
4. **忽略电池优化** — 降低被系统杀掉的概率
5. **通知**（Android 13+）— 系统通知权限

顶栏菜单「权限与设置」可跳到对应系统页。无障碍未开时，到点只会提示，不会点击。

## 目录与职责

```
app/src/main/java/com/Luofeng/autoclick/
  MainActivity.kt              应用入口，挂上 Compose
  AppInfo.kt                   关于页文案、交流群号
  AppLog.kt                    统一日志
  AppTiming.kt                 计时常量（倒计时、去重窗口等）
  ScreenUtils.kt               真实屏幕尺寸、悬浮窗类型
  DesignTokens.kt              颜色 / 间距 / 圆角 / 字号
  ui/                          全部 Compose 界面
    AppRoot.kt                 主列表状态与保存/调度接线
    ProjectCard.kt             项目卡片、运行中药丸
    StepRow.kt                 点位行与点间箭头
    LiveClock.kt               数字/表盘实时时钟
    Permissions.kt             权限检测与对话框
    AppDialogs.kt              表单、重命名、动作表、关于/加群
    PointPickerLauncher.kt     拉起选点浮层
    UiModels.kt                仅 UI 用的临时状态
    TimeFormat.kt              剩余时间文案
    AnalogClock.kt / WheelPicker.kt
    theme/                     Material3 主题
  data/
    TaskRepository.kt          项目 JSON 存 SharedPreferences
    AppPrefs.kt                时钟模式等界面偏好
  domain/
    ClickProject.kt            ClickProject / ClickStep / ScheduledClick
    ClickSequence.kt           项目 → 点击时间轴
    ProjectListLogic.kt        列表与点位链的纯函数
    ClickDedupGuard.kt         短窗口去重，避免重复触发
  overlay/
    OverlayPointPickerService.kt   悬浮选点
    PointPickerBus.kt              选点结果回传
    CountdownOverlayService.kt     到点前倒计时
  click/
    ClickAccessibilityService.kt   节点点击，失败则手势点击
  schedule/
    TaskScheduler.kt           精确闹钟与预告
    AlarmReceiver.kt           到点执行
    PreAlarmReceiver.kt        启动倒计时
    BootReceiver.kt            开机重排
```

单测在 `app/src/test/java/...` 对应包下：JSON 解析、序列展开、列表逻辑、选点布局、倒计时文案、表盘指针。

## 运行时怎么走

```
用户保存项目 → TaskRepository → TaskScheduler 设 exact alarm
选点 → OverlayPointPickerService → PointPickerBus → AppRoot 改 steps
到点 → AlarmReceiver 读最新项目 → flattenProject → ClickAccessibilityService
开机 → BootReceiver → rescheduleAll
到点前 30s → PreAlarmReceiver → CountdownOverlayService
```

坐标一律存 `xRatio` / `yRatio`（相对整屏），点击时再乘当前真实分辨率。

## 数据

项目列表存在 SharedPreferences：`click_tasks_pref` / `tasks_json`。旧版「单点 + 次数 + 间隔」打开后会迁成「1 个点位 + repeatCount + repeatGapMs」，行为保持兼容。

## 说明

自动点击会操作你授予无障碍权限后的屏幕内容。请只用于自己的设备与自己的操作，并遵守目标应用与平台规则。
