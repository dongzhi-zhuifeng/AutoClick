# 点击项目（多点位序列）Design

**Date:** 2026-08-25  
**Status:** 思路已确认，待你审阅本 spec 后按计划实现

## 目标

在「到点自动点」不变的前提下，把原来的「一个任务 = 一个点」升级成 **一个项目 = 开始时刻 + 重复轮数 + 一条点位链**。点位链可视化：收缩卡片接近现状；展开后能看/改每个点；新点只多填一段相对上一点的延迟。

## 用户可见模型

### 项目（大卡片）

| 字段 | 含义 |
|------|------|
| 名称 | 默认「项目」；长按可重命名 |
| 开始时刻 | 时/分/秒 + 现有 `delayOffsetMs`（时钟微调，-1000~+1000） |
| 重复次数 `repeatCount` | 闹钟触发后，整条点位链跑几轮，≥1 |
| 轮间隔 `repeatGapMs` | 上一轮最后一次点击 → 下一轮第一个点，默认 500ms；旧任务的 `intervalMs` 迁到这里 |
| 开关 | 管整个项目的闹钟 |

第一点相对开始时刻的延迟固定为 **0**（到点立刻点第一个位置）。不在 UI 上为第一点单独要延迟。

### 点位（展开后的行）

| 字段 | 含义 |
|------|------|
| 坐标 | 屏幕比例 `xRatio` / `yRatio` |
| `delayFromPrevMs` | **仅第二点及以后**：相对上一次点击的等待。新加点位时必填，默认抄上一段延迟，没有上一段则 500 |

一块 = 一次点击。同一坐标连点 = 多行同坐标，或长按点位「复制」。

### 执行时间轴（规范）

对 `repeatCount` 的每一轮、按点位顺序：

1. 第一轮第一个点：等待 `delayFromPrevMs`（恒为 0），然后点击  
2. 同一轮后续点：等待该点的 `delayFromPrevMs`，然后点击  
3. 下一轮开始前：等待 `repeatGapMs`，再点第一个点  

例：A(0) → B(400) → 重复 2、轮间隔 500 → `A —400— B —500— A —400— B`

单点 + 重复 5 + 轮间隔 500 ≈ 旧模型「一点、间隔 500、共 5 次」。

## 主列表交互

- **收缩**：布局与现卡片接近（时间、摘要、倒计时、运行中药丸）。摘要改为 `N 个点位 · 重复 M 次`（有延迟偏移时仍显示 `延迟 ±x ms`）。
- **点击卡片非药丸区域**：展开/收起。展开后在卡片内看到点位链、每段延迟、底部「+ 添加点位」。
- **点击时间行**（展开或收缩均可）：打开项目表单（时刻、延迟偏移、重复次数、轮间隔），不打开旧的「选点+次数」大表单。
- **药丸**：只切换 `enabled`，不触发展开。
- **长按项目卡片**：动作表 **重命名 / 复制 / 删除**（复制：新 id、名称加「 副本」、步骤新 id、默认停止并写入存储；删除需确认）。
- **长按点位行**：动作表 **复制 / 删除**（复制插入到该行下方，同坐标同延迟；删除最后一点时项目仍保留，但启用中的空链不点击——保存时若 `steps.isEmpty()` 则强制 `enabled=false` 并取消闹钟）。
- **+ 添加点位**：现有悬浮选点；确认后弹出延迟输入（必填，数字，单位 ms，默认值如上），写入链尾。
- **点点位行（短按）**：再次选点，更新该行坐标，延迟不变。

展开状态只存在会话内（`remember`），不写入 JSON。

## 动效（提高操作感，不新造一套语言）

一律用现有 `AppDuration.stateMs`（220ms），必要时加 `AppDuration.clockToggleMs`（280ms）：

- 卡片展开/收起：`animateContentSize` + `AnimatedVisibility`（`expandVertically` + `fadeIn/Out`）
- 展开箭头旋转 220ms
- 点位增删：`AnimatedVisibility` 或 `LazyColumn` 的 `animateItem`
- 动作表：Material3 `ModalBottomSheet` 或 `AlertDialog` 列表，出现用默认 enter
- 药丸颜色动画保持现状
- 不改时钟数字↔表盘过渡

## 选点浮层（简化）

只保留：

- 准星，**无初始坐标时默认屏幕中心**（FAB 新建已如此；编辑/追加传入当前点）
- 实时坐标文案（像素，随拖动更新）
- 「确定位置」→ 回 App

删除：上方提示「拖动标记到目标位置」、左侧「取消」。

取消路径：选点顶栏可获焦，**系统返回**关闭服务且不写 `PointPickerBus`。不要用「切回 App」当取消（用户本来就要切到目标 App 再选点）。

不改：双窗口（顶栏 + 24dp marker）、`FLAG_NOT_TOUCH_MODAL`、拖动改 `LayoutParams`、确认后 `REORDER_TO_FRONT`。

## 调度与点击

- 闹钟仍 **每个项目一个** exact alarm + 30s 预告。Intent **只带 `id`**，触发时从 `TaskRepository` 读最新项目再展开序列（避免 extras 塞整条链）。
- `ClickDedupGuard` 仍按项目 `id`。
- `ClickAccessibilityService` 增加按 `List<ScheduledClick>` 累加 `postDelayed` 的序列点击；单点 `performClicks` 改为调用序列或保留给测试点击。
- 倒计时浮层标记 **第一点** 坐标；到点后跑完整序列，不按旧的 count/interval extras 连点同一坐标。
- Exact alarm / 后台 startService 失败仍只打日志，不降级、不上 FGS。

## 数据与兼容

JSON 数组仍存在 `tasks_json`。新对象含 `name`、`repeatCount`、`repeatGapMs`、`steps: [{id,xRatio,yRatio,delayFromPrevMs}]`。

无 `steps` 的旧对象：一个 step（坐标来自 xRatio/yRatio 或旧 x/y），`delayFromPrevMs=0`，`repeatCount=原 count`，`repeatGapMs=原 intervalMs`，`name=""`（UI 显示「项目」）。坏项仍跳过，整段坏 JSON 仍空列表且不自动覆盖存储。

## 非目标

- 块拖拽排序、分支/条件/循环  
- 倒计时为每个点都打标记  
- 改「加入群聊」、时钟主题、无障碍手势算法、精确闹钟策略  
- 新 UI 框架或新 Gradle 依赖  

## 成功标准

1. 旧单点任务打开后行为与「1 个点位 + 原次数 + 原间隔」一致。  
2. 能在一个项目里加多个点，点与点之间有各自延迟，到点后按链执行。  
3. 卡片可收缩/展开并带动效；长按菜单符合上文。  
4. 选点页无提示、无取消键，默认中心，有坐标和确定。  
5. `:app:testDebugUnitTest` 与 `:app:compileDebugKotlin` 通过。
