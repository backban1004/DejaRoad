package com.dejaroad.app

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/** 목적지와 남은 거리·시간 */
data class Destination(val name: String, val remainM: Int, val remainS: Int)

/**
 * Pleos NaviHelper로 지금 안내 중인 경로의 회전 안내 목록과 목적지를 받는다.
 * 콜백은 모두 메인 스레드에서 부른다.
 */
class NaviWatcher(context: Context) {

  private val navi = NaviHelper(context.applicationContext)
  private val main = Handler(Looper.getMainLooper())
  private var connected = false
  /** 연결 전에 들어온 경로 요청 (연결되면 보낸다) */
  private var pendingRoute: RouteInfo? = null

  var onSteps: (List<Step>) -> Unit = {}
  var onDestination: (Destination?) -> Unit = {}
  var onArrived: () -> Unit = {}
  var onRouteGone: () -> Unit = {}

  /** 내비가 바뀐 안내를 알아서 보내 주지 않을 때를 대비해 주기적으로 다시 묻는다 */
  private val poll =
    object : Runnable {
      override fun run() {
        if (!connected) return
        runCatching {
          navi.getRouteStateInfo()
          navi.getTBTInfo()
          navi.getDestinationInfo()
        }.onFailure { Log.w(TAG, "내비 상태 요청 실패", it) }
        main.postDelayed(this, POLL_MS)
      }
    }

  fun start() {
    navi.initialize(
      {
        Log.i(TAG, "NaviHelper 연결")
        navi.addListener(listener)
        connected = true
        pendingRoute?.let { r -> pendingRoute = null; send(r) }
        main.post(poll)
      },
      { t -> Log.e(TAG, "NaviHelper 연결 실패", t) },
    )
  }

  fun stop() {
    connected = false
    main.removeCallbacksAndMessages(null)
    runCatching { navi.removeListener(listener) }
    runCatching { navi.release() }
  }

  /** 시연용: 목적지로 경로 안내를 시작한다 (내비가 전체 화면으로 올라온다) */
  fun requestRoute(lat: Double, lon: Double, name: String) {
    val r = RouteInfo(lat, lon, null, null, name, "", "", "", RouteOption.RECOMMENDED, "", false)
    if (connected) send(r) else pendingRoute = r
  }

  private fun send(r: RouteInfo) {
    Log.i(TAG, "경로 요청: ${r}")
    runCatching { navi.requestRoute(r) }.onFailure { Log.e(TAG, "경로 요청 실패", it) }
  }

  private val listener =
    object : NaviHelperEventListener {
      override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
        Log.i(TAG, "경로 시작: ${routeStartInfo.name} ${routeStartInfo.distance}m")
        runCatching {
          navi.getDestinationInfo()
          navi.getTBTInfo()
        }
      }

      override fun onDestinationChanged() {
        runCatching { navi.getDestinationInfo() }
      }

      override fun onDestinationInfo(destinationInfo: DestinationInfo) {
        val d = destinationInfo
        val next = if (d.latitude == 0.0 && d.longitude == 0.0) null else Destination(d.name, d.remainDistance, d.remainTime)
        main.post { onDestination(next) }
      }

      override fun onTBTInfo(tbtInfo: List<TBTInfo>) {
        val steps = tbtInfo.map { Step(turnOf(it.type), sideOf(it.type), it.distance, it.description) }
        main.post { onSteps(steps) }
      }

      // isRouting은 '경로 계산 중'이라 안내 중에도 false다(에뮬레이터 확인). 안내 여부는 naviMode로 본다
      override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
        val guiding = routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING || routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING
        if (!guiding) main.post { onRouteGone() }
      }

      override fun onRouteEnded() {
        main.post { onRouteGone() }
      }

      override fun onRouteCancelled() {
        main.post { onRouteGone() }
      }

      override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
        Log.i(TAG, "목적지 도착")
        main.post { onArrived() }
      }

      override fun onError(errorCode: NaviErrorCode) {
        Log.w(TAG, "내비 오류: $errorCode")
      }
    }

  companion object {
    private const val TAG = "NaviWatcher"
    private const val POLL_MS = 3_000L

    fun turnOf(t: TurnType): Turn =
      when (t) {
        TurnType.TURN_STRAIGHT, TurnType.TURN_WAY -> Turn.STRAIGHT
        TurnType.TURN_RIGHT, TurnType.TURN_LEFT -> Turn.TURN
        TurnType.TURN_KEEP_R, TurnType.TURN_KEEP_L -> Turn.KEEP
        TurnType.TURN_SHARP_R, TurnType.TURN_SHARP_L -> Turn.SHARP
        TurnType.TURN_UTURN -> Turn.UTURN
        TurnType.TURN_ENTER_IC_R, TurnType.TURN_ENTER_IC_L, TurnType.TURN_ENTER_IC_C -> Turn.ENTER_IC
        TurnType.TURN_EXIT_IC_R, TurnType.TURN_EXIT_IC_L, TurnType.TURN_EXIT_IC_C -> Turn.EXIT_IC
        TurnType.TURN_ENTER_UNDER_R, TurnType.TURN_ENTER_UNDER_L, TurnType.TURN_ENTER_UNDER_C -> Turn.ENTER_UNDER
        TurnType.TURN_AVOID_UNDER_R, TurnType.TURN_AVOID_UNDER_L, TurnType.TURN_AVOID_UNDER_C -> Turn.AVOID_UNDER
        TurnType.TURN_ENTER_OVER_R, TurnType.TURN_ENTER_OVER_L, TurnType.TURN_ENTER_OVER_C -> Turn.ENTER_OVER
        TurnType.TURN_AVOID_OVER_R, TurnType.TURN_AVOID_OVER_L, TurnType.TURN_AVOID_OVER_C -> Turn.AVOID_OVER
        TurnType.TURN_EXIT_ROUNDABOUT -> Turn.EXIT_ROUNDABOUT
        TurnType.TURN_DEVIDE_MERGE -> Turn.MERGE
        TurnType.TURN_TOLL -> Turn.TOLL
        TurnType.TURN_TUNNEL -> Turn.TUNNEL
        TurnType.TURN_ENTER_SERVICEAREA, TurnType.TURN_RESTAREA -> Turn.REST
        TurnType.TURN_ARRIVE -> Turn.ARRIVE
        else -> if (t.name.startsWith("TURN_ENTER_ROUNDABOUT")) Turn.ROUNDABOUT else Turn.OTHER
      }

    fun sideOf(t: TurnType): Side {
      val n = t.name
      return when {
        n.endsWith("_R") || n == "TURN_RIGHT" -> Side.RIGHT
        n.endsWith("_L") || n == "TURN_LEFT" || n == "TURN_UTURN" -> Side.LEFT
        // 회전교차로는 몇 시 방향으로 나가는지로: 1~5시 오른쪽, 7~11시 왼쪽
        n.startsWith("TURN_ENTER_ROUNDABOUT_") ->
          when (n.substringAfterLast('_').toIntOrNull()) {
            in 1..5 -> Side.RIGHT
            in 7..11 -> Side.LEFT
            else -> Side.CENTER
          }
        else -> Side.CENTER
      }
    }
  }
}
