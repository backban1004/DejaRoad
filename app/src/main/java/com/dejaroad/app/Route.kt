package com.dejaroad.app

/** 회전 안내의 종류. NaviHelper TurnType을 앱이 쓰는 묶음으로 줄인 것이다 */
enum class Turn {
  STRAIGHT, TURN, KEEP, SHARP, UTURN,
  ENTER_IC, EXIT_IC, ENTER_UNDER, ENTER_OVER, AVOID_UNDER, AVOID_OVER,
  ROUNDABOUT, EXIT_ROUNDABOUT, MERGE, TOLL, TUNNEL, REST, ARRIVE, OTHER,
}

enum class Side { LEFT, RIGHT, CENTER }

/**
 * 회전 안내 하나. NaviHelper TBTInfo.distance는 직전 안내 지점(첫 항목은 목록을 받은 순간의 위치)부터의 구간 길이다.
 */
data class Step(val turn: Turn, val side: Side, val lengthM: Double, val text: String)

/** 어려운 곳의 종류. 화면 아이콘과 안내 문장을 정한다 */
enum class SpotKind { ENTER_HIGHWAY, JUNCTION, EXIT_HIGHWAY, ROUNDABOUT, UTURN, BESIDE_UNDERPASS, BESIDE_OVERPASS, INTO_UNDERPASS, INTO_OVERPASS, MERGE, SHARP, QUICK_TURNS }

/**
 * 경로 위의 어려운 곳.
 * @param atM 경로 목록을 받은 위치부터 그 지점까지 거리
 * @param key 다음에 같은 곳을 알아보기 위한 이름 (안내 문구 + 종류 + 방향)
 */
data class HardSpot(
  val key: String,
  val name: String,
  val kind: SpotKind,
  val side: Side,
  val atM: Double,
  val score: Int,
  /** 바로 다음 안내까지 거리 (연속 회전 안내에 쓴다) */
  val nextGapM: Double? = null,
  val nextSide: Side? = null,
)

/**
 * 내비 회전 안내 목록에서 초보 운전자에게 어려운 곳을 고른다.
 *
 * 내비는 차로 수나 경로 좌표를 주지 않으므로 안내 종류와 구간 길이만으로 판단한다.
 * - 고속도로 합류·분기점·회전교차로처럼 그 자체로 어려운 곳
 * - 평범한 회전이라도 다음 안내가 [QUICK_GAP_M] 안에 이어지면 차로를 미리 옮겨야 하므로 어려운 곳
 * - 지난번에 급제동한 곳([pastKeys])은 점수를 크게 더해 꼭 넣는다
 */
object HardSpotFinder {
  const val MAX_SPOTS = 4
  const val MIN_SCORE = 2
  const val QUICK_GAP_M = 300.0
  private const val PAST_BONUS = 5

  fun find(steps: List<Step>, pastKeys: Set<String> = emptySet()): List<HardSpot> {
    val spots = mutableListOf<HardSpot>()
    var at = 0.0
    steps.forEachIndexed { i, step ->
      at += step.lengthM
      val next = steps.getOrNull(i + 1)?.takeIf { it.turn !in QUIET }
      val gap = next?.lengthM
      val kind = kindOf(step, gap) ?: return@forEachIndexed
      val key = keyOf(step)
      var score = baseScore(kind)
      // 바로 뒤에 또 갈림이 있으면 차로를 미리 정해야 해서 더 어렵다
      if (gap != null && gap < QUICK_GAP_M && kind != SpotKind.QUICK_TURNS) score += 1
      if (key in pastKeys) score += PAST_BONUS
      spots += HardSpot(key, nameOf(step, kind), kind, step.side, at, score, gap, next?.side)
    }
    return spots
      .filter { it.score >= MIN_SCORE }
      .sortedByDescending { it.score }
      .take(MAX_SPOTS)
      .sortedBy { it.atM }
  }

  /** 안내 지점이 어떤 어려운 곳인지. 평범한 곳이면 null */
  fun kindOf(step: Step, gapToNextM: Double?): SpotKind? =
    when (step.turn) {
      Turn.ENTER_IC -> SpotKind.ENTER_HIGHWAY
      Turn.KEEP -> SpotKind.JUNCTION
      Turn.EXIT_IC -> SpotKind.EXIT_HIGHWAY
      Turn.ROUNDABOUT -> SpotKind.ROUNDABOUT
      Turn.UTURN -> SpotKind.UTURN
      Turn.AVOID_UNDER -> SpotKind.BESIDE_UNDERPASS
      Turn.AVOID_OVER -> SpotKind.BESIDE_OVERPASS
      Turn.ENTER_UNDER -> SpotKind.INTO_UNDERPASS
      Turn.ENTER_OVER -> SpotKind.INTO_OVERPASS
      Turn.MERGE -> SpotKind.MERGE
      Turn.SHARP -> SpotKind.SHARP
      Turn.TURN -> if (gapToNextM != null && gapToNextM < QUICK_GAP_M) SpotKind.QUICK_TURNS else null
      else -> null
    }

  fun baseScore(kind: SpotKind): Int =
    when (kind) {
      SpotKind.ENTER_HIGHWAY, SpotKind.JUNCTION, SpotKind.ROUNDABOUT -> 3
      SpotKind.EXIT_HIGHWAY, SpotKind.UTURN, SpotKind.BESIDE_UNDERPASS, SpotKind.BESIDE_OVERPASS, SpotKind.MERGE, SpotKind.QUICK_TURNS -> 2
      SpotKind.INTO_UNDERPASS, SpotKind.INTO_OVERPASS, SpotKind.SHARP -> 1
    }

  fun keyOf(step: Step): String = "${step.turn}|${step.side}|${normalize(step.text)}"

  private fun normalize(text: String) = text.replace(Regex("\\s+"), "").lowercase()

  /** 화면 제목. 내비 안내 문구가 있으면 그대로 쓰고, 없으면 종류 이름으로 대신한다 */
  fun nameOf(step: Step, kind: SpotKind): String {
    val text = step.text.trim()
    if (text.isEmpty()) return kindLabel(kind, step.side)
    // IC 이름만 오면 들어가는지 나가는지 붙인다 ("판교IC" → "판교IC 합류")
    return when {
      kind == SpotKind.ENTER_HIGHWAY && text.endsWith("IC") -> "$text 합류"
      kind == SpotKind.EXIT_HIGHWAY && text.endsWith("IC") -> "$text 출구"
      else -> text
    }
  }

  fun kindLabel(kind: SpotKind, side: Side): String =
    when (kind) {
      SpotKind.ENTER_HIGHWAY -> "고속도로 합류"
      SpotKind.JUNCTION -> "${sideWord(side)} 분기점"
      SpotKind.EXIT_HIGHWAY -> "${sideWord(side)} 출구"
      SpotKind.ROUNDABOUT -> "회전교차로"
      SpotKind.UTURN -> "유턴"
      SpotKind.BESIDE_UNDERPASS -> "지하차도 옆길"
      SpotKind.BESIDE_OVERPASS -> "고가차도 옆길"
      SpotKind.INTO_UNDERPASS -> "지하차도 진입"
      SpotKind.INTO_OVERPASS -> "고가차도 진입"
      SpotKind.MERGE -> "길이 합쳐지는 곳"
      SpotKind.SHARP -> "${sideWord(side)} 급회전"
      SpotKind.QUICK_TURNS -> "연속 회전"
    }

  /** 다음 안내 계산에서 '갈림'으로 치지 않는 안내 */
  private val QUIET = setOf(Turn.STRAIGHT, Turn.TOLL, Turn.TUNNEL, Turn.REST, Turn.ARRIVE, Turn.OTHER)
}

fun sideWord(side: Side): String =
  when (side) {
    Side.LEFT -> "왼쪽"
    Side.RIGHT -> "오른쪽"
    Side.CENTER -> "가운데"
  }
