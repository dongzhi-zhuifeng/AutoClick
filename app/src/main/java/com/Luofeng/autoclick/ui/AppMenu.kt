package com.Luofeng.autoclick.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Luofeng.autoclick.AppSpacing
import com.Luofeng.autoclick.AppTypeScale
import com.Luofeng.autoclick.LocalAppColors

/** 顶栏下拉菜单的统一菜单项与分隔线。 */
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
