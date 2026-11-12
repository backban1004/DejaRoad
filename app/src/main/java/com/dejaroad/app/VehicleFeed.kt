package com.dejaroad.app

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 급제동과 주행 여부를 보기 위한 차량 값.
 * - Pleos Vehicle SDK: 기어, 브레이크 페달
 * - Android 표준 Car API: 차량 속도 (Pleos SDK에 공개 API가 없음)
 */
class VehicleFeed(private val context: Context) {

  private val main = Handler(Looper.getMainLooper())
  private val vehicle = Vehicle(context)

  var gear by mutableStateOf<VehicleGear?>(null)
    private set
  var speedKph by mutableStateOf(0f)
    private set
  var brake by mutableStateOf(false)
    private set

  /** 기어가 P가 아니거나 움직이고 있으면 주행 중으로 본다 */
  val isDriving: Boolean
    get() = (gear != null && gear != VehicleGear.GEAR_PARK) || speedKph >= MOVING_KPH

  fun start() {
    runCatching { vehicle.initialize() }.onFailure { Log.e(TAG, "Vehicle 연결 실패", it) }
    // 차량 서비스가 연결될 시간을 두고 구독한다
    main.postDelayed({ subscribeVehicle(); subscribeSpeed() }, CONNECT_DELAY_MS)
  }

  fun stop() {
    main.removeCallbacksAndMessages(null)
    runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
    runCatching { vehicle.getBrake().unregisterBrakePedalStatus(brakeListener) }
    runCatching { propertyManager()?.unregisterCallback(speedCallback) }
    runCatching { car?.disconnect() }
    runCatching { vehicle.release() }
  }

  private val gearListener =
    object : GearSelectionListener {
      override fun onSelected(gear: VehicleGear?) {
        main.post { this@VehicleFeed.gear = gear }
      }

      override fun onFailed(e: Exception) = warn("기어", e)
    }

  private val brakeListener =
    object : BrakePedalStatusListener {
      override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
        main.post { brake = pressed == true }
      }

      override fun onFailed(e: Exception) = warn("브레이크", e)
    }

  private fun subscribeVehicle() {
    step("기어") {
      vehicle.getGear().getCurrentGearSelectionState({ g -> main.post { gear = g } }, { warn("기어 읽기", it) })
      vehicle.getGear().registerGearSelection(gearListener)
    }
    step("브레이크") {
      vehicle.getBrake().getCurrentBrakePedalStatus({ v -> main.post { brake = v == true } }, { warn("브레이크 읽기", it) })
      vehicle.getBrake().registerBrakePedalStatus(brakeListener)
    }
  }

  private var car: Car? = null

  private fun propertyManager(): CarPropertyManager? {
    val connected = car ?: Car.createCar(context).also { car = it } ?: return null
    return connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
  }

  private val speedCallback =
    object : CarPropertyManager.CarPropertyEventCallback {
      override fun onChangeEvent(value: CarPropertyValue<*>) {
        val v = value.value as? Float ?: return
        if (value.propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) main.post { speedKph = v * 3.6f }
      }

      override fun onErrorEvent(propertyId: Int, areaId: Int) {
        Log.w(TAG, "차량 속성 오류 prop=$propertyId area=$areaId")
      }
    }

  private fun subscribeSpeed() {
    val cpm = runCatching { propertyManager() }.getOrNull() ?: return Log.e(TAG, "CarPropertyManager 없음").let { }
    step("속도") { cpm.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FAST) }
  }

  private inline fun step(name: String, block: () -> Unit) {
    runCatching(block).onFailure { Log.e(TAG, "$name 구독 실패", it) }
  }

  private fun warn(name: String, e: Exception) {
    Log.w(TAG, "$name 실패", e)
  }

  companion object {
    private const val TAG = "VehicleFeed"
    private const val CONNECT_DELAY_MS = 1_500L
    private const val MOVING_KPH = 3f
  }
}
