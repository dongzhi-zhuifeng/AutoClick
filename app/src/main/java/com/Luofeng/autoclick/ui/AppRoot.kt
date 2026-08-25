package com.Luofeng.autoclick.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.Luofeng.autoclick.AppColorsDark
import com.Luofeng.autoclick.AppDuration
import com.Luofeng.autoclick.AppInfo
import com.Luofeng.autoclick.AppShapes
import com.Luofeng.autoclick.AppSpacing
import com.Luofeng.autoclick.AppTiming
import com.Luofeng.autoclick.AppTypeScale
import com.Luofeng.autoclick.LocalAppColors
import com.Luofeng.autoclick.R
import com.Luofeng.autoclick.ScreenUtils
import com.Luofeng.autoclick.click.ClickAccessibilityService
import com.Luofeng.autoclick.data.AppPrefs
import com.Luofeng.autoclick.data.TaskRepository
import com.Luofeng.autoclick.domain.ClickProject
import com.Luofeng.autoclick.domain.ClickStep
import com.Luofeng.autoclick.domain.appendStep
import com.Luofeng.autoclick.domain.applyEnabledToggle
import com.Luofeng.autoclick.domain.copyProject
import com.Luofeng.autoclick.domain.copyStepAt
import com.Luofeng.autoclick.domain.createProject
import com.Luofeng.autoclick.domain.defaultAddStepDelayMs
import com.Luofeng.autoclick.domain.deleteStepAt
import com.Luofeng.autoclick.domain.insertProjectAfter
import com.Luofeng.autoclick.domain.isStepDelayChipEditable
import com.Luofeng.autoclick.domain.parseRequiredDelayMs
import com.Luofeng.autoclick.domain.pixelsToRatio
import com.Luofeng.autoclick.domain.projectCardSummary
import com.Luofeng.autoclick.domain.projectDisplayName
import com.Luofeng.autoclick.domain.removeProject
import com.Luofeng.autoclick.domain.renameProject
import com.Luofeng.autoclick.domain.replaceProject
import com.Luofeng.autoclick.domain.shouldPromptAddStepDelay
import com.Luofeng.autoclick.domain.stepDelayChipLabel
import com.Luofeng.autoclick.domain.stepPercentLabel
import com.Luofeng.autoclick.domain.updateProjectSchedule
import com.Luofeng.autoclick.domain.updateStepCoordinates
import com.Luofeng.autoclick.domain.updateStepDelay
import com.Luofeng.autoclick.overlay.OverlayPointPickerService
import com.Luofeng.autoclick.overlay.PickerPurpose
import com.Luofeng.autoclick.overlay.PointPickerBus
import com.Luofeng.autoclick.schedule.TaskScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 应用根界面：列表状态、保存调度、选点回写。 */
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
