package com.dejaroad.app

/** 차량 값 한 장 (주기적으로 받는다) */
data class Tick(val atMillis: Long, val speedKph: Float, val brake: Boolean, val traveledM: Double)

/** 급제동 한 번: 몇에서 몇으로 줄였는지, 경로 위 어디서 밟기 시작했는지 */
data class BrakeEvent(val startMillis: Long, val endMillis: Long, val fromKph: Int, val toKph: Int, val atM: Double) {
  val dropKph: Int
    get() = fromKph - toKph
}

/**
 * 브레이크 페달과 차 속도로 '놀라서 세게 밟은' 순간을 찾는다. (휴대폰은 페달 신호를 알 수 없다)
 *
 * 페달을 밟은 동안 속도가 [MIN_DROP_KPH] 넘게, 평균 [MIN_DECEL_KPH_S] km/h/s 넘게 줄었고
 * 밟기 시작한 속도가 [MIN_FROM_KPH] 이상이면 급제동으로 본다. 신호 대기처럼 천천히 서는 것은 제외된다.
 */
class HardBrakeDetector(private val onEvent: (BrakeEvent) -> Unit) {

  private var braking = false
  private var startTick: Tick? = null
  private var minKph = Float.MAX_VALUE
  private var minAt = 0L
  private var releasedAt: Long? = null
  /** 밟기 직전 속도를 알기 위해 최근 값을 조금 들고 있는다 */
  private val recent = ArrayDeque<Tick>()

  fun feed(t: Tick) {
    recent.addLast(t)
    while (recent.isNotEmpty() && t.atMillis - recent.first().atMillis > LOOKBACK_MS) recent.removeFirst()

    if (!braking) {
      if (t.brake) {
        braking = true
        // 페달 신호는 늦게 올 수 있어 직전 1초 안에서 가장 빠른 때를 시작으로 잡는다
        startTick = recent.filter { t.atMillis - it.atMillis <= LOOKBACK_MS }.maxByOrNull { it.speedKph } ?: t
        minKph = t.speedKph
        minAt = t.atMillis
        releasedAt = null
      }
      return
    }

    if (t.speedKph < minKph) {
      minKph = t.speedKph
      minAt = t.atMillis
    }
    releasedAt = if (t.brake) null else releasedAt ?: t.atMillis
    val released = releasedAt?.let { t.atMillis - it >= RELEASE_MS } ?: false
    val rising = t.speedKph > minKph + RISE_KPH
    if (released || rising) finish()
  }

  private fun finish() {
    val start = startTick
    braking = false
    startTick = null
    if (start == null) return
    val drop = start.speedKph - minKph
    val seconds = (minAt - start.atMillis).coerceAtLeast(1) / 1000.0
    if (start.speedKph >= MIN_FROM_KPH && drop >= MIN_DROP_KPH && drop / seconds >= MIN_DECEL_KPH_S) {
      onEvent(BrakeEvent(start.atMillis, minAt, Math.round(start.speedKph), Math.round(minKph), start.traveledM))
    }
  }

  fun reset() {
    braking = false
    startTick = null
    recent.clear()
  }

  companion object {
    const val MIN_FROM_KPH = 35f
    const val MIN_DROP_KPH = 20f
    /** 약 0.2g. 신호 앞에서 편하게 서는 감속은 보통 이보다 작다 */
    const val MIN_DECEL_KPH_S = 7.0
    private const val LOOKBACK_MS = 1_000L
    private const val RELEASE_MS = 600L
    private const val RISE_KPH = 3f
  }
}
