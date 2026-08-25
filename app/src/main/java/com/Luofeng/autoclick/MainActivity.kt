package com.Luofeng.autoclick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.Luofeng.autoclick.schedule.TaskScheduler
import com.Luofeng.autoclick.ui.AppRoot
import com.Luofeng.autoclick.ui.theme.AutoClickTheme

/** 应用入口：恢复闹钟并挂上 Compose 根界面。 */
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
