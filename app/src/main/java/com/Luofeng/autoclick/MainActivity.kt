package com.Luofeng.autoclick

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.Luofeng.autoclick.ui.theme.AutoClickTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TaskScheduler.rescheduleAll(this)

        setContent {
            AutoClickTheme {
                AppRoot()
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${ClickAccessibilityService::class.java.name}"
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    while (splitter.hasNext()) {
        if (splitter.next().equals(expected, ignoreCase = true)) return true
    }
    return false
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun canScheduleExactAlarms(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        am.canScheduleExactAlarms()
    } else true
}

fun formatRemaining(seconds: Long): String {
    if (seconds <= 0) return "即将执行"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "还有 %d时%02d分".format(h, m)
        m > 0 -> "还有 %d分%02d秒".format(m, s)
        else -> "还有 ${s} 秒"
    }
}

sealed class FormTarget {
    data class New(val x: Float, val y: Float) : FormTarget()
    data class Edit(val task: ClickProject) : FormTarget()
}

data class PendingAddStep(val projectId: Long, val x: Float, val y: Float)

data class PendingDelayEdit(val projectId: Long, val stepId: Long, val initialMs: Long)

data class StepMenuTarget(val projectId: Long, val step: ClickStep)

fun startPointPicker(
    context: Context,
    purpose: String,
    projectId: Long? = null,
    stepId: Long? = null,
    initialX: Float? = null,
    initialY: Float? = null
) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        )
        return
    }
    Toast.makeText(context, "可切换到目标App后再选点", Toast.LENGTH_LONG).show()
    context.startService(
        Intent(context, OverlayPointPickerService::class.java).apply {
            putExtra("purpose", purpose)
            if (projectId != null) putExtra("projectId", projectId)
            if (stepId != null) putExtra("stepId", stepId)
            if (initialX != null) putExtra("initialX", initialX)
            if (initialY != null) putExtra("initialY", initialY)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var testClickJob by remember { mutableStateOf<Job?>(null) }
    var tasks by remember { mutableStateOf<List<ClickProject>>(TaskRepository.loadTasks(context)) }
    val expandedIds = remember { mutableStateSetOf<Long>() }
    var formTarget by remember { mutableStateOf<FormTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<ClickProject?>(null) }
    var projectMenuTarget by remember { mutableStateOf<ClickProject?>(null) }
    var stepMenuTarget by remember { mutableStateOf<StepMenuTarget?>(null) }
    var renameTarget by remember { mutableStateOf<ClickProject?>(null) }
    var pendingAddStep by remember { mutableStateOf<PendingAddStep?>(null) }
    var pendingDelayEdit by remember { mutableStateOf<PendingDelayEdit?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var batteryOk by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var exactAlarmOk by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun persistUpdated(updated: ClickProject, old: ClickProject? = null) {
        if (old != null && old.id != updated.id) TaskScheduler.cancelTask(context, old)
        tasks = replaceProject(tasks, updated)
        TaskRepository.saveTasks(context, tasks)
        if (updated.enabled && updated.steps.isNotEmpty()) {
            TaskScheduler.scheduleTask(context, updated)
        } else {
            TaskScheduler.cancelTask(context, updated)
        }
    }

    DisposableEffect(Unit) {
        onDispose { testClickJob?.cancel() }
    }

    val issueCount = listOf(accessibilityEnabled, batteryOk, exactAlarmOk).count { !it }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(AppTiming.UI_TICK_MS)
            nowTick = System.currentTimeMillis()
            accessibilityEnabled = isAccessibilityServiceEnabled(context)
            batteryOk = isIgnoringBatteryOptimizations(context)
            exactAlarmOk = canScheduleExactAlarms(context)
        }
    }

    LaunchedEffect(Unit) {
        PointPickerBus.pickedPoint.collect { point ->
            if (point != null) {
                when (val purpose = point.purpose) {
                    is PickerPurpose.NewProject -> {
                        formTarget = FormTarget.New(point.x, point.y)
                    }
                    is PickerPurpose.EditStep -> {
                        val existing = tasks.find { it.id == purpose.projectId }
                        if (existing != null) {
                            val screen = ScreenUtils.getRealScreenSize(context)
                            val (xRatio, yRatio) = pixelsToRatio(point.x, point.y, screen.x, screen.y)
                            persistUpdated(updateStepCoordinates(existing, purpose.stepId, xRatio, yRatio), existing)
                        }
                    }
                    is PickerPurpose.AddStep -> {
                        val current = tasks.find { it.id == purpose.projectId }
                        if (current != null && !shouldPromptAddStepDelay(current)) {
                            val screen = ScreenUtils.getRealScreenSize(context)
                            val (xRatio, yRatio) = pixelsToRatio(point.x, point.y, screen.x, screen.y)
                            persistUpdated(
                                appendStep(current, xRatio, yRatio, 0L, System.currentTimeMillis()),
                                current
                            )
                            expandedIds.add(current.id)
                        } else {
                            pendingAddStep = PendingAddStep(purpose.projectId, point.x, point.y)
                        }
                    }
                }
                PointPickerBus.pickedPoint.value = null
            }
        }
    }

    Scaffold(
        containerColor = colors.Background,
        topBar = {
            TopAppBar(
                title = { Text("自动点击助手", fontWeight = FontWeight.Bold, color = colors.TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.Background),
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            if (issueCount > 0) {
                                BadgedBox(badge = { Badge { Text("$issueCount") } }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "菜单", tint = colors.TextPrimary)
                                }
                            } else {
                                Icon(Icons.Default.MoreVert, contentDescription = "菜单", tint = colors.TextPrimary)
                            }
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            shape = AppShapes.menuShape,
                            containerColor = colors.LightBlueTint,
                            modifier = Modifier.width(180.dp)
                        ) {
                            AppMenuItem("权限与设置") { showMenu = false; showPermissionDialog = true }
                            AppMenuDivider()
                            AppMenuItem("加入群聊") { showMenu = false; showGroupDialog = true }
                            AppMenuDivider()
                            AppMenuItem("关于") { showMenu = false; showAboutDialog = true }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            val fabInteraction = remember { MutableInteractionSource() }
            val fabPressed by fabInteraction.collectIsPressedAsState()
            val fabHovered by fabInteraction.collectIsHoveredAsState()
            val fabBg = if (fabPressed || fabHovered) colors.FabPressed else colors.FabContainer
            FloatingActionButton(
                onClick = { startPointPicker(context, "new") },
                modifier = Modifier.padding(bottom = 12.dp),
                interactionSource = fabInteraction,
                shape = AppShapes.fabShape,
                containerColor = fabBg,
                contentColor = colors.FabContent
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加任务", tint = colors.FabContent)
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.Background)
                .padding(padding)
        ) {
            LiveClock(nowTick = nowTick)

            Spacer(modifier = Modifier.height(AppSpacing.lg))

            Text(
                "任务列表",
                fontSize = AppTypeScale.sectionSp.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.TextPrimary,
                modifier = Modifier.padding(horizontal = AppSpacing.pageHPad)
            )

            Spacer(modifier = Modifier.height(AppSpacing.xs))

            if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有任务\n点击下方 + 添加一个吧",
                        textAlign = TextAlign.Center,
                        color = colors.TextSecondary,
                        lineHeight = 22.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = AppSpacing.pageHPad, vertical = AppSpacing.xxs),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGap)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        ProjectCard(
                            project = task,
                            nowTick = nowTick,
                            expanded = task.id in expandedIds,
                            onToggleExpand = {
                                if (task.id in expandedIds) expandedIds.remove(task.id)
                                else expandedIds.add(task.id)
                            },
                            onToggleEnabled = { enabled ->
                                persistUpdated(applyEnabledToggle(task, enabled), task)
                            },
                            onTimeClick = { formTarget = FormTarget.Edit(task) },
                            onLongPress = { projectMenuTarget = task },
                            onAddStep = { startPointPicker(context, "add", projectId = task.id) },
                            onStepClick = { step ->
                                val screen = ScreenUtils.getRealScreenSize(context)
                                startPointPicker(
                                    context,
                                    "edit",
                                    projectId = task.id,
                                    stepId = step.id,
                                    initialX = step.xRatio * screen.x,
                                    initialY = step.yRatio * screen.y
                                )
                            },
                            onStepLongPress = { step ->
                                stepMenuTarget = StepMenuTarget(task.id, step)
                            },
                            onStepDelayClick = { step ->
                                pendingDelayEdit = PendingDelayEdit(task.id, step.id, step.delayFromPrevMs)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(AppSpacing.fabListClearance)) }
                }
            }
        }
    }

    if (showPermissionDialog) {
        PermissionDialog(
            accessibilityEnabled = accessibilityEnabled,
            batteryOk = batteryOk,
            exactAlarmOk = exactAlarmOk,
            onDismiss = { showPermissionDialog = false }
        )
    }

    if (showGroupDialog) {
        GroupDialog(onDismiss = { showGroupDialog = false })
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    projectMenuTarget?.let { project ->
        ProjectActionSheet(
            onDismiss = { projectMenuTarget = null },
            onRename = {
                projectMenuTarget = null
                renameTarget = project
            },
            onCopy = {
                val copy = copyProject(project, System.currentTimeMillis())
                tasks = insertProjectAfter(tasks, project.id, copy)
                TaskRepository.saveTasks(context, tasks)
                projectMenuTarget = null
            },
            onDelete = {
                projectMenuTarget = null
                deleteTarget = project
            }
        )
    }

    stepMenuTarget?.let { target ->
        StepActionSheet(
            onDismiss = { stepMenuTarget = null },
            onCopy = {
                val project = tasks.find { it.id == target.projectId }
                val index = project?.steps?.indexOfFirst { it.id == target.step.id } ?: -1
                if (project != null && index >= 0) {
                    persistUpdated(copyStepAt(project, index, System.currentTimeMillis()), project)
                }
                stepMenuTarget = null
            },
            onDelete = {
                val project = tasks.find { it.id == target.projectId }
                val index = project?.steps?.indexOfFirst { it.id == target.step.id } ?: -1
                if (project != null && index >= 0) {
                    persistUpdated(deleteStepAt(project, index), project)
                }
                stepMenuTarget = null
            }
        )
    }

    renameTarget?.let { project ->
        RenameProjectDialog(
            initialName = project.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                persistUpdated(renameProject(project, name), project)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = AppShapes.dialogShape,
            containerColor = colors.Background,
            title = { Text("删除项目") },
            text = { Text("确定要删除这个点击项目吗？") },
            confirmButton = {
                TextButton(onClick = {
                    TaskScheduler.cancelTask(context, task)
                    tasks = removeProject(tasks, task.id)
                    TaskRepository.saveTasks(context, tasks)
                    expandedIds.remove(task.id)
                    deleteTarget = null
                }) { Text("删除", color = colors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    pendingAddStep?.let { pending ->
        val project = tasks.find { it.id == pending.projectId }
        DelayMsDialog(
            title = "点位延迟",
            initialMs = project?.let { defaultAddStepDelayMs(it) } ?: AppTiming.DEFAULT_INTERVAL_MS,
            onDismiss = { pendingAddStep = null },
            onConfirm = { delayMs ->
                val current = tasks.find { it.id == pending.projectId }
                if (current != null) {
                    val screen = ScreenUtils.getRealScreenSize(context)
                    val (xRatio, yRatio) = pixelsToRatio(pending.x, pending.y, screen.x, screen.y)
                    persistUpdated(
                        appendStep(current, xRatio, yRatio, delayMs, System.currentTimeMillis()),
                        current
                    )
                    expandedIds.add(current.id)
                }
                pendingAddStep = null
            }
        )
    }

    pendingDelayEdit?.let { pending ->
        DelayMsDialog(
            title = "点位延迟",
            initialMs = pending.initialMs,
            onDismiss = { pendingDelayEdit = null },
            onConfirm = { delayMs ->
                val current = tasks.find { it.id == pending.projectId }
                if (current != null) {
                    persistUpdated(updateStepDelay(current, pending.stepId, delayMs), current)
                }
                pendingDelayEdit = null
            }
        )
    }

    formTarget?.let { target ->
        ProjectFormDialog(
            target = target,
            onDismiss = { formTarget = null },
            onTestClick = { x, y ->
                val service = ClickAccessibilityService.instance
                if (service == null) {
                    Toast.makeText(context, "无障碍服务未开启", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "3秒后将在目标位置点击一次，请切到桌面查看", Toast.LENGTH_LONG).show()
                    testClickJob?.cancel()
                    testClickJob = scope.launch {
                        delay(AppTiming.TEST_CLICK_DELAY_MS)
                        service.performClicks(x, y, 1, AppTiming.TEST_CLICK_INTERVAL_MS)
                    }
                }
            },
            onConfirm = { hour, minute, second, delayOffsetMs, repeatCount, repeatGapMs, name ->
                when (target) {
                    is FormTarget.New -> {
                        val screen = ScreenUtils.getRealScreenSize(context)
                        val (xRatio, yRatio) = pixelsToRatio(target.x, target.y, screen.x, screen.y)
                        val created = createProject(
                            nowMs = System.currentTimeMillis(),
                            xRatio = xRatio,
                            yRatio = yRatio,
                            hour = hour,
                            minute = minute,
                            second = second,
                            delayOffsetMs = delayOffsetMs,
                            repeatCount = repeatCount,
                            repeatGapMs = repeatGapMs,
                            name = name
                        )
                        tasks = tasks + created
                        TaskRepository.saveTasks(context, tasks)
                        TaskScheduler.scheduleTask(context, created)
                    }
                    is FormTarget.Edit -> {
                        persistUpdated(
                            updateProjectSchedule(
                                project = target.task,
                                hour = hour,
                                minute = minute,
                                second = second,
                                delayOffsetMs = delayOffsetMs,
                                repeatCount = repeatCount,
                                repeatGapMs = repeatGapMs,
                                name = name
                            ),
                            target.task
                        )
                    }
                }
                formTarget = null
            }
        )
    }
}

/** 统一样式的菜单项，确保每一项高度、内边距完全一致 */
@Composable
fun AppMenuItem(text: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppSpacing.menuItemHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.md),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = AppTypeScale.menuSp.sp, color = colors.TextPrimary)
    }
}

@Composable
fun AppMenuDivider() {
    val colors = LocalAppColors.current
    HorizontalDivider(color = colors.Divider, thickness = 1.dp)
}

@Composable
fun LiveClock(nowTick: Long) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeText = remember(nowTick) { formatter.format(Date(nowTick)) }
    var analog by remember { mutableStateOf(AppPrefs.isAnalogClockEnabled(context)) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val clockFontSize = (maxWidth.value * AppTypeScale.clockFactor)
            .coerceIn(AppTypeScale.clockMinSp, AppTypeScale.clockMaxSp).sp
        val analogSize = (maxWidth.value * AppTypeScale.analogFactor)
            .coerceIn(AppTypeScale.analogMinDp, AppTypeScale.analogMaxDp).dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (analog) AppSpacing.clockTopAnalog else AppSpacing.clockTop,
                    bottom = AppSpacing.clockBottom
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = analog,
                contentAlignment = Alignment.Center,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(AppDuration.clockToggleMs)) +
                        scaleIn(
                            initialScale = 0.92f,
                            animationSpec = tween(AppDuration.clockToggleMs)
                        )).togetherWith(
                        fadeOut(animationSpec = tween(AppDuration.clockToggleMs)) +
                            scaleOut(
                                targetScale = 0.92f,
                                animationSpec = tween(AppDuration.clockToggleMs)
                            )
                    )
                },
                label = "clockMode"
            ) { showAnalog ->
                val toggleLabel = if (showAnalog) "切换为数字时钟" else "切换为钟表"
                val onToggle = {
                    val next = !analog
                    analog = next
                    AppPrefs.setAnalogClockEnabled(context, next)
                }
                if (showAnalog) {
                    AnalogClockFace(
                        nowTick = nowTick,
                        faceFill = colors.CardBackground,
                        accentColor = colors.PrimaryBlue,
                        lightColor = Color(0xFF6495ED),
                        secondHandColor = AppColorsDark.Danger,
                        modifier = Modifier
                            .size(analogSize)
                            .clickable(onClickLabel = toggleLabel, onClick = onToggle)
                    )
                } else {
                    Text(
                        text = timeText,
                        fontSize = clockFontSize,
                        fontWeight = FontWeight.Bold,
                        color = colors.PrimaryBlue,
                        modifier = Modifier.clickable(onClickLabel = toggleLabel, onClick = onToggle)
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppSpacing.clockCaptionGap))
            Text("实时", fontSize = 18.sp, color = colors.TextSecondary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(
    project: ClickProject,
    nowTick: Long,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTimeClick: () -> Unit,
    onLongPress: () -> Unit,
    onAddStep: () -> Unit,
    onStepClick: (ClickStep) -> Unit,
    onStepLongPress: (ClickStep) -> Unit,
    onStepDelayClick: (ClickStep) -> Unit
) {
    val colors = LocalAppColors.current
    val remaining = remember(
        nowTick, project.id, project.hour, project.minute, project.second, project.delayOffsetMs, project.enabled
    ) {
        TaskScheduler.remainingSeconds(project)
    }
    val isUrgent = project.enabled && remaining in 1..60
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(AppDuration.stateMs),
        label = "expandArrow"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.cardShape)
            .animateContentSize(tween(AppDuration.stateMs)),
        shape = AppShapes.cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onToggleExpand, onLongClick = onLongPress)
                    .padding(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(AppSpacing.iconSize)
                        .background(colors.LightBlueTint, AppShapes.iconCircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GpsFixed, contentDescription = null, tint = colors.PrimaryBlue)
                }

                Spacer(modifier = Modifier.width(AppSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        projectDisplayName(project.name),
                        fontSize = AppTypeScale.bodySp.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.TextPrimary
                    )
                    Text(
                        "%02d:%02d:%02d 开始".format(project.hour, project.minute, project.second),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = AppTypeScale.bodySp.sp,
                        color = colors.TextPrimary,
                        modifier = Modifier.combinedClickable(onClick = onTimeClick, onLongClick = onLongPress)
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.xxs))
                    Text(
                        projectCardSummary(project),
                        fontSize = AppTypeScale.captionSp.sp,
                        color = colors.TextSecondary
                    )
                    if (project.enabled) {
                        Text(
                            formatRemaining(remaining),
                            fontSize = AppTypeScale.captionSp.sp,
                            color = if (isUrgent) colors.Danger else colors.PrimaryBlue,
                            fontWeight = if (isUrgent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = colors.TextSecondary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(arrowRotation)
                    )
                    StatusPill(enabled = project.enabled, onToggle = onToggleEnabled)
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(AppDuration.stateMs)) + fadeIn(tween(AppDuration.stateMs)),
                exit = shrinkVertically(tween(AppDuration.stateMs)) + fadeOut(tween(AppDuration.stateMs))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppSpacing.md, end = AppSpacing.md, bottom = AppSpacing.sm)
                ) {
                    project.steps.forEachIndexed { index, step ->
                        StepRow(
                            index = index,
                            step = step,
                            showIncomingArrow = index > 0,
                            onClick = { onStepClick(step) },
                            onLongPress = { onStepLongPress(step) },
                            onDelayClick = { onStepDelayClick(step) }
                        )
                    }
                    TextButton(onClick = onAddStep) {
                        Text("添加点位")
                    }
                }
            }
        }
    }
}

@Composable
fun StepChainArrow() {
    val colors = LocalAppColors.current
    val lineColor = colors.PrimaryBlue.copy(alpha = 0.38f)
    Canvas(
        modifier = Modifier
            .width(18.dp)
            .height(14.dp)
    ) {
        val stroke = 4.5.dp.toPx()
        val cx = size.width / 2f
        val head = 6.dp.toPx()
        val shaftEnd = size.height - head + 1.dp.toPx()
        drawLine(
            color = lineColor,
            start = Offset(cx, 0f),
            end = Offset(cx, shaftEnd),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        val triangle = Path().apply {
            moveTo(cx, size.height)
            lineTo(cx - 6.dp.toPx(), shaftEnd - 0.5.dp.toPx())
            lineTo(cx + 6.dp.toPx(), shaftEnd - 0.5.dp.toPx())
            close()
        }
        drawPath(triangle, lineColor)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StepRow(
    index: Int,
    step: ClickStep,
    showIncomingArrow: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelayClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val editable = isStepDelayChipEditable(index)
    val infoStyle = TextStyle(
        fontSize = AppTypeScale.bodySp.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = AppTypeScale.bodySp.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = AppSpacing.xxs)
    ) {
        if (showIncomingArrow) {
            Box(
                modifier = Modifier.width(132.dp),
                contentAlignment = Alignment.Center
            ) {
                StepChainArrow()
            }
            Spacer(modifier = Modifier.height(AppSpacing.xxs))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(132.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .clip(AppShapes.pillShape)
                        .then(
                            if (editable) Modifier.clickable(onClick = onDelayClick) else Modifier
                        ),
                    color = colors.LightBlueTint,
                    shape = AppShapes.pillShape
                ) {
                    Text(
                        stepDelayChipLabel(index, step.delayFromPrevMs),
                        fontSize = AppTypeScale.menuSp.sp,
                        color = colors.PrimaryBlue,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeight = AppTypeScale.menuSp.sp
                        ),
                        modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AppSpacing.sm),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    "点位 ${index + 1}",
                    color = colors.TextPrimary,
                    style = infoStyle,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    stepPercentLabel(step.xRatio, step.yRatio),
                    color = colors.TextSecondary,
                    style = infoStyle,
                    modifier = Modifier
                        .padding(start = AppSpacing.sm)
                        .alignByBaseline()
                )
            }
        }
    }
}

@Composable
fun StatusPill(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    val targetBg = if (enabled) colors.PillOnBg else colors.PillOffBg
    val bg by animateColorAsState(targetBg, tween(AppDuration.stateMs), label = "pillBg")
    val textColor = if (enabled) colors.PillOnText else colors.PillOffText
    val label = if (enabled) "运行中" else "已停止"
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .clip(AppShapes.pillShape)
            .background(bg, AppShapes.pillShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = colors.PrimaryBlue),
                onClick = { onToggle(!enabled) }
            )
            .padding(horizontal = AppSpacing.pillHPad, vertical = AppSpacing.pillVPad),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = textColor,
            fontSize = AppTypeScale.menuSp.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PermissionDialog(
    accessibilityEnabled: Boolean,
    batteryOk: Boolean,
    exactAlarmOk: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShapes.dialogShape,
        containerColor = colors.Background,
        title = { Text("权限与设置") },
        text = {
            Column {
                PermissionRow(
                    ok = accessibilityEnabled,
                    title = "无障碍服务",
                    subtitle = "用于实现自动点击",
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                PermissionRow(
                    ok = exactAlarmOk,
                    title = "精确闹钟权限",
                    subtitle = "用于准时触发定时任务",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                PermissionRow(
                    ok = batteryOk,
                    title = "电池优化",
                    subtitle = "关闭限制避免任务被系统杀死",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun PermissionRow(ok: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ok) colors.Success else colors.Warning
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = AppTypeScale.menuSp.sp, color = colors.TextPrimary)
            Text(subtitle, fontSize = AppTypeScale.captionSp.sp, color = colors.TextSecondary)
        }
        if (!ok) {
            TextButton(onClick = onClick) { Text("去设置") }
        }
    }
}

@Composable
fun GroupDialog(onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShapes.dialogShape,
        title = { Text("加入群聊") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.qr_placeholder),
                    contentDescription = "群二维码",
                    modifier = Modifier.size(160.dp).clip(AppShapes.buttonShape)
                )
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                Text("群号：${AppInfo.GROUP_NUMBER}", fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Spacer(modifier = Modifier.height(AppSpacing.xxs))
                Text(
                    AppInfo.GROUP_INTRO,
                    color = colors.TextSecondary,
                    fontSize = AppTypeScale.captionSp.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShapes.dialogShape,
        containerColor = colors.Background,
        title = { Text("关于") },
        text = { Text(AppInfo.ABOUT_TEXT, color = colors.TextPrimary) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectActionSheet(
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.Background,
        shape = AppShapes.dialogShape
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)) {
            TextButton(onClick = onRename, modifier = Modifier.fillMaxWidth()) {
                Text("重命名", color = colors.TextPrimary)
            }
            TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Text("复制", color = colors.TextPrimary)
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("删除", color = colors.Danger)
            }
            Spacer(modifier = Modifier.height(AppSpacing.sm))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepActionSheet(
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.Background,
        shape = AppShapes.dialogShape
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)) {
            TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Text("复制", color = colors.TextPrimary)
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("删除", color = colors.Danger)
            }
            Spacer(modifier = Modifier.height(AppSpacing.sm))
        }
    }
}

@Composable
fun RenameProjectDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = LocalAppColors.current
    var nameText by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShapes.dialogShape,
        containerColor = colors.Background,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text("名称") },
                placeholder = { Text("项目") },
                shape = AppShapes.inputShape,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(nameText.trim()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun DelayMsDialog(
    title: String,
    initialMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val colors = LocalAppColors.current
    var delayText by remember { mutableStateOf(initialMs.toString()) }
    val parsed = parseRequiredDelayMs(delayText)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShapes.dialogShape,
        containerColor = colors.Background,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = delayText,
                onValueChange = { delayText = it.filter { c -> c.isDigit() } },
                label = { Text("延迟（毫秒）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = AppShapes.inputShape,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (parsed != null) onConfirm(parsed) },
                enabled = parsed != null
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ProjectFormDialog(
    target: FormTarget,
    onDismiss: () -> Unit,
    onTestClick: (x: Float, y: Float) -> Unit,
    onConfirm: (hour: Int, minute: Int, second: Int, delayOffsetMs: Int, repeatCount: Int, repeatGapMs: Long, name: String) -> Unit
) {
    val colors = LocalAppColors.current
    val configuration = LocalConfiguration.current
    val now = Calendar.getInstance()
    val initHour = when (target) {
        is FormTarget.New -> now.get(Calendar.HOUR_OF_DAY)
        is FormTarget.Edit -> target.task.hour
    }
    val initMinute = when (target) {
        is FormTarget.New -> now.get(Calendar.MINUTE)
        is FormTarget.Edit -> target.task.minute
    }
    val initSecond = when (target) {
        is FormTarget.New -> now.get(Calendar.SECOND)
        is FormTarget.Edit -> target.task.second
    }
    val initDelayOffset = when (target) {
        is FormTarget.New -> 0
        is FormTarget.Edit -> target.task.delayOffsetMs
    }
    val initialGapMs = when (target) {
        is FormTarget.New -> AppTiming.DEFAULT_INTERVAL_MS
        is FormTarget.Edit -> target.task.repeatGapMs
    }
    val initialCount = when (target) {
        is FormTarget.New -> 1
        is FormTarget.Edit -> target.task.repeatCount
    }
    val initialName = when (target) {
        is FormTarget.New -> ""
        is FormTarget.Edit -> target.task.name
    }

    var hour by remember { mutableIntStateOf(initHour) }
    var minute by remember { mutableIntStateOf(initMinute) }
    var second by remember { mutableIntStateOf(initSecond) }
    var gapText by remember { mutableStateOf(initialGapMs.toString()) }
    var countText by remember { mutableStateOf(initialCount.toString()) }
    var delayOffsetText by remember { mutableStateOf(initDelayOffset.toString()) }
    var nameText by remember { mutableStateOf(initialName) }
    var showTimeWheel by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.PrimaryBlue,
        unfocusedBorderColor = colors.DividerOnCard
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShapes.dialogShape,
        containerColor = colors.Background,
        title = { Text(if (target is FormTarget.Edit) "编辑项目" else "设置项目") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (configuration.screenHeightDp * 0.6f).dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (target is FormTarget.New) {
                    Text(
                        "点击位置：X=${target.x.toInt()}, Y=${target.y.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.PrimaryBlue
                    )
                    TextButton(onClick = { onTestClick(target.x, target.y) }) { Text("测试点击") }
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                }
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("名称（可选）") },
                    placeholder = { Text("项目") },
                    shape = AppShapes.inputShape,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                OutlinedButton(
                    onClick = { showTimeWheel = true },
                    shape = AppShapes.buttonShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始时间：%02d:%02d:%02d".format(hour, minute, second))
                }
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it.filter { c -> c.isDigit() } },
                    label = { Text("重复次数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = AppShapes.inputShape,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                OutlinedTextField(
                    value = gapText,
                    onValueChange = { gapText = it.filter { c -> c.isDigit() } },
                    label = { Text("轮间隔（毫秒，最小1ms）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = AppShapes.inputShape,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                OutlinedTextField(
                    value = delayOffsetText,
                    onValueChange = { input ->
                        delayOffsetText = input.filterIndexed { idx, c -> c.isDigit() || (c == '-' && idx == 0) }
                    },
                    label = { Text("延迟点击（ms，范围 ${AppTiming.DELAY_OFFSET_MIN} ~ ${AppTiming.DELAY_OFFSET_MAX}）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = AppShapes.inputShape,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val gap = gapText.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
                val count = countText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val delayOffset = (delayOffsetText.toIntOrNull() ?: 0)
                    .coerceIn(AppTiming.DELAY_OFFSET_MIN, AppTiming.DELAY_OFFSET_MAX)
                onConfirm(hour, minute, second, delayOffset, count, gap, nameText.trim())
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showTimeWheel) {
        TimeWheelPickerDialog(
            initialHour = hour, initialMinute = minute, initialSecond = second,
            onDismiss = { showTimeWheel = false },
            onConfirm = { h, m, s ->
                hour = h; minute = m; second = s
                showTimeWheel = false
            }
        )
    }
}
