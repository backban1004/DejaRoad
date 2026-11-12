package com.dejaroad.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * 내비가 전체 화면을 덮거나 다른 앱을 쓰는 중에도 차량 값을 계속 쌓도록 세션을 돌리는 포그라운드 서비스.
 * (백그라운드 앱은 차량 리스너 이벤트가 멈췄다가 몰아서 들어온다)
 */
class RoadService : Service() {

  private val session: RoadSession
    get() = (application as DejaRoadApp).session

  override fun onCreate() {
    super.onCreate()
    runCatching { startInForeground() }.onFailure { Log.e(TAG, "포그라운드 시작 실패", it) }
    session.start()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

  override fun onDestroy() {
    session.stop()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun startInForeground() {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL, "길 안내 도우미", NotificationManager.IMPORTANCE_LOW))
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val notification =
      NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_alt_route)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("어려운 곳 1km 전에 소리로 알려 드려요")
        .setContentIntent(open)
        .setOngoing(true)
        .build()
    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType())
  }

  /** 받은 권한에 맞춰 포그라운드 종류를 정한다 (없는 권한의 종류를 넣으면 시작이 거부된다) */
  private fun serviceType(): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
    var type = 0
    if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    return type
  }

  private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

  companion object {
    private const val TAG = "RoadService"
    private const val CHANNEL = "tracking"
    private const val NOTIFICATION_ID = 1

    fun start(context: Context) {
      runCatching { ContextCompat.startForegroundService(context, Intent(context, RoadService::class.java)) }
        .onFailure { Log.e(TAG, "서비스 시작 요청 실패", it) }
    }
  }
}
