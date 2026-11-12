package com.dejaroad.app

import kotlin.math.abs

/**
 * 지점 앞뒤의 속도를 '거리' 기준으로 잰 것.
 * 지점 [Review.BEFORE_M] 전부터 [Review.AFTER_M] 뒤까지 [STEP_M]마다 평균 속도(없으면 null)와 브레이크를 밟았는지.
 * 거리 기준이라 지난번과 오늘을 같은 자리끼리 겹쳐 볼 수 있다.
 */
data class SpeedProfile(val speeds: List<Float?>, val brakes: List<Boolean>) {

  val hasData: Boolean
    get() = speeds.count { it != null } >= 2

  /** 가장 낮은 속도 (반올림) */
  val minKph: Int?
    get() = speeds.filterNotNull().minOrNull()?.let { Math.round(it) }

  companion object {
    const val STEP_M = 25.0
    val SIZE = ((Review.BEFORE_M + Review.AFTER_M) / STEP_M).toInt() + 1
    /** 지점(0 m)이 몇 번째 칸인지 */
    val SPOT_INDEX = (Review.BEFORE_M / STEP_M).toInt()

    fun empty() = SpeedProfile(List(SIZE) { null }, List(SIZE) { false })

    /** 이번 주행 기록에서 [spotAtM] 앞뒤를 잘라 낸다 */
    fun of(ticks: List<Tick>, spotAtM: Double): SpeedProfile {
      val start = spotAtM - Review.BEFORE_M
      val sums = DoubleArray(SIZE)
      val counts = IntArray(SIZE)
      val brakes = BooleanArray(SIZE)
      for (t in ticks) {
        val i = Math.round((t.traveledM - start) / STEP_M).toInt()
        if (i !in 0 until SIZE || abs(t.traveledM - (start + i * STEP_M)) > STEP_M / 2) continue
        sums[i] += t.speedKph.toDouble()
        counts[i]++
        if (t.brake) brakes[i] = true
      }
      return SpeedProfile(List(SIZE) { if (counts[it] > 0) (sums[it] / counts[it]).toFloat() else null }, brakes.toList())
    }

    /**
     * 시연용 '지난번' 곡선: [fromKph]로 오다가 지점 [brakeAtM] m 전에서 [toKph]까지 줄이고 다시 올린다.
     * 시연 기록(seed_past)에만 쓴다.
     */
    fun demo(fromKph: Int, toKph: Int, brakeAtM: Double): SpeedProfile {
      val b = SPOT_INDEX - (brakeAtM / STEP_M).toInt()
      val speeds =
        List(SIZE) { i ->
          when {
            i < b -> fromKph.toFloat()
            i < b + 3 -> fromKph - (fromKph - toKph) * (i - b + 1) / 3f
            else -> (toKph + (i - b - 2) * 1.6f).coerceAtMost(fromKph - 4f)
          }
        }
      return SpeedProfile(speeds, List(SIZE) { it in b until b + 3 })
    }
  }
}
