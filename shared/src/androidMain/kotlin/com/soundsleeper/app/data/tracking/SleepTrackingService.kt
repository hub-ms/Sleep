package com.soundsleeper.app.data.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.russhwolf.settings.ExperimentalSettingsApi
import com.soundsleeper.app.MainActivity
import com.soundsleeper.app.R
import com.soundsleeper.app.platform.AndroidTrackingManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.ExperimentalTime

@UnstableApi
@ExperimentalMaterial3Api
@ExperimentalTime
@ExperimentalSettingsApi
@ExperimentalCoroutinesApi
@InternalVoyagerApi
/**
 * 수면 측정을 앱이 백그라운드에 있거나 화면이 꺼져도 계속 진행시키는 포그라운드 서비스.
 * 실제 센서/분석/저장 로직은 전부 [AndroidTrackingManager]에 위임하고, 이 클래스는
 * Android 서비스 생명주기(알림, 웨이크락, 시작/종료 Intent 라우팅)만 책임진다.
 */
class SleepTrackingService : Service(), KoinComponent {
    private val trackingManager: AndroidTrackingManager by inject()
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_FINISH = "ACTION_FINISH"
        const val ACTION_DISCARD = "ACTION_DISCARD"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_DURATION_MILLIS = "EXTRA_DURATION_MILLIS"
        const val EXTRA_MUSIC_NAME = "EXTRA_MUSIC_NAME"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "sleep_tracking_channel"
    }

    /** 서비스가 처음 생성될 때 알림 채널을 만들고, 트래킹 매니저에게 알림 갱신/종료 콜백을 등록한다. */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        trackingManager.attachCallbacks(
            onNotificationUpdate = { text ->
                notificationManager.notify(NOTIFICATION_ID, createNotification(text))
            },
            onRequestStopForeground = {
                Log.d("SleepTrackingService", "onRequestStopForeground 콜백 호출됨")  // ⭐ 추가
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        )
    }

    /** 바인딩을 지원하지 않는 서비스이므로 항상 null을 반환한다. */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 서비스로 들어오는 모든 Intent(측정 시작/종료/취소, 또는 시스템에 의한 재시작)를 처리하는 입구.
     * 실제 동작은 [AndroidTrackingManager.performStart]/`performFinish`/`performDiscard`에 위임한다.
     */
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent == null) {
            // 시스템이 서비스를 죽였다가 재시작한 경우(START_STICKY) — 측정 중이었다면
            // 포그라운드 상태와 웨이크락을 복구한다. intent가 없으므로 액션 분기는 할 수 없다.
            if (trackingManager.trackingState.value.isTracking) {
                if (!hasRecordAudioPermission()) {
                    Log.e("SleepTrackingService", "RECORD_AUDIO 권한 없음 — 서비스 재시작 시 포그라운드 전환 불가")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, createNotification("수면 측정 중.."))
                acquireWakeLock()
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                // 서비스가 microphone 타입 포그라운드로 선언되어 있어(AndroidManifest.xml),
                // RECORD_AUDIO 권한 없이 startForeground()를 호출하면 SecurityException으로 크래시한다.
                // 온보딩 이후 사용자가 권한을 회수한 경우를 대비해 반드시 먼저 확인한다.
                if (!hasRecordAudioPermission()) {
                    Log.e("SleepTrackingService", "RECORD_AUDIO 권한 없음 — 수면 측정을 시작할 수 없습니다")
                    trackingManager.notifyPermissionDenied()
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForeground(NOTIFICATION_ID, createNotification("수면 측정 중.."))
                Log.d("SleepTrackingService", "startForeground 완료")
                acquireWakeLock()

                if (!trackingManager.trackingState.value.isTracking) {
                    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_STICKY
                    val durationMillis = intent.getLongExtra(EXTRA_DURATION_MILLIS, 480L * 60 * 1000)
                    val musicName = intent.getStringExtra(EXTRA_MUSIC_NAME)
                    trackingManager.performStart(sessionId, durationMillis, musicName)
                }
            }
            ACTION_FINISH -> {
                // ✅ intent extras 우선, 없으면 현재 State에서 fallback
                val currentState = trackingManager.trackingState.value
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: currentState.sessionId ?: return START_STICKY
                val musicName = intent.getStringExtra(EXTRA_MUSIC_NAME) ?: currentState.musicName
                trackingManager.performFinish(sessionId, musicName)
            }
            ACTION_DISCARD -> {
                val currentState = trackingManager.trackingState.value
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: currentState.sessionId ?: return START_STICKY
                val musicName = intent.getStringExtra(EXTRA_MUSIC_NAME) ?: currentState.musicName
                trackingManager.performDiscard(sessionId, musicName)
            }
        }
        return START_STICKY
    }

    /** 서비스 종료 시 웨이크락을 반드시 해제한다(안 하면 배터리를 계속 붙잡는다). */
    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
        Log.d("SleepTrackingService", "onDestroy 호출됨, pid=${android.os.Process.myPid()}")
    }

    /** 사용자가 앱 task를 스와이프로 제거했을 때, 측정 중이 아니면 서비스도 함께 종료한다. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!trackingManager.trackingState.value.isTracking) {
            stopSelf()
        }
    }

    /** 화면이 꺼져도 측정이 멈추지 않도록 부분 웨이크락을 잡는다(최대 8시간, 이미 잡혀 있으면 무시). */
    private fun acquireWakeLock() {
        if(wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SleepTrackingService:WakeLock"
        ).also { it.acquire(8*60*60*1000L) }
    }

    /** 잡고 있던 웨이크락을 해제하고 참조를 비운다. */
    private fun releaseWakeLock() {
        if(wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    /** 마이크 권한이 있는지 확인한다 — 이 서비스가 microphone 타입 포그라운드로 선언되어 있어 필수. */
    private fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    /** "수면 측정" 알림 채널을 생성한다(중요도 낮음 — 소리/진동 없이 조용히 상태만 표시). */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "수면 측정",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "수면 측정 진행 상태"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 측정 진행 중 계속 띄워 두는 상시 알림을 만든다. 탭하면 앱을 열고, 종료 버튼은 ACTION_FINISH를 보낸다. */
    private fun createNotification(contentText: String): Notification {
        val openPending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val finishPending = PendingIntent.getService(
            this,
            1,
            Intent(this, SleepTrackingService::class.java).apply {
                action = ACTION_FINISH
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sleep)
            .setContentTitle("수면 측정")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openPending)
            .build()
    }
}