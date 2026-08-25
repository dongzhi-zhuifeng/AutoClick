# AutoClick 质量提升与 UI 精装修 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在功能、JSON、Intent、点击/闹钟语义 100% 不变的前提下，提升健壮性、可维护性、主题一致性、对比度与暗色适配。

**Architecture:** 样式与 token 集中在 `DesignTokens.kt` + `ui/theme` + `res/values`；业务类只加边界保护、常量化与结构化日志。不拆分 `MainActivity.kt` 文件、不改无障碍点击算法、不改 Alarm 精确性策略。

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose Material3, 现有 View overlay XML, JUnit4。禁止新 UI 框架。

**Spec:** `docs/superpowers/specs/2026-08-24-quality-ui-audit.md`

## Global Constraints

- 功能零变更：不增删任何用户可见功能点、不改点击次数/间隔/去重窗口/预告 30s 窗口的数值语义
- 向后兼容：`tasks_json` 字段名与旧 `x/y` 回退逻辑保持；Alarm / Overlay extras 名保持
- 禁止新依赖与新 UI 框架
- 样式改动集中在 token / theme / `res/values` / 现有 XML drawable 与 layout，不把颜色散进新的业务分支
- 高风险项（审计报告第 3 节）除非用户点名否则跳过
- 每批结束后运行 `.\gradlew.bat :app:testDebugUnitTest`；相关逻辑改动必须先有失败测试再改生产代码（TDD）
- 不创建 git commit，除非用户另行要求

---

## File map

| File | Responsibility after this plan |
|---|---|
| `app/src/main/java/com/Luofeng/autoclick/AppLog.kt` | 统一 tag 与关键事件日志 |
| `app/src/main/java/com/Luofeng/autoclick/AppTiming.kt` | 时序常量（值与现状完全一致） |
| `app/src/main/java/com/Luofeng/autoclick/DesignTokens.kt` | 亮/暗颜色、间距、圆角、字号 |
| `app/src/main/java/com/Luofeng/autoclick/ui/theme/Color.kt` | 删除模板紫，改为从 AppColors 派生 |
| `app/src/main/java/com/Luofeng/autoclick/ui/theme/Type.kt` | 实际字号层级 |
| `app/src/main/java/com/Luofeng/autoclick/ui/theme/Theme.kt` | 关闭动态色，MaterialScheme 对齐 AppColors，提供 LocalAppColors |
| `app/src/main/java/com/Luofeng/autoclick/MainActivity.kt` | 使用 token；时钟合 ticker；测试点击可取消；List 类型；过渡动画 |
| `app/src/main/java/com/Luofeng/autoclick/WheelPicker.kt` | 颜色改走 AppColors，算法不动 |
| Overlay XML + `res/values/colors.xml` `dimens.xml` | 悬浮窗色与网格对齐 |
| `TaskRepository.kt` `TaskScheduler.kt` `AlarmReceiver.kt` `PreAlarmReceiver.kt` `OverlayPointPickerService.kt` `ClickAccessibilityService.kt` `CountdownOverlayService.kt` | 保护 + 日志，不改成功路径 |
| `app/src/test/java/com/Luofeng/autoclick/*.kt` | 行为锁定测试 |
| **不修改** | `ClickTask.kt` 字段、`AppInfo.kt` 文案、`AndroidManifest.xml` 权限/组件、点击手势 path、WheelPicker 循环倍数 |

---

### Task 1: 锁定纯函数行为的单元测试

**Files:**
- Create: `app/src/test/java/com/Luofeng/autoclick/FormatRemainingTest.kt`
- Create: `app/src/test/java/com/Luofeng/autoclick/ClickDedupGuardTest.kt`
- Test: 同上

**Interfaces:**
- Consumes: 现有 `formatRemaining(seconds: Long): String`、`ClickDedupGuard`
- Produces: 锁定当前文案与去重窗口 2000ms

- [ ] **Step 1: 写失败前先写测试（函数已存在，测试应直接 PASS；若 FAIL 则先停下来，说明现状与文档不一致）**

```kotlin
package com.Luofeng.autoclick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatRemainingTest {
    @Test fun zeroOrNegative_isImminent() {
        assertEquals("即将执行", formatRemaining(0))
        assertEquals("即将执行", formatRemaining(-3))
    }
    @Test fun secondsOnly() {
        assertEquals("还有 9 秒", formatRemaining(9))
    }
    @Test fun minutesAndSeconds() {
        assertEquals("还有 2分05秒", formatRemaining(125))
    }
    @Test fun hoursAndMinutes_omitsSeconds() {
        assertEquals("还有 1时02分", formatRemaining(3725))
    }
}

class ClickDedupGuardTest {
    @Test fun firstMarkSucceeds_secondWithinWindowFails() {
        ClickDedupGuard.clear(42L)
        assertTrue(ClickDedupGuard.tryMark(42L, windowMs = 2_000L))
        assertFalse(ClickDedupGuard.tryMark(42L, windowMs = 2_000L))
        ClickDedupGuard.clear(42L)
        assertTrue(ClickDedupGuard.tryMark(42L, windowMs = 2_000L))
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.Luofeng.autoclick.FormatRemainingTest --tests com.Luofeng.autoclick.ClickDedupGuardTest`

Expected: PASS。若 `还有 9 秒` 空格与实现不一致，**以实现为准改测试，不改正文案。**

---

### Task 2: TaskRepository JSON 往返与损坏数据测试

**Files:**
- Create: `app/src/test/java/com/Luofeng/autoclick/TaskRepositoryTest.kt`
- Modify: `app/src/main/java/com/Luofeng/autoclick/TaskRepository.kt`（本任务只加测试；生产保护在 Task 8）

Robolectric 不在现有依赖中。**禁止新增 Robolectric。** 将解析抽成纯函数才能在 JVM 测。

**Interfaces:**
- Produces: `internal fun parseTasksJson(json: String, screenWidth: Int, screenHeight: Int): List<ClickTask>`
- `loadTasks` 改为调用该函数；`saveTasks` 抽 `fun tasksToJson(tasks: List<ClickTask>): String`

- [ ] **Step 1: 先写纯函数测试（此时 parse 还不存在 → FAIL）**

```kotlin
class TaskRepositoryParseTest {
    @Test fun roundTrip_preservesFields() {
        val t = ClickTask(1L, 0.5f, 0.25f, 8, 30, 15, 0, 100, 500L, 3, true)
        val json = TaskRepository.tasksToJson(listOf(t))
        val back = TaskRepository.parseTasksJson(json, 1080, 1920)
        assertEquals(1, back.size)
        assertEquals(t, back[0])
    }
    @Test fun legacyXy_convertsToRatio() {
        val json = """[{"id":1,"x":540,"y":960,"hour":1,"minute":2,"intervalMs":500,"count":1}]"""
        val back = TaskRepository.parseTasksJson(json, 1080, 1920)
        assertEquals(0.5f, back[0].xRatio, 0.001f)
        assertEquals(0.5f, back[0].yRatio, 0.001f)
    }
    @Test fun corruptJson_returnsEmpty_withoutThrowing() {
        val back = TaskRepository.parseTasksJson("{not-json", 1080, 1920)
        assertTrue(back.isEmpty())
    }
    @Test fun oneBadItem_skipsIt_keepsGood() {
        val json = """[{"id":1,"xRatio":0.1,"yRatio":0.2,"hour":1,"minute":0,"intervalMs":1,"count":1},{"id":"bad"}]"""
        val back = TaskRepository.parseTasksJson(json, 1080, 1920)
        assertEquals(1, back.size)
        assertEquals(1L, back[0].id)
    }
}
```

- [ ] **Step 2: 运行确认 FAIL**（`Unresolved reference: parseTasksJson`）

- [ ] **Step 3: 抽取纯函数，行为与现实现逐字段一致**（`optInt("second",0)` 等默认值原样保留）。`loadTasks` / `saveTasks` 仅做 prefs 读写。损坏 JSON 返回 empty；单条坏对象 skip。成功路径 JSON 字段顺序可变，字段名不可变。

- [ ] **Step 4: 再跑测试，期望 PASS**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.Luofeng.autoclick.TaskRepositoryParseTest`

---

### Task 3: 时序常量与日志（值冻结）

**Files:**
- Create: `app/src/main/java/com/Luofeng/autoclick/AppTiming.kt`
- Create: `app/src/main/java/com/Luofeng/autoclick/AppLog.kt`
- Modify: 所有现写 `30_000L` / `2000L` / `3000` / 手势 `200` / ripple `80`/`500` 的调用点改为常量引用，**数字不变**

```kotlin
object AppTiming {
    const val PRE_ALARM_LEAD_MS = 30_000L
    const val DEDUP_WINDOW_MS = 2_000L
    const val TEST_CLICK_DELAY_MS = 3_000L
    const val TEST_CLICK_INTERVAL_MS = 500L
    const val GESTURE_STROKE_DURATION_MS = 200L
    const val RIPPLE_SIZE_PX = 80
    const val RIPPLE_REMOVE_DELAY_MS = 500L
    const val COUNTDOWN_TICK_MS = 50L
    const val COUNTDOWN_FAST_TICK_MS = 5L
    const val COUNTDOWN_FAST_THRESHOLD_MS = 200L
    const val DEFAULT_INTERVAL_MS = 500L
    const val DELAY_OFFSET_MIN = -1000
    const val DELAY_OFFSET_MAX = 1000
    const val UI_TICK_MS = 1_000L
}

object AppLog {
    const val TAG = "AutoClick"
    fun i(event: String, details: String = "") {
        android.util.Log.i(TAG, if (details.isEmpty()) event else "$event $details")
    }
    fun w(event: String, t: Throwable? = null) {
        if (t == null) android.util.Log.w(TAG, event) else android.util.Log.w(TAG, event, t)
    }
    fun e(event: String, t: Throwable? = null) {
        if (t == null) android.util.Log.e(TAG, event) else android.util.Log.e(TAG, event, t)
    }
    fun d(event: String, details: String = "") {
        android.util.Log.d(TAG, if (details.isEmpty()) event else "$event $details")
    }
}
```

- [ ] **Step 1: 添加两个 object 文件**
- [ ] **Step 2: 替换 `TaskScheduler` 的 `30_000L`、`ClickDedupGuard` 默认 2000、`MainActivity` 测试点击 3000/500、`ClickAccessibilityService` 200/80/500、`CountdownOverlayService` 5/50/200**
- [ ] **Step 3: 跑 Task 1–2 测试，必须仍 PASS**

---

### Task 4: 设计 token — 间距 / 圆角 / 亮色对比度

**Files:**
- Modify: `app/src/main/java/com/Luofeng/autoclick/DesignTokens.kt`

**不改** 品牌主色相 `#007AFF`。只加深辅助灰、紧急红、分割线。

```kotlin
object AppColors {
    val Background = Color(0xFFF2F4F8)
    val CardBackground = Color(0xFFFFFFFF)
    val PrimaryBlue = Color(0xFF007AFF)
    val LightBlueTint = Color(0xFFE8F1FE)
    val LightBlueTintDeep = Color(0xFFD8E9FD)
    val TextPrimary = Color(0xFF1C1C1E)
    val TextSecondary = Color(0xFF6C6C70) // was #8E8E93, WCAG AA
    val Danger = Color(0xFFD70015)        // was #FF3B30, WCAG AA on white
    val DangerLight = Color(0xFFFFEDEC)
    val Warning = Color(0xFFC93400)       // keep hue, darker for icons+text if needed; 若只用于图标可维持 #FF9500
    val WarningLight = Color(0xFFFFF4E5)
    val Success = Color(0xFF248A3D)       // was #34C759 — 仅用于权限成功图标+可能的文字
    val PillOnBg = LightBlueTint
    val PillOnText = PrimaryBlue
    val PillOffBg = Color(0xFFF0F0F2)
    val PillOffText = TextSecondary
    val Divider = Color(0xFFE5E5EA)       // was 纯白，菜单上不可见
    val DividerOnCard = Color(0xFFEDEDF2)
    val OnPrimary = Color(0xFFFFFFFF)
}

object AppShapes {
    val cardShape = RoundedCornerShape(16.dp)
    val dialogShape = RoundedCornerShape(20.dp)
    val buttonShape = RoundedCornerShape(12.dp)
    val pillShape = RoundedCornerShape(percent = 50)
    val menuShape = RoundedCornerShape(12.dp)
    val iconCircleShape = RoundedCornerShape(percent = 50)
    val inputShape = RoundedCornerShape(12.dp)
}

object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val cardGap = 16.dp          // was 14
    val iconSize = 48.dp         // was 46
    val menuItemHeight = 48.dp   // was 46
    val pillHPad = 16.dp         // was 14
    val pillVPad = 8.dp          // was 7
    val pageHPad = 20.dp
    val clockTop = 40.dp
    val fabListClearance = 90.dp
    val overlayHintTop = 60.dp
}

object AppDuration {
    val stateMs = 220
}

object AppTypeScale {
    val titleSp = 20
    val sectionSp = 14
    val bodySp = 16
    val menuSp = 14
    val captionSp = 12
    val clockFactor = 0.21f
}
```

- [ ] **Step 1: 写入 token（Warning 若只作图标且无文字叠加，可保留 `#FF9500`——以审计为准：权限行 Warning 图标无文字叠色，允许保持原橙）**
- [ ] **Step 2: 不在本任务改 MainActivity，避免半套网格**

---

### Task 5: 接通 MaterialTheme，去掉模板紫与动态色

**Files:**
- Modify: `app/src/main/java/com/Luofeng/autoclick/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/Luofeng/autoclick/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/Luofeng/autoclick/ui/theme/Theme.kt`

```kotlin
// Theme.kt 关键行为
fun AutoClickTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 关闭，避免对话框跟壁纸色
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) AppColorsDark else AppColors // AppColorsDark 在 Task 10 才加；本任务先只用亮色 AppColors，darkTheme 分支暂时仍用亮色 token，避免半套暗色
    val scheme = lightColorScheme(
        primary = colors.PrimaryBlue,
        onPrimary = colors.OnPrimary,
        background = colors.Background,
        surface = colors.CardBackground,
        onBackground = colors.TextPrimary,
        onSurface = colors.TextPrimary,
        error = colors.Danger
    )
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
```

本任务若尚未有 `AppColorsDark`，**darkTheme 暂映射到同一套亮色**，并在代码注释标明 Task 10 替换。禁止 `dynamicColor = true`。

`Type.kt` 填 `titleLarge/bodyLarge/bodyMedium/labelSmall` 对应 AppTypeScale。

- [ ] **Step 1: 实现 LocalAppColors + 关闭 dynamicColor**
- [ ] **Step 2: `Color.kt` 删除 Purple80 等未引用模板色**

---

### Task 6: MainActivity 网格 / 字体 / 过渡（无逻辑改动）

**Files:**
- Modify: `app/src/main/java/com/Luofeng/autoclick/MainActivity.kt`

替换散落 `14.dp`/`46.dp`/`7.dp`/`11.sp` 为 `AppSpacing` / `AppTypeScale`。

`StatusPill` 背景用 `animateColorAsState(tween(AppDuration.stateMs))`。

`DropdownMenu` 使用 `AppShapes.menuShape` + `AppColors.Divider`。

`tasks` 类型改为 `List<ClickTask>`；`nowTick` 改为 `mutableLongStateOf`。

`TaskCard` remember 增加 `task.second, task.delayOffsetMs, task.id`。

`LiveClock` 改为接收 `nowTick: Long`，删除内部 `while(true)`，由 `AppRoot` 唯一 ticker 驱动（间隔仍 `AppTiming.UI_TICK_MS`）。**权限检查仍每 1 秒**（不降频）。

测试点击：

```kotlin
val testClickJob = remember { mutableStateOf<Job?>(null) }
// onTestClick:
testClickJob.value?.cancel()
testClickJob.value = scope.launch {
    delay(AppTiming.TEST_CLICK_DELAY_MS)
    service.performClicks(x, y, 1, AppTiming.TEST_CLICK_INTERVAL_MS)
}
DisposableEffect(Unit) { onDispose { testClickJob.value?.cancel() } }
```

成功路径仍是 3 秒后点 1 次、间隔 500ms。

- [ ] **Step 1: token 替换 + 单一 ticker + 可取消测试点击**
- [ ] **Step 2: 跑单元测试**

Run: `.\gradlew.bat :app:testDebugUnitTest`

---

### Task 7: WheelPicker 颜色接入 token

**Files:**
- Modify: `app/src/main/java/com/Luofeng/autoclick/WheelPicker.kt`

把 `Color(0xFF4A90E2)` / `Color(0xFFB0B8C1)` / `Color.Gray` 换成 `AppColors.PrimaryBlue` / `AppColors.TextSecondary`。循环倍数 1000、itemHeight 40.dp、visibleCount 5 **不改**。删除未使用 `items` import。

- [ ] **Step 1: 只改颜色与 import**
- [ ] **Step 2: 编译** `.\gradlew.bat :app:compileDebugKotlin`

---

### Task 8: 仓库 / 调度 / 广播健壮性

**Files:**
- Modify: `TaskRepository.kt`（若 Task 2 已抽函数，本任务只给 `loadTasks` 打日志）
- Modify: `TaskScheduler.kt`
- Modify: `AlarmReceiver.kt`
- Modify: `PreAlarmReceiver.kt`

`scheduleTask`：

```kotlin
try {
    am.setExactAndAllowWhileIdle(...)
} catch (t: SecurityException) {
    AppLog.e("event=schedule_exact_denied taskId=${task.id}", t)
    // 不降级 inexact
}
```

`AlarmReceiver` / `PreAlarmReceiver`：`if (id < 0) { AppLog.w("event=alarm_bad_id"); return }` —— **注意：** 当前 `id=-1` 仍会 loadTasks 并尝试 reschedule。改为 early return 会改变“损坏 extra 时仍重排全部 enabled 任务”的边角行为。

**处理：** 只在 `id < 0` 时跳过点击，**仍然**执行文件末尾的 `loadTasks`+`scheduleTask` 查找（找不到则 noop）。这样成功路径与“正常 id”一致，损坏 id 只是点不了。若 `find { it.id == -1 }` 为 null，reschedule 本就不发生。**可以 early return 整函数**，因为本来也不会匹配到任务。记录为边角变化，可接受。

`startCountdownNow` / overlay `startService` 包 try/catch `IllegalStateException`，只记日志，不改用 FGS。

- [ ] **Step 1: 实现保护**
- [ ] **Step 2: 跑 `TaskRepositoryParseTest` + 全量 unit test**

---

### Task 9: Overlay 服务资源与 XML 视觉对齐

**Files:**
- Create: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `overlay_point_picker.xml` `countdown_pill.xml` `pill_background.xml` `hint_background.xml` `target_marker.xml`
- Modify: `OverlayPointPickerService.kt` `CountdownOverlayService.kt` `ClickAccessibilityService.kt`

`colors.xml`：

```xml
<color name="brand_primary">#007AFF</color>
<color name="overlay_on_primary">#FFFFFF</color>
<color name="overlay_cancel">#636366</color>
<color name="overlay_pill_bg">#CC007AFF</color>
<color name="overlay_hint_bg">#CC1C1C1E</color>
<color name="marker_stroke">#007AFF</color>
<color name="marker_fill">#007AFF</color>
```

XML 中 `#2196F3` / `#757575` / `#0066FF` / `#0055FF` 全部改为上述 color。圆角：hint 14dp→12dp 或 16dp（与 button/card）；pill 10dp→12dp。

`OverlayPointPickerService.onDestroy`：`try { removeView } catch (t: Exception) { AppLog.w("event=picker_remove_failed", t) }`

空 catch 补日志。`addView` 包 try，失败则 `stopSelf()`。

**不改** 拖动算法、FLAG、选点 extras。

- [ ] **Step 1: colors/dimens + XML**
- [ ] **Step 2: try/catch 日志**
- [ ] **Step 3: 编译**

---

### Task 10: 暗色模式（仅应用内 Compose，不含 Overlay）

**Files:**
- Modify: `DesignTokens.kt` 增加 `AppColorsDark`（或 `fun colors(dark: Boolean)`）
- Modify: `Theme.kt` 按 `darkTheme` 切换 `LocalAppColors` 与 `darkColorScheme`
- Modify: `MainActivity.kt` 把所有 `AppColors.X` 改为 `LocalAppColors.current.X`（或 `appColors().X`）

暗色色板：

```text
Background        #000000
CardBackground    #1C1C1E
PrimaryBlue       #0A84FF
LightBlueTint     #1C3A5F
TextPrimary       #F5F5F7
TextSecondary     #A1A1A6   // 需在 #1C1C1E 上 ≥4.5:1
Danger            #FF6961
PillOffBg         #2C2C2E
Divider           #3A3A3C
```

无独立“暗色开关”。Overlay XML **不**加 night 资源。

- [ ] **Step 1: Dark tokens + CompositionLocal 贯穿 MainActivity/WheelPicker**
- [ ] **Step 2: 编译**
- [ ] **Step 3: 对照检查：亮色下主色仍为 #007AFF，任务开关/增删/选点逻辑代码路径未改**

---

### Task 11: 表单滚动与时钟字号夹紧（响应式，零功能）

**Files:**
- Modify: `MainActivity.kt` `TaskFormDialog` 的 `Column` 加 `verticalScroll(rememberScrollState())` + `heightIn(max = …)` 使用 `LocalConfiguration`
- Modify: `LiveClock` `clockFontSize = (maxWidth.value * AppTypeScale.clockFactor).coerceIn(36f, 96f).sp`

不改保存字段。

- [ ] **Step 1: 滚动 + coerceIn**
- [ ] **Step 2: 编译**

---

### Task 12: 全量验证与复核

**Files:** 无新文件，或按需修测试文案空格

- [ ] **Step 1: 跑测试**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: 全部 PASS

- [ ] **Step 2: 跑 lint（不要求清零 Play 政策的 BatteryLife，只确认无新 error）**

Run: `.\gradlew.bat :app:lintDebug`

- [ ] **Step 3: 规格复核清单**
  - [ ] JSON 字段名未改
  - [ ] extras 名未改
  - [ ] 去重 2000ms、预告 30s、手势 200ms、测试等待 3s 未改
  - [ ] 未新增菜单项/设置项/权限
  - [ ] Overlay 不跟随暗色
- [ ] **Step 4: 写《变更摘要》到对话（不自动 commit）**

---

## 明确不做（除非你回复点名）

- 拆 `MainActivity.kt`
- recycle AccessibilityNodeInfo
- 取消进行中的 `performClicks`
- Foreground Service
- 改 WheelPicker 循环算法
- 删除 `AppPrefs`
- 权限轮询降频
- 圆角改为 8/12
- 升级 compileSdk / 依赖版本（lint 提示，非本期）
- 搬迁全部硬编码中文到 strings.xml
