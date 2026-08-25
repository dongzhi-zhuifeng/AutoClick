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

/** 点位行：延迟芯片、坐标百分比、点间箭头。 */
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
