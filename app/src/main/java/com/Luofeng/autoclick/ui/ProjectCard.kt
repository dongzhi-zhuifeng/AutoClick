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

/** 项目卡片：收缩摘要、展开点位链、运行中药丸。 */
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
