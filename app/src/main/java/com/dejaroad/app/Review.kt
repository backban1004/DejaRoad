package com.dejaroad.app

/** 도착 후 어려운 곳 하나를 어떻게 지났는지 */
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
 * 급제동은 어려운 곳 [BEFORE_M] 전부터 [AFTER_M] 뒤까지 일어난 것을 그 곳의 것으로 본다
 * (갈림길을 보고 놀라 미리 밟는 경우가 많다).
 */
object Review {
  const val BEFORE_M = 800.0
  const val AFTER_M = 150.0

  fun spotOf(event: BrakeEvent, spots: List<HardSpot>): HardSpot? =
    spots.filter { event.atM in (it.atM - BEFORE_M)..(it.atM + AFTER_M) }.minByOrNull { kotlin.math.abs(it.atM - event.atM) }

  /** [passedM]까지 지난 어려운 곳만 돌아본다 (중간에 끝난 주행은 남은 곳을 빼고) */
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

  /** 머리 문장 */
  fun headline(rows: List<ReviewRow>): String {
    val hard = rows.firstOrNull { it.outcome == Outcome.HARD }
    val better = rows.firstOrNull { it.outcome == Outcome.BETTER }
    return when {
      hard != null && better != null ->
        "${Script.eunNeun(better.spot.name)} 나아졌고, ${Script.eunNeun(hard.spot.name)} ${if (hard.hardCount > 1) "아직 " else ""}어려웠어요"
      hard != null && hard.hardCount > 1 -> "${Script.iGa(hard.spot.name)} 아직 어려웠어요"
      hard != null -> "${Script.iGa(hard.spot.name)} 어려웠어요"
      better != null -> "${Script.eunNeun(better.spot.name)} 이번엔 부드럽게 지났어요"
      rows.isEmpty() -> "어려운 곳 없이 도착했어요"
      else -> "어려운 곳 ${rows.size}곳을 모두 무난히 지났어요"
    }
  }

  /** 줄마다 설명. [todayMin]은 오늘 그 곳 앞뒤에서 가장 낮았던 속도(알면) */
  fun detail(row: ReviewRow, now: Long, todayMin: Int? = null): String =
    when (row.outcome) {
      Outcome.HARD -> {
        val e = row.event!!
        val where = (row.spot.atM - e.atM).let { if (it > 30) "${Script.meters(it)} 전에" else "지나면서" }
        val same = row.before?.let { " ${Script.gwaWa(Script.dayWord(it.atMillis, now))} 같은 곳이에요." } ?: ""
        "$where ${e.fromKph}→${e.toKph} km/h로 브레이크를 세게 밟았어요.$same"
      }
      Outcome.BETTER ->
        row.before?.let {
          val today = if (todayMin != null && todayMin > it.toKph) "오늘은 $todayMin km/h 아래로 떨어지지 않았어요." else "오늘은 급제동 없이 지났어요."
          "${Script.dayWord(it.atMillis, now)}엔 ${it.fromKph}→${it.toKph} km/h로 급히 줄였는데, $today"
        } ?: "오늘은 급제동 없이 지났어요."
      Outcome.OK -> "이번에도 급제동 없이 지났어요."
      Outcome.FIRST_OK -> "처음 지난 곳이에요. 급제동 없이 지났어요."
    }

  fun state(row: ReviewRow): String =
    when (row.outcome) {
      Outcome.HARD -> if (row.hardCount > 1) "${row.hardCount}번째 급제동" else "급제동"
      Outcome.BETTER -> "나아졌어요"
      Outcome.OK, Outcome.FIRST_OK -> "무난했어요"
    }
}
