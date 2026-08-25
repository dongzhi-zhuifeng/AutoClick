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

/** 检测无障碍 / 电池优化 / 精确闹钟，并展示权限对话框。 */
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
