package com.dejaroad.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.ceil

/** 지금 보여 줄 화면 */
enum class Mode { IDLE, PREVIEW, DRIVING, REVIEW }

/** 도착 후 돌아보기: 줄마다 결과와, 급제동한 곳은 앞뒤 20초 속도 */
data class TripReview(val headline: String, val rows: List<ReviewRow>, val traces: Map<String, List<Tick>>, val endedAt: Long, val remembered: Boolean)

/**
 * 데자로드의 중심.
 * - 내비 회전 안내 목록에서 어려운 곳을 고르고(예습)
 * - 주행 중에는 속도를 이어 더해 경로 위 위치를 알고, 1km 전(지난번 급제동한 곳은 2km 전)에 소리로 알리고
 * - 브레이크 페달과 속도로 급제동을 찾아, 도착하면 어려운 곳마다 어떻게 지났는지 남긴다(복습)
 *
 * 앱에 하나만 있고, 화면(MainActivity)과 포그라운드 서비스(RoadService)가 함께 쓴다.
 */
class RoadSession(context: Context) {

  private val app = context.applicationContext
  private val main = Handler(Looper.getMainLooper())
  private val store = MemoryStore(app)
  private val location = LocationTracker(app)
  val voice = VoiceGuide(app)
  val feed = VehicleFeed(app)
  private val navi = NaviWatcher(app)

  var memory by mutableStateOf(store.load())
    private set
  var destination by mutableStateOf<Destination?>(null)
    private set
  /** 이번 경로의 어려운 곳. atM은 이번 주행 시작점부터 거리 */
  var spots by mutableStateOf<List<HardSpot>>(emptyList())
    private set
  /** 이번 주행에서 달린 거리 (속도를 이어 더한다) */
  var traveledM by mutableStateOf(0.0)
    private set
  var review by mutableStateOf<TripReview?>(null)
    private set
  /** '그냥 출발하기'를 눌러 예습 목록을 접었는지 */
  var previewDone by mutableStateOf(false)
    private set

  val mode: Mode
    get() =
      when {
        review != null && !feed.isDriving -> Mode.REVIEW
        feed.isDriving -> Mode.DRIVING
        spots.isNotEmpty() || destination != null -> Mode.PREVIEW
        else -> Mode.IDLE
      }

  private var steps: List<Step> = emptyList()
  /** 내비가 목적지 남은 거리를 알려 준 때의 달린 거리 */
  private var destinationAtM = 0.0
  private var tripActive = false
  private val events = mutableListOf<BrakeEvent>()
  private val eventPlaces = mutableMapOf<BrakeEvent, Pair<Double, Double>?>()
  private val trace = ArrayList<Tick>()
  private val announced = HashSet<String>()
  private var lastTickAt = 0L
  private var parkedSince: Long? = null
  private var arrived = false
  private var started = false

  private val detector = HardBrakeDetector { e ->
    Log.i(TAG, "급제동 ${e.fromKph}→${e.toKph} km/h, 경로 ${e.atM.toInt()}m")
    events += e
    eventPlaces[e] = location.location?.let { it.latitude to it.longitude }
  }

  private val tick =
    object : Runnable {
      override fun run() {
        onTick(SystemClock.elapsedRealtime())
        main.postDelayed(this, TICK_MS)
      }
    }

  fun start() {
    if (started) return
    started = true
    feed.start()
    location.start()
    voice.start()
    navi.onSteps = ::onSteps
    navi.onDestination = { destination = it; destinationAtM = traveledM }
    navi.onArrived = { arrived = true }
    navi.onRouteGone = ::onRouteGone
    navi.start()
    main.post(tick)
  }

  fun stop() {
    if (!started) return
    started = false
    main.removeCallbacksAndMessages(null)
    navi.stop()
    feed.stop()
    location.stop()
    voice.release()
  }

  // region 경로

  private fun onSteps(next: List<Step>) {
    if (next == steps) return
    steps = next
    val found = HardSpotFinder.find(next, memory.hardKeys)
    if (!tripActive) {
      // 출발 전에 경로가 바뀌면 처음부터 다시 고른다
      traveledM = 0.0
      spots = found
      previewDone = false
      announced.clear()
    } else {
      // 주행 중 다시 받은 목록은 '지금 위치부터'이므로 달린 거리를 더해 이번 주행 기준으로 바꾼다
      val passed = spots.filter { it.atM <= traveledM }
      spots = passed + found.map { it.copy(atM = it.atM + traveledM) }.filter { f -> passed.none { it.key == f.key } }
    }
    Log.i(TAG, "회전 안내 ${next.size}개 → 어려운 곳 ${spots.size}곳: " + next.joinToString { "${it.turn}/${it.side}/${it.lengthM.toInt()}m/${it.text}" })
  }

  private fun onRouteGone() {
    if (tripActive) return
    if (steps.isEmpty() && spots.isEmpty()) return
    steps = emptyList()
    spots = emptyList()
    destination = null
  }

  /** 남은 분: 내비가 알려 준 남은 시간을 거리 비율로 나누고, 모르면 시속 40km로 친다 */
  fun minutesTo(spot: HardSpot): Int {
    val ahead = (spot.atM - traveledM).coerceAtLeast(0.0)
    val d = destination
    val seconds = if (d != null && d.remainM > 0 && d.remainS > 0) ahead / d.remainM * d.remainS else ahead / (40 / 3.6)
    return ceil(seconds / 60).toInt().coerceAtLeast(1)
  }

  fun pastOf(spot: HardSpot): PastHard? = memory.lastHard(spot.key)

  /** 목적지까지 남은 분: 내비가 알려 준 남은 시간에서 그 뒤에 달린 만큼 뺀다 */
  fun minutesToDestination(): Int? {
    val d = destination ?: return null
    if (d.remainM <= 0 || d.remainS <= 0) return null
    val left = (d.remainM - (traveledM - destinationAtM)).coerceAtLeast(0.0)
    return ceil(left / d.remainM * d.remainS / 60).toInt()
  }

  /** 아직 안 지난 첫 어려운 곳 */
  val nextSpot: HardSpot?
    get() = spots.firstOrNull { it.atM > traveledM - PASSED_M }

  // endregion

  // region 예습

  fun playPreview() {
    previewDone = false
    val text = Script.preview(spots, ::minutesTo, ::pastOf, System.currentTimeMillis())
    voice.speak(text)
  }

  fun stopPreview() = voice.stop()

  fun skipPreview() {
    voice.stop()
    previewDone = true
  }

  // endregion

  // region 주행

  private fun onTick(now: Long) {
    val dt = if (lastTickAt == 0L) 0.0 else (now - lastTickAt) / 1000.0
    lastTickAt = now
    val v = feed.speedKph
    val driving = feed.isDriving

    if (driving && !tripActive && spots.isNotEmpty()) startTrip()
    if (!tripActive) return

    if (v > 0.5f) traveledM += v / 3.6 * dt
    val t = Tick(System.currentTimeMillis(), v, feed.brake, traveledM)
    if (trace.size < MAX_TRACE) trace += t
    detector.feed(t)
    if (driving) {
      parkedSince = null
      announce()
    } else {
      val since = parkedSince ?: now.also { parkedSince = it }
      val allPassed = spots.isEmpty() || traveledM >= spots.last().atM + Review.AFTER_M
      if (now - since >= END_PARKED_MS && (allPassed || arrived)) endTrip()
    }
  }

  private fun startTrip() {
    Log.i(TAG, "주행 시작: 어려운 곳 ${spots.size}곳")
    tripActive = true
    arrived = false
    review = null
    events.clear()
    eventPlaces.clear()
    trace.clear()
    detector.reset()
    voice.stop()
  }

  /** 지난번 급제동한 곳은 2km 전에 한 번, 모든 곳은 1km 전에 한 번 말한다 */
  private fun announce() {
    for (spot in spots) {
      val ahead = spot.atM - traveledM
      if (ahead < 0) continue
      val past = pastOf(spot)
      if (past != null && ahead <= EARLY_M && ahead > NEAR_M && announced.add(spot.key + "@2k")) {
        voice.speak("${Script.spoken(ahead)} 앞 ${spot.name}. ${Script.dayWord(past.atMillis, System.currentTimeMillis())} 급히 줄였던 곳이에요. 미리 준비해요.")
        return
      }
      if (ahead <= NEAR_M && ahead > TOO_CLOSE_M && announced.add(spot.key + "@1k")) {
        voice.speak("${Script.spoken(ahead)} 앞 ${spot.name}. ${Script.now(spot)}.")
        return
      }
    }
  }

  /** 주행을 끝내고 돌아본다 */
  fun endTrip() {
    if (!tripActive) return
    tripActive = false
    val now = System.currentTimeMillis()
    val rows = Review.rows(spots, events, memory, traveledM)
    // 급제동한 곳마다 밟기 8초 전부터 12초 뒤까지 속도
    val traces = rows.mapNotNull { r -> r.event?.let { e -> r.spot.key to trace.filter { it.atMillis in (e.startMillis - 8_000)..(e.startMillis + 12_000) } } }.toMap()
    var next = memory
    for (r in rows) {
      val hard = r.event?.let { e -> eventPlaces[e].let { p -> PastHard(r.spot.key, r.spot.name, now, e.fromKph, e.toKph, p?.first, p?.second) } }
      next = next.plus(hard, Pass(r.spot.key, now, hard != null))
    }
    memory = next
    store.save(next)
    review = TripReview(Review.headline(rows), rows, traces, now, rows.any { it.outcome == Outcome.HARD })
    Log.i(TAG, "주행 끝: ${review?.headline} / " + rows.joinToString { "${it.spot.name}=${it.outcome}" })
  }

  /** 복습 화면 '확인' */
  fun closeReview() {
    review = null
    traveledM = 0.0
    destinationAtM = 0.0
    announced.clear()
    // 경로가 남아 있으면(시연처럼) 다음 주행을 위해 처음 위치 기준으로 다시 고른다
    val again = steps
    steps = emptyList()
    if (again.isNotEmpty()) onSteps(again) else spots = emptyList()
  }

  /** 복습 화면 '이번 기록은 빼기': 이번 주행에서 남긴 기록을 지운다 */
  fun forgetLastTrip() {
    val at = review?.endedAt ?: return
    memory = memory.copy(hards = memory.hards.filter { it.atMillis != at }, passes = memory.passes.filter { it.atMillis != at })
    store.save(memory)
    closeReview()
  }

  // endregion

  // region 시연용

  fun demoRoute() = navi.requestRoute(DEMO_LAT, DEMO_LON, DEMO_NAME)

  /**
   * 시연용: 내비 대신 판교역 → 광교 갤러리아 회전 안내 목록을 넣는다.
   * 내비가 경로를 주지 못하는 환경(에뮬레이터 37.2 GPS 문제 등)에서 화면 흐름을 보이기 위한 것이고, 실제 차에서는 쓰지 않는다.
   */
  fun loadDemoSteps() {
    Log.i(TAG, "시연용 경로 목록을 넣음 (내비 대신)")
    destination = Destination(DEMO_NAME, DEMO_STEPS.sumOf { it.lengthM }.toInt(), 27 * 60)
    destinationAtM = 0.0
    steps = emptyList()
    onSteps(DEMO_STEPS)
  }

  /** 지금 경로의 분기점·합류에 '지난 화요일' 급제동 기록을 넣는다 */
  fun seedPast() {
    val now = System.currentTimeMillis()
    val tuesday = now - daysSinceTuesday(now) * 86_400_000L - 3 * 3_600_000L
    val jc = spots.firstOrNull { it.kind == SpotKind.JUNCTION } ?: spots.getOrNull(1) ?: spots.firstOrNull()
    val ic = spots.firstOrNull { it.kind == SpotKind.ENTER_HIGHWAY && it != jc }
    val add = listOfNotNull(jc?.let { PastHard(it.key, it.name, tuesday, 74, 41, null, null) }, ic?.let { PastHard(it.key, it.name, tuesday, 52, 20, null, null) })
    memory = RoadMemory(hards = add, passes = add.map { Pass(it.key, tuesday, true) })
    store.save(memory)
    Log.i(TAG, "지난 기록 넣음: " + add.joinToString { it.name })
    val again = steps
    steps = emptyList()
    if (again.isNotEmpty() && !tripActive) onSteps(again)
  }

  private fun daysSinceTuesday(now: Long): Int {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val d = (c.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.TUESDAY + 7) % 7
    return if (d == 0) 7 else d
  }

  fun clearMemory() {
    memory = RoadMemory()
    store.save(memory)
    review = null
  }

  /** 시연용: 경로 위 위치를 옮긴다 (긴 고속도로 구간을 건너뛸 때) */
  fun jumpTo(meters: Double) {
    traveledM = meters
    Log.i(TAG, "위치 이동: ${meters.toInt()}m")
  }

  // endregion

  companion object {
    private const val TAG = "RoadSession"
    private const val TICK_MS = 200L
    private const val MAX_TRACE = 30_000
    const val NEAR_M = 1_000.0
    const val EARLY_M = 2_000.0
    private const val TOO_CLOSE_M = 150.0
    private const val PASSED_M = 50.0
    private const val END_PARKED_MS = 3_000L

    // 시연 목적지: 광교 갤러리아
    private const val DEMO_LAT = 37.28526
    private const val DEMO_LON = 127.05702
    private const val DEMO_NAME = "광교 갤러리아"
    private val DEMO_STEPS =
      listOf(
        Step(Turn.TURN, Side.RIGHT, 350.0, ""),
        Step(Turn.TURN, Side.LEFT, 900.0, ""),
        Step(Turn.ENTER_IC, Side.RIGHT, 350.0, "판교IC"),
        Step(Turn.STRAIGHT, Side.CENTER, 3_000.0, ""),
        Step(Turn.KEEP, Side.RIGHT, 4_600.0, "신갈JC"),
        Step(Turn.EXIT_IC, Side.RIGHT, 6_600.0, "동수원IC"),
        Step(Turn.TURN, Side.LEFT, 200.0, ""),
        Step(Turn.TURN, Side.RIGHT, 150.0, ""),
        Step(Turn.ARRIVE, Side.CENTER, 2_250.0, ""),
      )
  }
}
