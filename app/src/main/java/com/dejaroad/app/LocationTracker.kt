package com.dejaroad.app

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * 기록을 남긴 곳의 위치. 기본은 Pleos FusedLocation SDK이고,
 * 쓸 수 없거나 [FUSED_FRESH_MS] 동안 새 위치가 없으면 Android 위치로 채운다.
 */
class LocationTracker(context: Context) {

  private val manager = context.getSystemService(LocationManager::class.java)
  private val fused = FusedLocation(context.applicationContext)

  @Volatile var location: Location? = null
    private set

  private var fusedStarted = false
  @Volatile private var lastFusedAt = 0L

  private val androidListener = LocationListener { next ->
    if (SystemClock.elapsedRealtime() - lastFusedAt > FUSED_FRESH_MS) location = next
  }

  private val fusedListener =
    object : FusedLocationListener {
      override fun onChanged(location: LocationData?) {
        val next = location?.toLocation() ?: return
        lastFusedAt = SystemClock.elapsedRealtime()
        this@LocationTracker.location = next
      }

      override fun onError(exception: Exception) {
        Log.w(TAG, "FusedLocation 오류", exception)
      }
    }

  fun start() {
    runCatching {
      if (fused.isSupported()) {
        fused.initialize()
        fusedStarted = true
        fused.registerFusedLocationCallback(fusedListener)
      } else {
        Log.i(TAG, "FusedLocation 미지원: Android 위치만 쓴다")
      }
    }.onFailure { Log.w(TAG, "FusedLocation 시작 실패: Android 위치만 쓴다", it) }
    startAndroid()
  }

  fun stop() {
    if (fusedStarted) {
      runCatching { fused.unregisterFusedLocationCallback(fusedListener) }
      runCatching { fused.release() }
      fusedStarted = false
    }
    runCatching { manager.removeUpdates(androidListener) }
  }

  @SuppressLint("MissingPermission")
  private fun startAndroid() {
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER).filter { manager.allProviders.contains(it) }
    for (provider in providers) {
      runCatching {
        manager.getLastKnownLocation(provider)?.let { if (location == null) location = it }
        manager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, 0f, androidListener, Looper.getMainLooper())
      }.onFailure { Log.w(TAG, "$provider 위치 구독 실패", it) }
    }
  }

  private fun LocationData.toLocation(): Location =
    Location("fused-pleos").also {
      it.latitude = latitude
      it.longitude = longitude
      it.time = timestampMillis
    }

  companion object {
    private const val TAG = "LocationTracker"
    private const val UPDATE_INTERVAL_MS = 1_000L
    private const val FUSED_FRESH_MS = 5_000L
  }
}
