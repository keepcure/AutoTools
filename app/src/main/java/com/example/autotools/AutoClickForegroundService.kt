package com.example.autotools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log

class AutoClickForegroundService : Service() {
    @Volatile
    private var loopRunning = false
    private var workerThread: Thread? = null
    private var nextButtonTurn = true
    private var sequenceIndex = 0
    var count = 1
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val TAG = "AutoTools"
        const val ACTION_TICK = "com.example.autotools.ACTION_CLICK_TICK"
        const val EXTRA_NEXT_BUTTON = "next_button"
        const val EXTRA_ACTION = "action"
        const val ACTION_NEXT = 0
        const val ACTION_FAST_FORWARD = 1
        private const val CHANNEL_ID = "auto_click"
        private const val NOTIFICATION_ID = 1001
        private const val CLICK_INTERVAL_KEY = "click_interval_ms"
        private const val MIN_CLICK_INTERVAL_MS = 500L
        private const val MAX_CLICK_INTERVAL_MS = 3000L
        private const val DEFAULT_CLICK_INTERVAL_MS = 1500L
        private const val CLICK_SEQUENCE_KEY = "click_sequence"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startWorker()
    }

    private fun startWorker() {
        if (loopRunning) return
        loopRunning = true
        workerThread = Thread {
            // 设置线程优先级：前台线程优先级，对抗ROM调度压制
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
            Log.i(TAG, "worker thread start")
            while (loopRunning) {
                try {
                    Log.i(TAG, "onStartCommand${count++}")
                    val sequence = if (getSharedPreferences("auto_tools", MODE_PRIVATE)
                            .getInt(CLICK_SEQUENCE_KEY, 0) == 1) {
                        intArrayOf(ACTION_NEXT, ACTION_FAST_FORWARD, ACTION_FAST_FORWARD)
                    } else {
                        intArrayOf(ACTION_NEXT, ACTION_FAST_FORWARD)
                    }
                    val action = sequence[sequenceIndex % sequence.size]
                    sequenceIndex++
                    sendTick(action)
                    nextButtonTurn = !nextButtonTurn
                } catch (ex: Exception) {
                    Log.e(TAG, "loop error", ex)
                }
                SystemClock.sleep(readClickInterval())
            }
            Log.w(TAG, "worker thread exit, loopRunning=$loopRunning")
        }
        workerThread?.start()
    }

    private fun readClickInterval(): Long {
        return getSharedPreferences("auto_tools", MODE_PRIVATE)
            .getInt(CLICK_INTERVAL_KEY, DEFAULT_CLICK_INTERVAL_MS.toInt())
            .toLong()
            .coerceIn(MIN_CLICK_INTERVAL_MS, MAX_CLICK_INTERVAL_MS)
    }

    private fun sendTick(action: Int) {
        mainHandler.post {
            Log.i(TAG, "sending action tick: $action")
            sendBroadcast(
                Intent(ACTION_TICK)
                    .setPackage(packageName)
                    .putExtra(EXTRA_ACTION, action)
                    .putExtra(EXTRA_NEXT_BUTTON, action == ACTION_NEXT)
            )
        }
    }

    override fun onDestroy() {
        loopRunning = false
        workerThread?.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.foreground_service_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT // ✅必须DEFAULT！状态栏出图标
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.foreground_service_notification))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
}
