package com.Luofeng.autoclick.overlay

import com.Luofeng.autoclick.R
import com.Luofeng.autoclick.AppLog
import com.Luofeng.autoclick.MainActivity
import com.Luofeng.autoclick.ScreenUtils


import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView

/** 悬浮选点：拖动准星确认屏幕坐标。 */
internal data class OverlayBackKeyDecision(val consume: Boolean, val shouldStop: Boolean)

internal fun overlayBackKeyDecision(keyCode: Int, action: Int): OverlayBackKeyDecision {
    if (keyCode != KeyEvent.KEYCODE_BACK) {
        return OverlayBackKeyDecision(consume = false, shouldStop = false)
    }
    return when (action) {
        KeyEvent.ACTION_DOWN -> OverlayBackKeyDecision(consume = true, shouldStop = false)
        KeyEvent.ACTION_UP -> OverlayBackKeyDecision(consume = true, shouldStop = true)
        else -> OverlayBackKeyDecision(consume = false, shouldStop = false)
    }
}

internal fun overlayMarkerOrigin(centerX: Float, centerY: Float, sizePx: Int): Pair<Int, Int> {
    return (centerX - sizePx / 2f).toInt() to (centerY - sizePx / 2f).toInt()
}

internal fun overlayMarkerCenter(originX: Int, originY: Int, sizePx: Int): Pair<Float, Float> {
    val half = sizePx / 2f
    return originX + half to originY + half
}

internal fun overlayCoordsOrigin(
    markerCenterX: Float,
    markerCenterY: Float,
    markerSizePx: Int,
    coordsW: Int,
    coordsH: Int,
    gapPx: Int
): Pair<Int, Int> {
    val markerRight = markerCenterX + markerSizePx / 2f
    val markerTop = markerCenterY - markerSizePx / 2f
    val x = (markerRight - gapPx).toInt()
    val y = (markerTop - coordsH - gapPx).toInt()
    return x to y
}

internal fun overlayConfirmOrigin(
    markerCenterX: Float,
    markerCenterY: Float,
    markerSizePx: Int,
    confirmW: Int,
    confirmH: Int,
    gapPx: Int
): Pair<Int, Int> {
    val x = (markerCenterX - confirmW / 2f).toInt()
    val y = (markerCenterY + markerSizePx / 2f + gapPx).toInt()
    return x to y
}

internal fun overlayClampOrigin(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    screenW: Int,
    screenH: Int
): Pair<Int, Int> {
    val maxX = (screenW - width).coerceAtLeast(0)
    val maxY = (screenH - height).coerceAtLeast(0)
    return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
}

class OverlayPointPickerService : Service() {

    private lateinit var windowManager: WindowManager
    private var coordsView: TextView? = null
    private var confirmView: View? = null
    private var markerView: View? = null
    private var coordsParams: WindowManager.LayoutParams? = null
    private var confirmParams: WindowManager.LayoutParams? = null
    private var markerParams: WindowManager.LayoutParams? = null
    private var markerSizePx: Int = 0
    private var hudGapPx: Int = 0
    private var screenW: Int = 0
    private var screenH: Int = 0
    private var pickerPurpose: PickerPurpose = PickerPurpose.NewProject

    private var currentX = 0f
    private var currentY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pickerPurpose = parsePickerPurpose(intent)

        val initialX = intent?.getFloatExtra("initialX", -1f) ?: -1f
        val initialY = intent?.getFloatExtra("initialY", -1f) ?: -1f

        if (markerView == null) {
            try {
                setupOverlay(initialX, initialY)
            } catch (t: Exception) {
                AppLog.e("event=picker_setup_failed", t)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun parsePickerPurpose(intent: Intent?): PickerPurpose {
        val purpose = intent?.getStringExtra("purpose") ?: "new"
        val projectId = intent?.getLongExtra("projectId", -1L) ?: -1L
        val stepId = intent?.getLongExtra("stepId", -1L) ?: -1L
        return when (purpose) {
            "add" -> PickerPurpose.AddStep(projectId)
            "edit" -> PickerPurpose.EditStep(projectId, stepId)
            else -> PickerPurpose.NewProject
        }
    }

    private fun barOverlayFlags(): Int {
        return WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    }

    private fun markerOverlayFlags(): Int {
        return barOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    private fun confirmFlags(hidden: Boolean): Int {
        return hudFlags(markerOverlayFlags(), hidden)
    }

    private fun coordsFlags(hidden: Boolean): Int {
        return hudFlags(barOverlayFlags(), hidden)
    }

    private fun hudFlags(base: Int, hidden: Boolean): Int {
        return if (hidden) base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else base
    }

    private fun wrapParams(flags: Int, x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            ScreenUtils.overlayWindowType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            ScreenUtils.applyDisplayCutout(this)
        }
    }

    private fun setupOverlay(initialX: Float, initialY: Float) {
        val root = LayoutInflater.from(this).inflate(R.layout.overlay_point_picker, null) as ViewGroup
        val coords = root.findViewById<TextView>(R.id.tvCoords)
        val confirm = root.findViewById<View>(R.id.btnConfirm)
        val marker = root.findViewById<View>(R.id.targetMarker)
        root.removeView(coords)
        root.removeView(confirm)
        root.removeView(marker)

        val screen = ScreenUtils.getRealScreenSize(this)
        screenW = screen.x
        screenH = screen.y
        markerSizePx = resources.getDimensionPixelSize(R.dimen.picker_marker_size)
        hudGapPx = resources.getDimensionPixelSize(R.dimen.overlay_hud_gap)
        currentX = if (initialX >= 0f) initialX else screen.x / 2f
        currentY = if (initialY >= 0f) initialY else screen.y / 2f
        val (originX, originY) = overlayMarkerOrigin(currentX, currentY, markerSizePx)

        val markerLp = WindowManager.LayoutParams(
            markerSizePx,
            markerSizePx,
            ScreenUtils.overlayWindowType(),
            markerOverlayFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = originX
            y = originY
            ScreenUtils.applyDisplayCutout(this)
        }

        updateCoordsLabel(coords)
        val coordsLp = wrapParams(coordsFlags(hidden = false), 0, 0)
        val confirmLp = wrapParams(confirmFlags(hidden = false), 0, 0)
        positionHud(coords, coordsLp, confirm, confirmLp)

        windowManager.addView(coords, coordsLp)
        windowManager.addView(confirm, confirmLp)
        windowManager.addView(marker, markerLp)
        coordsView = coords
        confirmView = confirm
        markerView = marker
        coordsParams = coordsLp
        confirmParams = confirmLp
        markerParams = markerLp

        coords.isFocusableInTouchMode = true
        coords.requestFocus()
        coords.setOnKeyListener { _, keyCode, event ->
            val decision = overlayBackKeyDecision(keyCode, event.action)
            if (decision.shouldStop) stopSelf()
            decision.consume
        }

        setupDrag(marker, markerLp, coords, coordsLp, confirm, confirmLp)

        confirm.setOnClickListener {
            PointPickerBus.pickedPoint.value = PointPickerBus.PickedPoint(currentX, currentY, pickerPurpose)

            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(openIntent)

            stopSelf()
        }
    }

    private fun measureWrap(view: View): Pair<Int, Int> {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return view.measuredWidth.coerceAtLeast(1) to view.measuredHeight.coerceAtLeast(1)
    }

    private fun updateCoordsLabel(target: TextView? = coordsView) {
        target?.text = "%d, %d".format(currentX.toInt(), currentY.toInt())
    }

    private fun positionHud(
        coords: View,
        coordsLp: WindowManager.LayoutParams,
        confirm: View,
        confirmLp: WindowManager.LayoutParams
    ) {
        val (coordsW, coordsH) = measureWrap(coords)
        val (confirmW, confirmH) = measureWrap(confirm)
        val (rawCoordsX, rawCoordsY) = overlayCoordsOrigin(
            currentX, currentY, markerSizePx, coordsW, coordsH, hudGapPx
        )
        val (cox, coy) = overlayClampOrigin(rawCoordsX, rawCoordsY, coordsW, coordsH, screenW, screenH)
        coordsLp.x = cox
        coordsLp.y = coy
        val (rawConfirmX, rawConfirmY) = overlayConfirmOrigin(
            currentX, currentY, markerSizePx, confirmW, confirmH, hudGapPx
        )
        val (cnx, cny) = overlayClampOrigin(rawConfirmX, rawConfirmY, confirmW, confirmH, screenW, screenH)
        confirmLp.x = cnx
        confirmLp.y = cny
    }

    private fun applyHudLayout(
        coords: View,
        coordsLp: WindowManager.LayoutParams,
        confirm: View,
        confirmLp: WindowManager.LayoutParams
    ) {
        positionHud(coords, coordsLp, confirm, confirmLp)
        try {
            if (coords.isAttachedToWindow) windowManager.updateViewLayout(coords, coordsLp)
            if (confirm.isAttachedToWindow) windowManager.updateViewLayout(confirm, confirmLp)
        } catch (t: Exception) {
            AppLog.w("event=picker_hud_move_failed", t)
        }
    }

    private fun setHudHidden(
        view: View,
        layoutParams: WindowManager.LayoutParams,
        shownFlags: Int,
        hidden: Boolean
    ) {
        view.alpha = if (hidden) 0f else 1f
        layoutParams.flags = hudFlags(shownFlags, hidden)
        try {
            if (view.isAttachedToWindow) windowManager.updateViewLayout(view, layoutParams)
        } catch (t: Exception) {
            AppLog.w("event=picker_hud_hide_failed", t)
        }
    }

    private fun setupDrag(
        marker: View,
        params: WindowManager.LayoutParams,
        coords: View,
        coordsLp: WindowManager.LayoutParams,
        confirm: View,
        confirmLp: WindowManager.LayoutParams
    ) {
        var startRawX = 0f
        var startRawY = 0f
        var startParamX = 0
        var startParamY = 0
        var dragging = false

        marker.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startParamX = params.x
                    startParamY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) {
                        dragging = true
                        setHudHidden(coords, coordsLp, barOverlayFlags(), true)
                        setHudHidden(confirm, confirmLp, markerOverlayFlags(), true)
                    }
                    params.x = (startParamX + event.rawX - startRawX).toInt()
                    params.y = (startParamY + event.rawY - startRawY).toInt()
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (t: Exception) {
                        AppLog.w("event=picker_marker_move_failed", t)
                    }
                    val (cx, cy) = overlayMarkerCenter(params.x, params.y, markerSizePx)
                    currentX = cx
                    currentY = cy
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        updateCoordsLabel()
                        applyHudLayout(coords, coordsLp, confirm, confirmLp)
                        setHudHidden(coords, coordsLp, barOverlayFlags(), false)
                        setHudHidden(confirm, confirmLp, markerOverlayFlags(), false)
                    }
                    view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayView(coordsView)
        removeOverlayView(confirmView)
        removeOverlayView(markerView)
        coordsView = null
        confirmView = null
        markerView = null
        coordsParams = null
        confirmParams = null
        markerParams = null
    }

    private fun removeOverlayView(view: View?) {
        if (view == null) return
        try {
            windowManager.removeView(view)
        } catch (t: Exception) {
            AppLog.w("event=picker_remove_failed", t)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
