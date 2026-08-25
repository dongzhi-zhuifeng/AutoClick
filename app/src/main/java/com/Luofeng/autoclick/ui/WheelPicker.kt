package com.Luofeng.autoclick.ui

import com.Luofeng.autoclick.LocalAppColors
import com.Luofeng.autoclick.AppShapes


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** 时分秒滚轮选择器。 */
/** 单列滚轮选择器：支持无限循环滚动（loop=true时可以一直转，不会卡在两端） */
/** 三列滚轮组合弹窗：时/分/秒（不再需要毫秒），全部支持无限循环滚动 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    range: IntRange,
    initialValue: Int,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
    visibleCount: Int = 5,
    loop: Boolean = true,
    formatter: (Int) -> String = { "%02d".format(it) },
    onValueChange: (Int) -> Unit
) {
    val colors = LocalAppColors.current
    val rangeSize = range.count()
    // 循环模式下，虚拟生成大量重复的数据(1000轮)，让用户几乎感觉不到边界，可以一直转
    val loopMultiplier = if (loop) 1000 else 1
    val totalCount = rangeSize * loopMultiplier

    val initialIndex = if (loop) {
        (rangeSize * (loopMultiplier / 2)) + (initialValue - range.first)
    } else {
        (initialValue - range.first).coerceIn(0, rangeSize - 1)
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)

    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) return@derivedStateOf initialIndex
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val centerItem = layoutInfo.visibleItemsInfo.minByOrNull {
                abs((it.offset + it.size / 2) - viewportCenter)
            }
            centerItem?.index ?: initialIndex
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val idx = centerIndex.coerceIn(0, totalCount - 1)
            val value = range.first + (idx % rangeSize)
            onValueChange(value)
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
            .width(60.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxSize()
        ) {
            items(count = totalCount) { index ->
                val value = range.first + (index % rangeSize)
                val isSelected = index == centerIndex
                Box(
                    modifier = Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatter(value),
                        fontSize = if (isSelected) 20.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) colors.PrimaryBlue else colors.TextSecondary
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(colors.PrimaryBlue.copy(alpha = 0.1f))
        )
    }
}

@Composable
fun TimeWheelPickerDialog(
    initialHour: Int,
    initialMinute: Int,
    initialSecond: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int, second: Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    var second by remember { mutableIntStateOf(initialSecond) }
    val colors = LocalAppColors.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShapes.dialogShape,
        containerColor = colors.Background,
        title = { Text("选择开始时间") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("时", fontSize = 12.sp, color = colors.TextSecondary)
                    WheelPicker(range = 0..23, initialValue = hour, loop = true) { hour = it }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("分", fontSize = 12.sp, color = colors.TextSecondary)
                    WheelPicker(range = 0..59, initialValue = minute, loop = true) { minute = it }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("秒", fontSize = 12.sp, color = colors.TextSecondary)
                    WheelPicker(range = 0..59, initialValue = second, loop = true) { second = it }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(hour, minute, second) }) {
                Text("确定")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}