# AutoClick 代码质量诊断报告 + UI 审计报告

> 日期：2026-08-24  
> 范围：`app/src` 全部 Kotlin / XML / Gradle 源码（不含 `build/`、`.cursor/`）  
> 约束：功能零变更、接口与配置格式不变、禁止新 UI 框架  
> 状态：**仅分析，未改代码。待确认后再执行。**

---

## 0. 项目画像

AutoClick 是一个 Android 定时自动点击助手：Compose 主界面管理任务，AlarmManager 准时触发，无障碍服务执行点击，悬浮窗用于选点与倒计时。

| 项 | 现状 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose Material3 + 少量 View 悬浮窗 XML |
| 包名 | `com.Luofeng.autoclick` |
| minSdk / targetSdk | 25 / 36 |
| 业务 Kotlin | 17 个文件，主界面 `MainActivity.kt` 约 763 行 |
| 测试 | 仅模板 `ExampleUnitTest` / `ExampleInstrumentedTest`，**无业务测试** |
| Lint | 0 error，14 warning，1 hint |

**对外契约（执行期必须保持不变）：**

- SharedPreferences `click_tasks_pref` / `tasks_json` 字段：`id, xRatio, yRatio, hour, minute, second, millisecond, delayOffsetMs, intervalMs, count, enabled`，以及旧版 `x/y` 像素回退
- Overlay Intent extras：`editingTaskId, initialX, initialY, x, y, targetTime, taskId, intervalMs, count`
- Alarm extras：`id, xRatio, yRatio, intervalMs, count, targetTime`
- 无 CLI；无公开 SDK。内部 companion `ClickAccessibilityService.instance` 视为内部实现，不作为对外 API 承诺，但本期不改其语义

---

## 1. 代码质量诊断报告

### 1.1 优点（应保留）

- 点击坐标用 `xRatio/yRatio` + `ScreenUtils.getRealScreenSize()`，选点与点击坐标系一致
- 任务倒计时用 `Map<Long, TaskOverlay>`，多任务互不覆盖
- `ClickDedupGuard` 防止倒计时 overlay 与 `AlarmReceiver` 双触发
- `TaskRepository` 已兼容旧 `x/y` 像素字段
- Overlay 使用 `FLAG_LAYOUT_NO_LIMITS` + cutout mode，处理刘海屏
- `DesignTokens.kt` 已有初步颜色/圆角集中点，方向正确

### 1.2 健壮性

| ID | 严重度 | 位置 | 问题 | 建议 | 风险 |
|---|---|---|---|---|---|
| R1 | 高 | `TaskRepository.loadTasks` | `JSONArray(json)` / `getLong` 等无 try，损坏 JSON 会闪退 | 逐条 try/catch，坏条目跳过并打日志；**不自动 save**，避免覆盖用户数据 | 低 |
| R2 | 中 | `TaskScheduler.scheduleTask` | `setExactAndAllowWhileIdle` 在 Android 12+ 无精确闹钟权限时可能 `SecurityException` | catch + 结构化日志；**不要静默降级为非精确闹钟**（会改变准时性） | 低（只记日志） |
| R3 | 中 | `OverlayPointPickerService.onDestroy` | `removeView` 无 try；其它 overlay 都有 | 与倒计时 overlay 对齐，捕获 `IllegalArgumentException` | 低 |
| R4 | 中 | `CountdownOverlayService` / `ClickAccessibilityService` | 多处 `catch (e: Exception) {}` 吞掉异常 | 至少 `Log.w(tag, "…", e)` | 低 |
| R5 | 中 | `MainActivity` 测试点击 | 裸 `Handler.postDelayed(3000)`，Activity 销毁后仍可能回调 | 用 `DisposableEffect` + 可取消的 Job；超时值保持 3000ms | 低 |
| R6 | 中 | `ClickAccessibilityService.performClicks` | Service destroy 后 Handler 仍可能继续点击 | 高风险：加取消会改变进行中的点击序列 | **高风险，本期不做** |
| R7 | 中 | `findClickableNodeAt` | 子节点 `AccessibilityNodeInfo` 未 recycle | OEM 上 recycle 时机极易崩溃 | **高风险，本期不做** |
| R8 | 中 | `PreAlarmReceiver` | 后台 `startService`，Android 8+ 可能 `IllegalStateException` | 改 FGS 会改变系统通知与生命周期 | **高风险，本期不做** |
| R9 | 低 | `AlarmReceiver` / `PreAlarmReceiver` | `id == -1` 仍继续流程 | 非法 id 直接 return + 日志 | 低 |
| R10 | 低 | `BootReceiver` | 只处理 `BOOT_COMPLETED`，未处理 `LOCKED_BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` | 补广播会**新增行为** | **不做**（功能零变更） |
| R11 | 低 | `TaskFormDialog` | `count` 允许 0；interval 空串变 1 | 保持现状，只补测试锁死当前规则 | 不改行为 |

### 1.3 可维护性

| ID | 问题 | 建议 | 风险 |
|---|---|---|---|
| M1 | `MainActivity.kt` 763 行，Activity + 全部 Dialog/卡片混在一起 | 可抽 `ui/` 文件，但属于结构性拆分 | **高风险重构建议，默认不拆文件** |
| M2 | 魔法数字散落：`3000, 500, 30_000, 200, 80, 1000, 46.dp, 14.dp, 7.dp` | 抽到 `AppDimens` / `AppTiming` 常量，**值不变** | 低 |
| M3 | `AppPrefs` 整文件无人引用（`floating_button`） | 删除或保留并标注死代码 | 低；建议标死代码，**默认不删**（避免误伤后续功能） |
| M4 | `AppColors.LightBlueTintDeep / DangerLight / WarningLight`、`AppShapes.menuShape` 未使用 | 本期接到真实控件上，或留在 token 里给暗色/菜单用 | 低 |
| M5 | 日志 tag `"AutoClick"` 硬编码，中英混杂，无统一字段 | `object AppLog { const val TAG = "AutoClick" }` + 关键路径 `key=value` | 低 |
| M6 | Compose 文案几乎全硬编码，XML 才走 `strings.xml` | 抽字符串会大 diff，易漏 | 中；建议**仅新增文案走 strings**，旧文案本期不搬 |
| M7 | 命名大体一致；`Luofeng` 包名大小写不合 Java 惯例 | 改包名破坏安装升级 | **禁止** |

### 1.4 性能

| ID | 问题 | 建议 | 风险 |
|---|---|---|---|
| P1 | `AppRoot` 每秒轮询 3 个系统权限 + `nowTick`；`LiveClock` 另有 1s 循环 | 合并为单一 ticker；权限检查可 3–5s 一次（**会改变权限角标刷新频率**） | 权限降频 = 轻微行为变化，需你确认。时钟合并不影响功能 |
| P2 | `WheelPicker` `loopMultiplier=1000`，约 2.4 万虚拟 item | 改小倍数会改变滚轮“到头”手感 | **高风险，本期只加注释不改算法** |
| P3 | 每秒 `TaskCard` 全量 `remember` 重算 remaining | 可接受；任务量通常很少 | 不改 |
| P4 | Lint：`nowTick` 应用 `mutableLongStateOf`；`tasks` 用 `MutableList` 包在 `mutableStateOf` 里 | 改为 `mutableLongStateOf` + 赋值新 List（代码已是新 list，类型改成 `List` 即可） | 低 |
| P5 | `qr_placeholder.jpg` 放在 `drawable/` 无密度 | 挪到 `drawable-nodpi` 或 mdpi | 低，纯资源 |

### 1.5 类型安全

- Kotlin 数据类本身类型清晰
- Intent extras 全是无类型 `putExtra`，缺 key 靠默认值，无编译期保证
- `TaskRepository` `getDouble().toFloat()` 隐式精度损失，坐标场景可接受
- Compose `mutableStateOf(MutableList)`：Lint 已警告，内部修改不触发重组——当前每次都 `tasks = tasks.toMutableList().also {…}` 再赋值，**实际能重组**，但类型应改为 `List<ClickTask>`

### 1.6 资源管理

| 资源 | 现状 | 缺口 |
|---|---|---|
| WindowManager views | Overlay destroy 时 remove；ripple 500ms 后 remove | picker `onDestroy` 无 catch；ripple Handler 在 service 已 destroy 时仍可能跑 |
| Handler | 倒计时 overlay 的 `removeCallbacksAndMessages` 正确 | `performClicks` / 测试点击 / ripple 的 Handler 无生命周期绑定 |
| AccessibilityService.instance | `onDestroy` 置 null | 静态持有 Service，常规模式，**不改** |
| SharedPreferences | `edit { }` 自动 apply | 正常 |
| JSON / 文件 | 无文件句柄泄漏 | 正常 |

### 1.7 日志与监控

- 有日志：无障碍连接、点击、手势、Alarm 去重
- 无日志：任务 CRUD、schedule/cancel、JSON 解析失败、overlay add/remove 失败、选点确认
- 空 catch 导致失败不可观测
- 建议统一 `AppLog`，关键操作用 `Log.i`，失败用 `Log.w/e` 带 exception，格式 `event=schedule taskId=… triggerAt=…`

### 1.8 测试缺口

当前测试不能保护“功能零变更”。执行期应先补**行为锁定测试**（不改生产逻辑）：

- `formatRemaining`
- `TaskScheduler.nextTriggerMillis` / `remainingSeconds`（固定时钟需小重构才可测——若引入 `Clock` 接口属行为中性，但要小心；可用反射/同包测试 `baseTriggerMillis` 逻辑的纯函数抽取）
- `ClickDedupGuard.tryMark/clear`
- `TaskRepository` JSON 往返 + 旧 `x/y` 兼容 + 坏 JSON 不崩溃
- 表单校验规则：interval 最小 1、delayOffset coerceIn(-1000,1000)、count 空串→1（**锁死现状，含 count=0**）

---

## 2. UI 审计报告

### 2.1 现状：双主题系统（核心问题）

1. **`DesignTokens.kt` / `AppColors`**：主界面真正使用的 iOS 浅蓝系（`#007AFF` / `#F2F4F8`）
2. **`ui/theme/Color.kt` + `Theme.kt`**：Android Studio 模板紫粉色，且 `dynamicColor = true`
3. **悬浮窗 XML**：Material `#2196F3`、灰 `#757575`、标记 `#0066FF`，与主色 `#007AFF` 不一致

结果：Scaffold/卡片是 iOS 蓝，`AlertDialog` / `OutlinedTextField` / `TextButton` 在 Android 12+ 会跟壁纸动态色走，时间滚轮又用了第三套 `#4A90E2`。

`AutoClickTheme(darkTheme = isSystemInDarkTheme())` 已存在，但 **AppColors 全是亮色硬编码**，系统暗色时会出现：Material 对话框变暗、主列表仍是浅灰底。这不是暗色模式，是错配。

### 2.2 视觉层次与 WCAG 对比度

估算（sRGB 相对亮度，AA 正文需 ≥ 4.5:1）：

| 组合 | 对比度（约） | 结论 |
|---|---|---|
| `TextPrimary #1C1C1E` on 白/浅灰底 | ~16:1 | 通过 |
| `TextSecondary #8E8E93` on `#F2F4F8` / 白卡片 | ~3.0–3.4:1 | **不通过 AA** |
| `PillOffText`（同 secondary）on `#F0F0F2` | ~3.3:1 | **不通过** |
| `PrimaryBlue #007AFF` on 白（时钟、链接） | ~4.6:1 | 大字勉强，小字临界 |
| `Danger #FF3B30` on 白（剩余时间加急） | ~3.5:1 | **不通过** |
| 菜单 `Divider #FFFFFF` on `LightBlueTint #E8F1FE` | 极低 | 分割线几乎看不见 |
| Overlay 白字 on `#CC007AFF` / `#CC1C1C1E` | 足够 | 通过（悬浮层保持高对比即可） |

**建议（不改品牌色相，只加深用于正文的灰/红）：**

- `TextSecondary`：`#8E8E93` → `#6C6C70`（约 4.6:1+）
- 小字强调红：保留 `Danger` 用于按钮，紧急倒计时改用 `Danger` 加深版或 `FontWeight.Bold` + 略深红 `#D70015`
- 菜单分割线：使用 `DividerOnCard` 而非白
- 主色 `#007AFF` 保持，时钟已是大字，满足大文本 AA

### 2.3 尺寸与 4/8 基线网格

已接近 4dp 网格，但有偏移：

| 现状 | 问题 | 建议（值仅作视觉对齐，交互不变） |
|---|---|---|
| 水平页边 20dp | 合法 4dp 网格 | 保持 20 或统一 16；**推荐保持 20**（呼吸感更好） |
| 卡片间距 14dp | 不在 4 网格 | → 16dp |
| 卡片内图标 46dp、菜单项高 46dp | 不在 4 网格 | → 48dp |
| StatusPill 垂直 padding 7dp、水平 14dp | 7 不在网格 | → 8 / 16 |
| 图标与文字间距 14dp | | → 16dp |
| TaskCard 副标题 spacer 2dp | 过挤 | → 4dp |
| FAB 下方占位 90dp | | 保持，避免挡列表 |
| LiveClock top 40dp | 合法 | 保持 |

用户示例“卡片 8px / 弹窗 12px”偏 Android 紧凑。**当前是 iOS 大圆角（卡片 16 / 弹窗 20），推倒成 8/12 会明显改脸。** 推荐保留现有层级，只规范化：

| 角色 | 现状 | 推荐规范 |
|---|---|---|
| 小控件 / 输入描边 | 12 | 8（输入保持 12 更贴近现状） |
| 按钮 / 输入 | 12 | **12** |
| 卡片 | 16 | **16** |
| 菜单 | 14（未用） | **12** 并真正接到 DropdownMenu |
| 弹窗 | 20 | **16 或 20**（建议保持 20，避免弹窗变“硬”） |
| 胶囊 / 圆形图标 | 50% | 保持全圆 |

### 2.4 字体排版

- 未使用 `Type.kt` 的 `Typography`（几乎是模板默认）
- 字号散落：时钟自适应 `maxWidth*0.21.sp`、15/14/13/12/11/20
- 11sp 辅助文字偏小，且对比度差，可读性差
- 无统一 lineHeight / 字重阶梯

推荐 Compose 层级（接到 `Type.kt` + `AppTypography`）：

| 角色 | 字号 | 字重 | 行高 |
|---|---|---|---|
| 时钟 | 保持宽度自适应 | Bold | 默认 |
| 标题（TopBar / 弹窗标题） | 20.sp | SemiBold | 28 |
| 区块标题「任务列表」 | 14.sp | SemiBold | 20 |
| 卡片主文案 | 16.sp | SemiBold | 22 |
| 正文 / 菜单 | 14.sp | Normal | 20 |
| 辅助 | 12.sp（11→12） | Normal | 16 |
| 胶囊标签 | 12.sp | Medium | 16 |

字体栈：保持 `FontFamily.Default`（系统），不引入下载字体。

### 2.5 交互反馈

- `StatusPill` 已有 ripple，无颜色/缩放 transition
- 卡片 `combinedClickable` 默认 ripple，无 200–300ms 状态过渡
- Dialog 按钮无 disabled（保存始终可点，空 interval 被 coerce）——**不要新增 disabled 以免改变可点性**
- Overlay 按钮是系统 `Button` 默认高程，与 Compose 扁平风格不一致

建议：仅给 Pill / 卡片 / Overlay 按钮加 `animateColorAsState(tween(220))` 或 XML `colorStateList`；不改点击逻辑。

### 2.6 暗色模式

- **无** `values-night`，无暗色 `AppColors`
- 用户要求：亮色则补暗色
- Overlay 画在别的 App 上，应**继续用高对比半透明蓝/深底**，不跟随应用暗色（否则在亮色游戏界面上会看不清）

应用内暗色建议：

- 背景 `#000000` / `#1C1C1E`
- 卡片 `#2C2C2E`
- 主色仍 `#0A84FF`（iOS 暗色蓝）
- 主文字 `#F5F5F7`，辅文字 `#A1A1A6`（需再测对比度）
- 跟随 `isSystemInDarkTheme()`，**不新增设置项**（新增开关 = 新功能，禁止）

### 2.7 响应式

- 时钟 `BoxWithConstraints` 按宽缩放：好
- 任务表单多个 `OutlinedTextField` 在小屏弹窗可能挤出：`AlertDialog` 默认可滚动，风险中等
- Overlay 提示 `layout_marginTop=60dp`：刘海设备可能偏下，功能可用
- 横屏未专门适配；列表 + 居中 FAB 一般不崩
- 建议：表单 `verticalScroll` + 弹窗最大高度；小宽时时钟系数加 `coerceIn`

### 2.8 空白与呼吸感

- 整体已有 iOS 式留白，不算拥挤
- 卡片内副标题 11sp + 2dp 间距偏挤
- 空状态只有居中灰字，可加大行距，**不新增插画/按钮**（零功能）

### 2.9 Overlay 视觉分裂

`overlay_point_picker.xml` 确认按钮 `#2196F3` vs 品牌 `#007AFF`；标记 `#0066FF` vs `#007AFF`；hint 圆角 14 vs token 12/16。应把颜色收到 `res/values/colors.xml`，圆角收到 `dimens.xml`，与 `AppColors.PrimaryBlue` 同源。

---

## 3. 高风险重构建议（默认不执行，需你点名）

1. **拆分 `MainActivity.kt`** 为多个 ui 文件  
2. **无障碍节点 recycle / 点击 Handler 取消**  
3. **倒计时/闹钟改为 Foreground Service**  
4. **WheelPicker 虚拟列表算法重写**  
5. **改包名、改 JSON schema、改 Alarm extra 名**  
6. **精确闹钟失败时改用非精确闹钟**  
7. **把 Compose 硬编码中文全部迁到 strings.xml**（大 diff）  
8. **删除 `AppPrefs`**  
9. **权限轮询从 1s 改为 5s**（角标刷新变慢）  
10. **圆角从 16/20 降到 8/12**（改脸）

---

## 4. 推荐策略（待你确认）

**推荐：精装修方案（保守 + 主题统一 + 暗色）。**

- 功能、JSON、Intent、点击算法、闹钟策略、滚轮算法、文件结构大框架全部不动
- 样式集中在 `DesignTokens.kt`、`ui/theme/*`、`res/values/colors.xml` / `dimens.xml`、现有 XML drawable
- 健壮性只加保护与日志，失败路径不改变成功路径
- 先补锁定测试，再改生产代码

备选 A：只做 token/对比度/间距，不做暗色  
备选 B：加上面全部 + 拆分 MainActivity（不推荐本期）
