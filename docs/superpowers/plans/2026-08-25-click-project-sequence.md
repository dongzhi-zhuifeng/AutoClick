# 点击项目（多点位序列）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把单点 `ClickTask` 升级为可展开的点击项目：项目级开始时刻与重复轮数，链上每点一个坐标，第二点起必填相对延迟；收缩卡片接近现状，并简化选点浮层。

**Architecture:** 纯函数 `flattenProject` 把项目展开成 `ScheduledClick` 列表（单元测试锁行为）。`ClickProject`/`ClickStep` 替换 `ClickTask`；仓库负责 JSON 迁移；闹钟只传项目 id；无障碍按序列 `postDelayed`。UI 用可展开 `ProjectCard` + 长按动作表；选点 overlay 只留准星、坐标、「确定位置」。

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose Material3, 现有 WindowManager overlay XML, JUnit4。禁止新依赖。

**Spec:** `docs/superpowers/specs/2026-08-25-click-project-sequence-design.md`

## Global Constraints

- 旧 `tasks_json` 无 `steps` 的对象必须迁成「1 点 + repeatCount=原 count + repeatGapMs=原 intervalMs」，行为对齐旧单点连点
- 坏 JSON → 空列表且不自动 save；坏 item 跳过
- 闹钟 extras 只保留 `id`（及预告的 `targetTime`）；触发时从仓库读项目
- Exact alarm `SecurityException`、后台 `startService` 失败：只打日志，不降级、不上 FGS
- 选点双窗口 + `NOT_TOUCH_MODAL` 布局策略保持；删除提示与取消键
- 样式走 `DesignTokens` / `LocalAppColors` / `AppDuration.stateMs`
- TDD：新纯函数与解析逻辑先写失败测试再写实现
- Gradle：`$env:JAVA_HOME = "D:\Android_Studio\jbr"; .\gradlew <task> --no-configuration-cache`
- 不创建 git commit，除非用户另行要求
- 不改无障碍手势 path、精确闹钟策略、加入群聊、时钟画风

---

## File map

| File | Responsibility |
|---|---|
| Create `ClickProject.kt` | `ClickStep`, `ClickProject` 数据类 |
| Create `ClickSequence.kt` | `ScheduledClick`, `flattenProject` |
| Modify `ClickTask.kt` | **删除**（全部引用改到 `ClickProject`） |
| Modify `TaskRepository.kt` | 读写项目；兼容旧 JSON |
| Modify `TaskScheduler.kt` `AlarmReceiver.kt` `PreAlarmReceiver.kt` `CountdownOverlayService.kt` | 按项目 id 调度；到点跑序列 |
| Modify `ClickAccessibilityService.kt` | `performSequence` |
| Modify `PointPickerBus.kt` `OverlayPointPickerService.kt` `overlay_point_picker.xml` | 选点模式 + 简化 UI |
| Modify `MainActivity.kt` | 可展开卡片、长按菜单、加点延迟、项目表单 |
| Test `ClickSequenceTest.kt` `TaskRepositoryParseTest.kt` | 锁 flatten 与迁移 |

---

### Task 1: flattenProject 纯函数

**Files:**
- Create: `app/src/test/java/com/Luofeng/autoclick/ClickSequenceTest.kt`
- Create: `app/src/main/java/com/Luofeng/autoclick/ClickProject.kt`
- Create: `app/src/main/java/com/Luofeng/autoclick/ClickSequence.kt`

**Interfaces:**
- Produces:

```kotlin
data class ClickStep(
    val id: Long,
    val xRatio: Float,
    val yRatio: Float,
    val delayFromPrevMs: Long
)

data class ClickProject(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val second: Int = 0,
    val millisecond: Int = 0,
    val delayOffsetMs: Int = 0,
    val repeatCount: Int,
    val repeatGapMs: Long,
    val enabled: Boolean = true,
    val steps: List<ClickStep>
)

data class ScheduledClick(
    val xRatio: Float,
    val yRatio: Float,
    val delayAfterPrevMs: Long
)

fun flattenProject(project: ClickProject): List<ScheduledClick>
```

规则：`repeatCount < 1` 按 1；`steps` 空 → 空列表；第一轮第一点用该 step 的 `delayFromPrevMs`；同一轮后续点用各自 `delayFromPrevMs`；下一轮第一个点前插入 `repeatGapMs`。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.Luofeng.autoclick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickSequenceTest {
    private fun step(id: Long, x: Float, y: Float, delay: Long) =
        ClickStep(id, x, y, delay)

    @Test
    fun emptySteps_isEmpty() {
        val p = ClickProject(1, "", 8, 0, repeatCount = 3, repeatGapMs = 500, steps = emptyList())
        assertTrue(flattenProject(p).isEmpty())
    }

    @Test
    fun singlePoint_repeatThree_usesGapBetweenRounds() {
        val p = ClickProject(
            id = 1, name = "", hour = 8, minute = 0,
            repeatCount = 3, repeatGapMs = 500,
            steps = listOf(step(10, 0.5f, 0.5f, 0))
        )
        val out = flattenProject(p)
        assertEquals(3, out.size)
        assertEquals(0L, out[0].delayAfterPrevMs)
        assertEquals(500L, out[1].delayAfterPrevMs)
        assertEquals(500L, out[2].delayAfterPrevMs)
        assertEquals(0.5f, out[1].xRatio, 0.001f)
    }

    @Test
    fun twoPoints_twoRounds_insertsGapBeforeSecondRound() {
        val p = ClickProject(
            id = 1, name = "", hour = 8, minute = 0,
            repeatCount = 2, repeatGapMs = 500,
            steps = listOf(step(1, 0.1f, 0.1f, 0), step(2, 0.2f, 0.2f, 400))
        )
        val out = flattenProject(p)
        assertEquals(4, out.size)
        assertEquals(listOf(0L, 400L, 500L, 400L), out.map { it.delayAfterPrevMs })
        assertEquals(0.1f, out[2].xRatio, 0.001f)
        assertEquals(0.2f, out[3].xRatio, 0.001f)
    }

    @Test
    fun repeatCountZero_treatedAsOne() {
        val p = ClickProject(
            id = 1, name = "", hour = 8, minute = 0,
            repeatCount = 0, repeatGapMs = 500,
            steps = listOf(step(1, 0.3f, 0.4f, 0))
        )
        assertEquals(1, flattenProject(p).size)
    }
}
```

- [ ] **Step 2: 跑测试确认 RED**

Run: `$env:JAVA_HOME = "D:\Android_Studio\jbr"; .\gradlew :app:testDebugUnitTest --tests com.Luofeng.autoclick.ClickSequenceTest --no-configuration-cache`

Expected: 编译失败 `Unresolved reference 'flattenProject'` 或同类

- [ ] **Step 3: 最小实现 `ClickProject.kt` + `ClickSequence.kt`**

`flattenProject`：

```kotlin
fun flattenProject(project: ClickProject): List<ScheduledClick> {
    if (project.steps.isEmpty()) return emptyList()
    val rounds = project.repeatCount.coerceAtLeast(1)
    val out = ArrayList<ScheduledClick>(rounds * project.steps.size)
    repeat(rounds) { round ->
        project.steps.forEachIndexed { index, step ->
            val delay = when {
                index > 0 -> step.delayFromPrevMs
                round == 0 -> step.delayFromPrevMs
                else -> project.repeatGapMs
            }
            out.add(ScheduledClick(step.xRatio, step.yRatio, delay))
        }
    }
    return out
}
```

- [ ] **Step 4: 跑测试确认 GREEN**

Run: 同 Step 2  
Expected: BUILD SUCCESSFUL，该测试类 0 failures

---

### Task 2: 仓库读写与旧 JSON 迁移

**Files:**
- Modify: `app/src/test/java/com/Luofeng/autoclick/TaskRepositoryParseTest.kt`
- Modify: `app/src/main/java/com/Luofeng/autoclick/TaskRepository.kt`
- Modify: 所有 `ClickTask` / `List<ClickTask>` 引用改为 `ClickProject`（本 task 至少改仓库签名；编译会红直到后续 task 跟上——**本 task 结束前必须让 `:app:compileDebugKotlin` 能过**，因此同一 task 内把 `ClickTask` 引用全部替换为 `ClickProject` 的最小补丁：scheduler 暂用 `project.steps.first()` 的坐标填 extras，下一 task 再改成只传 id）

更干净的做法：**本 task 只改仓库 + 测试 + 把 `ClickTask` 改为 type alias 过渡禁止。** 直接替换类型，scheduler 用：

```kotlin
val first = task.steps.firstOrNull()
putExtra("xRatio", first?.xRatio ?: 0f)
```

直到 Task 3 删掉这些 extras。

**Interfaces:**
- Consumes: `ClickProject` / `ClickStep`
- Produces: `TaskRepository.loadTasks: MutableList<ClickProject>`，`parseTasksJson` / `tasksToJson` 新格式与旧格式

- [ ] **Step 1: 改测试为项目模型并加迁移用例（先改测试，跑 RED）**

把 `roundTrip_preservesFields` 换成带 `steps` 的 `ClickProject`。保留 `legacyXy_convertsToRatio`，断言迁出：

- `steps.size == 1`，`delayFromPrevMs == 0`
- `repeatCount ==` 旧 `count`
- `repeatGapMs ==` 旧 `intervalMs`
- `name == ""`

新增：含 `steps` 的 JSON round-trip 保留 step id 与 delay。

旧 `oneBadItem` / `corruptJson` 行为不变。

- [ ] **Step 2: 跑 `TaskRepositoryParseTest` 确认 RED**

- [ ] **Step 3: 实现 parse/write**

新 JSON 字段：`name`, `repeatCount`, `repeatGapMs`, `steps`（数组）。  
`tasksToJson` 写新格式。  
`parseTaskObject`：若有 `steps` 则解析列表（单步坏则跳过该步；项目无有效步则整项 skip）；若无 `steps` 则用现有坐标逻辑生成一步，`repeatCount = o.getInt("count")`，`repeatGapMs = o.getLong("intervalMs")`，`name = o.optString("name", "")`。

`ClickTask.kt` 删除，全工程改 `ClickProject`。`TaskScheduler.remainingSeconds` 改接收 `ClickProject`（时刻字段同名）。

- [ ] **Step 4: 跑 `TaskRepositoryParseTest` + `ClickSequenceTest` + `compileDebugKotlin` GREEN**

---

### Task 3: 序列点击与闹钟只传 id

**Files:**
- Modify: `ClickAccessibilityService.kt`
- Modify: `TaskScheduler.kt`, `AlarmReceiver.kt`, `PreAlarmReceiver.kt`, `CountdownOverlayService.kt`
- Modify: `AppTiming.kt` 仅当需要新常量时；轮间隔默认已有 `DEFAULT_INTERVAL_MS = 500`

**Interfaces:**
- Consumes: `flattenProject`, `ClickProject`
- Produces:

```kotlin
fun ClickAccessibilityService.performSequence(clicks: List<ScheduledClick>, screenWidth: Int, screenHeight: Int)
```

累加 delay：`elapsed = 0`; 对每个 click：`elapsed += delayAfterPrevMs`; `handler.postDelayed({ clickAt(x,y) }, elapsed)`。坐标 `x = xRatio * screenWidth`。

`performClicks(x,y,count,intervalMs)` 改为构造临时一步序列（给 3 秒测试点击用）：count 次同一点，第一次 delay 0，其余 delay `intervalMs`。

AlarmReceiver：读 `id`，`ClickDedupGuard.tryMark(id)`，load 项目，`flattenProject`，`performSequence`。不要再读 extras 里的 xRatio/count。然后若 enabled 则 `scheduleTask`。

PreAlarm / Countdown：load 项目，标记第一点；到点调用 `performSequence` 而非 `performClicks`。Countdown extras 可只留 `taskId` + `targetTime`（改 extras 时预告与主闹钟一并改，避免半新半旧）。

Scheduler `mainPendingIntent` / `prePendingIntent` 只 `putExtra("id", ...)`，pre 另加 `targetTime`。

- [ ] **Step 1: 给 flatten 已覆盖的调度语义补测试（可选同 Task 1）；无 Android 的 performSequence 不单测。改生产代码。**

- [ ] **Step 2: compileDebugKotlin + 全量 testDebugUnitTest GREEN**

---

### Task 4: 简化选点浮层

**Files:**
- Modify: `app/src/main/res/layout/overlay_point_picker.xml`
- Modify: `OverlayPointPickerService.kt`
- Modify: `PointPickerBus.kt`
- Modify: `app/src/main/res/values/strings.xml`（可删 `overlay_drag_hint` 引用；`cancel` 若仅 overlay 使用可留着给对话框）

**Interfaces:**
- Produces:

```kotlin
sealed class PickerPurpose {
    data object NewProject : PickerPurpose()
    data class AddStep(val projectId: Long) : PickerPurpose()
    data class EditStep(val projectId: Long, val stepId: Long) : PickerPurpose()
}

data class PickedPoint(
    val x: Float,
    val y: Float,
    val purpose: PickerPurpose
)
```

Intent extras：`purpose` 字符串 `new` / `add` / `edit`，`projectId`，`stepId`，`initialX`，`initialY`。缺省 purpose=`new`，initial&lt;0 → 屏幕中心。

XML：去掉 hint `TextView` 和 `btnCancel`。保留 `btnConfirm` + 新增 `tvCoords`（例如 `%d, %d`）。`targetMarker` 不变。

`setupOverlay`：顶栏只含坐标 + 确定。`bar` 去掉 `FLAG_NOT_FOCUSABLE`（保留 `NOT_TOUCH_MODAL` + `LAYOUT_IN_SCREEN` + `NO_LIMITS`），`isFocusableInTouchMode = true`，`setOnKeyListener` 对 `KEYCODE_BACK`：`stopSelf()` 且不写 Bus。Marker 窗口仍 `NOT_FOCUSABLE`。拖动时更新 `tvCoords`。无 initial 时中心（已有逻辑，保持）。

- [ ] **Step 1: 改 XML 与 Service（无新纯函数则本 task 以 compile 为准）**
- [ ] **Step 2: 跑已有 `OverlayPickerLayoutTest`（origin/center 未改应仍 PASS）+ compileDebugKotlin**

---

### Task 5: 主列表可展开卡片 + 动效 + 长按

**Files:**
- Modify: `MainActivity.kt`（`TaskCard` 改为 `ProjectCard`；可同文件，避免无关拆分）
- 项目表单：时刻 + delayOffset + repeatCount + repeatGapMs + 可选名称；去掉「间隔/次数/选点」作为创建点的主路径

**交互（必须全部接到）：**

1. `expandedIds: SnapshotStateSet<Long>`（或 `mutableStateMap`），不持久化  
2. 点击卡片内容（排除药丸）：toggle 展开。`Modifier.animateContentSize(tween(AppDuration.stateMs))` + `AnimatedVisibility(visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut())`  
3. 箭头 `Icons.Default.ExpandMore`，`Modifier.rotate` + `animateFloatAsState`  
4. 收缩摘要：`"${steps.size} 个点位 · 重复 ${repeatCount} 次"`，有 delayOffset 时追加延迟文案；enabled 时 `formatRemaining`  
5. 展开：点位行（序号或简称「点位 n」、坐标百分比）、行前延迟芯片（第一点显示「开始后立即」不可点；其后显示 `延迟 xxx ms`，点击弹出数字框改 `delayFromPrevMs`）  
6. 底部 `TextButton`「添加点位」→ overlay `PickerPurpose.AddStep`  
7. 短按点位行 → `EditStep` 选点  
8. 短按时间标题 → 项目表单  
9. 长按卡片 → `ModalBottomSheet` 或 `AlertDialog`：重命名（再弹出输入框）、复制、删除（删除仍确认）  
10. 长按点位行 → 复制 / 删除；复制：`step.copy(id = System.currentTimeMillis())` 插入 `index+1`；删空 steps 则 `enabled=false` 并 `cancelTask`  
11. 复制项目：`id=now`，`name = (name.ifBlank { "项目" }) + " 副本"`，steps 全部新 id，`enabled=false`，插入列表，save，不 schedule  
12. FAB：picker `NewProject`；确认后先项目表单（默认时刻=现在，repeatCount=1，repeatGapMs=500，一步 delay 0），保存并 schedule  
13. AddStep 确认后 **必须** 弹出延迟对话框（默认：上一 step 的 delay，若无则 500），取消则不加步  
14. 新建/编辑/删改后 `saveTasks` + 按 enabled 调度  
15. `FormTarget` / 旧 TaskFormDialog 的 interval+count+单点 删除或改成项目表单；测试点击改为对当前编辑点 `performClicks` 一次或对该点坐标点 1 次  

复制/重命名/删除不要在 `onClick` 里误触发：`combinedClickable` 长按只开菜单。

- [ ] **Step 1: 实现 UI 与 Bus 消费（`LaunchedEffect` 读 `pickedPoint` 按 purpose 分支）**
- [ ] **Step 2: compileDebugKotlin + testDebugUnitTest 全绿**

---

### Task 6: 全量核对与手工清单

**Files:** 无新文件

- [ ] **Step 1: 对照 spec 逐条**

| Spec | 对应 |
|------|------|
| flatten 规则 | Task 1 |
| JSON 迁移 | Task 2 |
| 闹钟 id + 序列点击 | Task 3 |
| 选点简化 + 中心默认 + 返回取消 | Task 4 |
| 展开收缩动效、长按菜单、加点延迟、项目时刻/重复 | Task 5 |
| 不改群聊/时钟/FGS | 未改那些文件的成功路径 |

- [ ] **Step 2: 跑**

`$env:JAVA_HOME = "D:\Android_Studio\jbr"; .\gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-configuration-cache`

Expected: BUILD SUCCESSFUL，failures=0

- [ ] **Step 3: 手工（实现者在真机/模拟器勾）**

- 旧数据升级后，原「5 次 / 500ms」到点仍是 5 下间隔约 500ms  
- FAB → 准星在中心、无提示无取消、有坐标、确定回 App  
- 展开加点 → 填延迟 → 链上多一行  
- 收缩外观接近旧卡片  
- 长按项目：重命名/复制/删除；长按点位：复制/删除  
- 展开/加点带动效  

---

## Spec coverage

- 项目字段与第一点 delay 0：Task 1–2、5  
- 新点必填延迟：Task 5.13  
- 收缩/展开 + 动效：Task 5.2–5.5  
- 选点简化：Task 4  
- 长按菜单：Task 5.9–5.11  
- 调度与兼容：Task 2–3  

## 类型一致性

- 全程 `ClickProject` / `ClickStep` / `ScheduledClick` / `flattenProject` / `PickerPurpose`  
- 仓库函数名保持 `loadTasks` / `saveTasks` / `parseTasksJson` / `tasksToJson`（避免无谓改 prefs key）
