package com.autoclicker.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingControlService : Service() {

    companion object {
        var instance: FloatingControlService? = null
            private set
    }

    private lateinit var windowManager: WindowManager
    private var controlView: View? = null
    private var crosshairView: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isClicking = false
    private var clickCount = 0
    private var targetCount = 0
    private var clickInterval = 50L
    private var targetX = 0f
    private var targetY = 0f
    private var isSelectingTarget = false

    // 控制按钮位置
    private var btnX = 0
    private var btnY = 300

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        showControlPanel()
    }

    override fun onDestroy() {
        instance = null
        isClicking = false
        handler.removeCallbacksAndMessages(null)
        removeControlView()
        removeCrosshair()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            "clicker_channel", "连点器服务",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "clicker_channel")
            .setContentTitle("连点器运行中")
            .setContentText("点击停止或返回主界面管理")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun showControlPanel() {
        if (controlView != null) return

        val inflater = LayoutInflater.from(this)
        controlView = inflater.inflate(R.layout.floating_control, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = btnX
            y = btnY
        }

        // 拖拽支持
        setupDrag(controlView!!, params)

        windowManager.addView(controlView, params)
        updateControlUI()
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var startTouchX = 0f
        var startTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startTouchX
                    val dy = event.rawY - startTouchY
                    if (dx * dx + dy * dy > 400) isDragging = true
                    if (isDragging) {
                        params.x = (startX + dx).toInt()
                        params.y = (startY + dy).toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        v.performClick()
                    }
                    true
                }
            }
        }
    }

    private fun updateControlUI() {
        val view = controlView ?: return
        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        val btnStart = view.findViewById<View>(R.id.btnStart)
        val btnTarget = view.findViewById<View>(R.id.btnTarget)
        val btnStop = view.findViewById<View>(R.id.btnStop)
        val btnAdd = view.findViewById<View>(R.id.btnAdd)
        val btnSub = view.findViewById<View>(R.id.btnSub)

        if (isSelectingTarget) {
            tvStatus.text = "点击屏幕选取目标"
            tvStatus.setTextColor(Color.YELLOW)
        } else if (isClicking) {
            tvStatus.text = "点击中 $clickCount/$targetCount"
            tvStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else if (targetX > 0 && targetY > 0) {
            tvStatus.text = "目标: ${targetX.toInt()},${targetY.toInt()} ${clickInterval}ms"
            tvStatus.setTextColor(Color.WHITE)
        } else {
            tvStatus.text = "请先选取目标位置"
            tvStatus.setTextColor(Color.parseColor("#FF9800"))
        }

        btnTarget.setOnClickListener {
            isSelectingTarget = true
            updateControlUI()
            showCrosshair()
        }

        btnStart.setOnClickListener {
            if (!ClickAccessibilityService.isRunning) {
                tvStatus.text = "请先开启无障碍服务"
                tvStatus.setTextColor(Color.RED)
                return@setOnClickListener
            }
            if (targetX <= 0 || targetY <= 0) {
                tvStatus.text = "请先选取目标位置"
                tvStatus.setTextColor(Color.RED)
                return@setOnClickListener
            }
            startClicking()
        }

        btnStop.setOnClickListener {
            stopClicking()
        }

        btnAdd.setOnClickListener {
            clickInterval = (clickInterval - 10).coerceAtLeast(10)
            updateControlUI()
        }

        btnSub.setOnClickListener {
            clickInterval = (clickInterval + 10).coerceAtMost(5000)
            updateControlUI()
        }
    }

    private fun showCrosshair() {
        removeCrosshair()
        crosshairView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        crosshairView!!.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && isSelectingTarget) {
                targetX = event.rawX
                targetY = event.rawY
                isSelectingTarget = false
                removeCrosshair()
                updateControlUI()
                true
            } else {
                false
            }
        }

        windowManager.addView(crosshairView, params)
    }

    private fun removeCrosshair() {
        crosshairView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        crosshairView = null
    }

    private fun startClicking() {
        if (isClicking) return
        isClicking = true
        clickCount = 0
        targetCount = Int.MAX_VALUE // 无限次，手动停止
        updateControlUI()
        doClick()
    }

    private fun doClick() {
        if (!isClicking) return

        ClickAccessibilityService.instance?.performClick(targetX, targetY) {
            handler.post {
                clickCount++
                updateControlUI()
                if (isClicking) {
                    handler.postDelayed({ doClick() }, clickInterval)
                }
            }
        }
    }

    private fun stopClicking() {
        isClicking = false
        handler.removeCallbacksAndMessages(null)
        updateControlUI()
    }

    private fun removeControlView() {
        controlView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        controlView = null
    }
}
