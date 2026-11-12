package com.dejaroad.app

import java.util.Calendar
import kotlin.math.roundToInt

/** 어려운 곳마다 화면에 쓰고 소리로 읽을 문장 */
object Script {

  /** 출발 전 예습: 이 곳에서 무엇을 하면 되는지 */
  fun advice(spot: HardSpot): String {
    val side = sideWord(spot.side)
    return when (spot.kind) {
      SpotKind.ENTER_HIGHWAY -> "진입로에서 속도를 충분히 올리고, 옆 차로 차와 간격을 보고 들어가요."
      SpotKind.JUNCTION -> "$side 길로 갈라져요. 1km 전부터 $side 차로로 옮겨 두세요."
      SpotKind.EXIT_HIGHWAY -> "$side 출구로 나가요. 1km 전부터 $side 끝 차로에 있어요."
      SpotKind.ROUNDABOUT -> "회전 중인 차가 먼저예요. 천천히 들어가서 ${exitWord(spot.side)} 나가요."
      SpotKind.UTURN -> "유턴 표지와 신호를 확인하고, 맨 ${sideWord(Side.LEFT)} 차로에서 돌아요."
      SpotKind.BESIDE_UNDERPASS -> "지하차도로 들어가지 않고 $side 옆길로 가요. 미리 $side 차로에 있어요."
      SpotKind.BESIDE_OVERPASS -> "고가차도로 올라가지 않고 $side 옆길로 가요. 미리 $side 차로에 있어요."
      SpotKind.INTO_UNDERPASS -> "지하차도로 들어가요. 들어가기 전 속도를 조금 줄여요."
      SpotKind.INTO_OVERPASS -> "고가차도로 올라가요. 올라가는 차로를 지키고 속도를 조금 줄여요."
      SpotKind.MERGE -> "길이 합쳐져요. 옆 차로 차와 간격을 보고 천천히 들어가요."
      SpotKind.SHARP -> "${side}으로 크게 꺾여요. 들어가기 전에 속도를 줄여요."
      SpotKind.QUICK_TURNS -> {
        val gap = spot.nextGapM?.let { meters(it) } ?: "곧"
        val next = spot.nextSide?.let { sideWord(it) } ?: side
        "${side}으로 꺾고 $gap 만에 또 ${next}으로 가요. 꺾을 때부터 $next 차로로 붙어요."
      }
    }
  }

  /** 주행 중 1km 전: 지금 할 일 한 문장 */
  fun now(spot: HardSpot): String {
    val side = sideWord(spot.side)
    return when (spot.kind) {
      SpotKind.ENTER_HIGHWAY -> "진입로에서 속도를 올리고 간격을 보고 들어가요"
      SpotKind.JUNCTION, SpotKind.EXIT_HIGHWAY, SpotKind.BESIDE_UNDERPASS, SpotKind.BESIDE_OVERPASS -> "지금 $side 차로로 옮겨 두세요"
      SpotKind.ROUNDABOUT -> "속도를 줄이고 회전 중인 차를 먼저 보내요"
      SpotKind.UTURN -> "맨 왼쪽 차로로 옮겨 두세요"
      SpotKind.INTO_UNDERPASS, SpotKind.INTO_OVERPASS, SpotKind.SHARP -> "속도를 조금 줄여요"
      SpotKind.MERGE -> "옆 차로 차와 간격을 보세요"
      SpotKind.QUICK_TURNS -> "꺾은 뒤 바로 ${spot.nextSide?.let { sideWord(it) } ?: side} 차로로 붙어요"
    }
  }

  private fun exitWord(side: Side) = if (side == Side.LEFT) "왼쪽으로" else if (side == Side.RIGHT) "오른쪽으로" else "안내한 쪽으로"

  fun meters(m: Double): String = if (m >= 1000) "%.1fkm".format(m / 1000).replace(".0km", "km") else "${(m / 10).roundToInt() * 10}m"

  /** 소리로 읽을 거리: 1킬로미터, 2킬로미터, 300미터 (100m 단위로 반올림) */
  fun spoken(m: Double): String = if (m >= 950) "${((m / 1000) + 0.5).toInt().coerceAtLeast(1)}킬로미터" else "${((m / 100) + 0.5).toInt().coerceAtLeast(1) * 100}미터"

  fun km(m: Double): String = if (m >= 1000) "%.1f km".format(m / 1000) else "${(m / 10).roundToInt() * 10} m"

  /** 지난 급제동을 한 줄로: "지난 화요일 여기서 74→41 km/h로 급히 줄였어요" */
  fun pastLine(past: PastHard, now: Long): String = "${dayWord(past.atMillis, now)} 여기서 ${past.fromKph}→${past.toKph} km/h로 급히 줄였어요"

  /** 며칠 전인지 사람 말로: 오늘, 어제, 지난 화요일, 10월 3일 */
  fun dayWord(at: Long, now: Long): String {
    val a = Calendar.getInstance().apply { timeInMillis = at }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    val days = dayIndex(b) - dayIndex(a)
    return when {
      days <= 0 -> "오늘"
      days == 1L -> "어제"
      days < 7 -> "지난 ${WEEKDAYS[a.get(Calendar.DAY_OF_WEEK) - 1]}요일"
      else -> "${a.get(Calendar.MONTH) + 1}월 ${a.get(Calendar.DAY_OF_MONTH)}일"
    }
  }

  private fun dayIndex(c: Calendar): Long = (c.timeInMillis + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 86_400_000L

  private val WEEKDAYS = listOf("일", "월", "화", "수", "목", "금", "토")

  /** 받침에 맞춰 이/가 */
  fun iGa(word: String): String = word + if (hasFinal(word)) "이" else "가"

  /** 받침에 맞춰 과/와 */
  fun gwaWa(word: String): String = word + if (hasFinal(word)) "과" else "와"

  /** 받침에 맞춰 은/는 */
  fun eunNeun(word: String): String = word + if (hasFinal(word)) "은" else "는"

  private fun hasFinal(word: String): Boolean {
    val c = word.trim().lastOrNull() ?: return false
    if (c in '가'..'힣') return (c - '가') % 28 != 0
    // 영문 약어(IC, JC 등)는 읽는 소리 기준: 씨/제이씨 → 받침 없음, L·M·N·R은 받침
    if (c.isDigit()) return c in "013678"
    return c.uppercaseChar() in "LMNR"
  }

  /** 출발 전 1분 동안 읽어 줄 예습 */
  fun preview(spots: List<HardSpot>, minutes: (HardSpot) -> Int, past: (HardSpot) -> PastHard?, now: Long): String {
    if (spots.isEmpty()) return "오늘 길에는 특별히 어려운 곳이 없어요. 편하게 출발하세요."
    val order = listOf("첫째", "둘째", "셋째", "넷째", "다섯째")
    val body =
      spots.mapIndexed { i, s ->
        val p = past(s)?.let { " ${dayWord(it.atMillis, now)} 여기서 시속 ${it.fromKph}에서 ${it.toKph}킬로미터로 급히 줄였어요." } ?: ""
        "${order.getOrElse(i) { "다음" }}, ${minutes(s)}분 뒤 ${s.name}. ${advice(s)}$p"
      }
    return ("오늘 길에 어려운 곳은 ${spots.size}곳이에요. " + body.joinToString(" ") + " 각 지점 1km 전에 다시 말씀드릴게요.").replace("1km", "1킬로미터").replace(Regex("""(\d+)m 만에"""), "$1미터 만에")
  }
}
