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

/** 关于、加群、重命名、延迟、项目表单与长按动作表。 */
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
