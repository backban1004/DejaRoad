package com.dejaroad.app

/** 도착 후 지점 하나를 어떻게 지났는지 */
enum class Outcome {
  /** 이번에 급제동했다 */
  HARD,
  /** 지난번엔 급제동했는데 이번엔 부드럽게 지났다 */
  BETTER,
  /** 전에 지난 적이 있고 이번에도 무난했다 */
  OK,
  /** 처음 지났고 무난했다 */
  FIRST_OK,
}

data class ReviewRow(
  val spot: HardSpot,
  val outcome: Outcome,
  val event: BrakeEvent?,
  /** 이번까지 이 곳에서 급제동한 횟수 */
  val hardCount: Int,
  /** 이번 주행 전 가장 최근 급제동 */
  val before: PastHard?,
)

/**
 * 주행 하나를 돌아본다.
 *
 * 급제동은 지점 [BEFORE_M] 전부터 [AFTER_M] 뒤까지 일어난 것을 그 곳의 것으로 본다
 * (갈림길을 보고 놀라 미리 밟는 경우가 많다).
 */
object Review {
  const val BEFORE_M = 800.0
  const val AFTER_M = 150.0

  fun spotOf(event: BrakeEvent, spots: List<HardSpot>): HardSpot? =
    spots.filter { event.atM in (it.atM - BEFORE_M)..(it.atM + AFTER_M) }.minByOrNull { kotlin.math.abs(it.atM - event.atM) }

  /** [passedM]까지 지난 지점만 돌아본다 (중간에 끝난 주행은 남은 곳을 빼고) */
  fun rows(spots: List<HardSpot>, events: List<BrakeEvent>, memory: RoadMemory, passedM: Double): List<ReviewRow> =
    spots.filter { it.atM <= passedM + AFTER_M }.map { spot ->
      val event = events.filter { spotOf(it, spots) == spot }.maxByOrNull { it.dropKph }
      val before = memory.lastHard(spot.key)
      val passedBefore = memory.passCount(spot.key) > 0 || before != null
      val outcome =
        when {
          event != null -> Outcome.HARD
          before != null && memory.passes.filter { it.key == spot.key }.maxByOrNull { it.atMillis }?.hard != false -> Outcome.BETTER
          passedBefore -> Outcome.OK
          else -> Outcome.FIRST_OK
        }
      ReviewRow(spot, outcome, event, memory.hardCount(spot.key) + if (event != null) 1 else 0, before)
    }

  /** 한 줄 요약 (기록용): "급제동 1 · 나아짐 1 · 무난 2". 0인 것은 뺀다 */
  fun headline(rows: List<ReviewRow>): String {
    if (rows.isEmpty()) return "지점 없음"
    val hard = rows.count { it.outcome == Outcome.HARD }
    val better = rows.count { it.outcome == Outcome.BETTER }
    val ok = rows.size - hard - better
    return listOf("급제동" to hard, "나아짐" to better, "무난" to ok).filter { it.second > 0 }.joinToString(" · ") { "${it.first} ${it.second}" }
  }

  /** 줄마다 기록 한 줄. [todayMin]은 오늘 그 지점 앞뒤에서 가장 낮았던 속도(알면) */
  fun detail(row: ReviewRow, now: Long, todayMin: Int? = null): String =
    when (row.outcome) {
      Outcome.HARD -> {
        val e = row.event!!
        val where = (row.spot.atM - e.atM).let { if (it > 30) "${Script.km(it)} 전" else "지점에서" }
        val same = row.before?.let { " · ${Script.gwaWa(Script.date(it.atMillis))} 같은 자리" } ?: ""
        "$where ${e.fromKph} → ${e.toKph} km/h$same"
      }
      Outcome.BETTER -> {
        val b = row.before
        val today = if (todayMin != null && b != null && todayMin > b.toKph) "오늘은 $todayMin km/h 아래로 안 떨어짐" else "오늘은 급제동 없음"
        b?.let { "${Script.date(it.atMillis)} ${it.fromKph} → ${it.toKph} km/h · $today" } ?: today
      }
      Outcome.OK -> "이번에도 급제동 없음"
      Outcome.FIRST_OK -> "처음 지남 · 급제동 없음"
    }

  fun state(row: ReviewRow): String =
    when (row.outcome) {
      Outcome.HARD -> if (row.hardCount > 1) "급제동 · ${ordinal(row.hardCount)}" else "급제동"
      Outcome.BETTER -> "나아짐"
      Outcome.OK, Outcome.FIRST_OK -> "무난"
    }

  private fun ordinal(n: Int): String =
    when (n) {
      2 -> "두 번째"
      3 -> "세 번째"
      4 -> "네 번째"
      5 -> "다섯 번째"
      else -> "${n}번째"
    }
}
