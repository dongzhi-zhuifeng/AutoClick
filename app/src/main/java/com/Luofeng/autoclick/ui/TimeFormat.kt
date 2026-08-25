package com.Luofeng.autoclick.ui

/** 把剩余秒数格式化成列表上的倒计时文案。 */
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
