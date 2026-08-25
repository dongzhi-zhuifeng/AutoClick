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

/** 首页实时时钟：数字与表盘切换。 */
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
