package com.dejaroad.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 지점마다 화면에 쓰고 소리로 읽을 말 */
object Script {

  /** 바로 앞 지점과 이만큼 안에 붙어 있으면 '이어서'로 읽는다 */
  const val FOLLOW_M = 500.0

  /** 화면 제목: "판교IC 합류" → "판교IC" (종류는 [kindTag]로 옆에 따로 쓴다) */
  fun title(spot: HardSpot): String = spot.name.removeSuffix(" 합류").removeSuffix(" 출구")

  /** 이름 옆 작은 글: 종류. 이름이 이미 종류 이름이면 비우고, 연속 회전은 꺾는 모양을 쓴다 */
  fun kindTag(spot: HardSpot): String {
    val side = sideWord(spot.side)
    if (spot.kind == SpotKind.QUICK_TURNS) {
      val a = turnWord(spot.side)
      val b = turnWord(spot.nextSide ?: spot.side)
      return spot.nextGapM?.let { "$a 후 ${meters(it)} $b" } ?: "$a 후 바로 $b"
    }
    if (spot.name == HardSpotFinder.kindLabel(spot.kind, spot.side)) return ""
    return when (spot.kind) {
      SpotKind.ENTER_HIGHWAY, SpotKind.MERGE -> "합류"
      SpotKind.JUNCTION -> "$side 분기"
      SpotKind.EXIT_HIGHWAY -> "출구"
      SpotKind.ROUNDABOUT -> "회전교차로"
      SpotKind.UTURN -> "유턴"
      SpotKind.BESIDE_UNDERPASS -> "지하차도 옆길"
      SpotKind.BESIDE_OVERPASS -> "고가차도 옆길"
      SpotKind.INTO_UNDERPASS -> "지하차도"
      SpotKind.INTO_OVERPASS -> "고가차도"
      SpotKind.SHARP -> "$side 급회전"
      SpotKind.QUICK_TURNS -> ""
    }
  }

  private fun turnWord(side: Side) =
    when (side) {
      Side.LEFT -> "좌회전"
      Side.RIGHT -> "우회전"
      Side.CENTER -> "직진"
    }

  /** 화면에 쓰는 짧은 할 일 (문장 대신 표지판처럼) */
  fun cue(spot: HardSpot): String {
    val side = sideWord(spot.side)
    return when (spot.kind) {
      SpotKind.ENTER_HIGHWAY -> "진입로에서 충분히 가속"
      SpotKind.JUNCTION -> "1km 전부터 $side 차로"
      SpotKind.EXIT_HIGHWAY -> "1km 전부터 $side 끝 차로"
      SpotKind.ROUNDABOUT -> "회전 중인 차 먼저 · ${exitWord(spot.side)} 진출"
      SpotKind.UTURN -> "맨 왼쪽 차로 · 유턴 신호 확인"
      SpotKind.BESIDE_UNDERPASS, SpotKind.BESIDE_OVERPASS -> "미리 $side 차로"
      SpotKind.INTO_UNDERPASS, SpotKind.SHARP -> "들어가기 전 감속"
      SpotKind.INTO_OVERPASS -> "차로 유지 · 오르기 전 감속"
      SpotKind.MERGE -> "옆 차로 간격 보고 천천히"
      SpotKind.QUICK_TURNS -> "꺾을 때부터 ${spot.nextSide?.let { sideWord(it) } ?: side} 차로"
    }
  }

  /** 기록 날짜: "11/10 화" */
  fun date(at: Long): String = SimpleDateFormat("M/d E", Locale.KOREA).format(Date(at))

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

  /** 주행 중 1.5km 전: 지금 할 일 한 문장 */
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

  /** 지난번 급제동한 곳 3km 앞: 지난 실패를 되새기기보다 지금부터 할 준비를 말한다 */
  fun early(spot: HardSpot): String {
    val side = sideWord(spot.side)
    return when (spot.kind) {
      SpotKind.ENTER_HIGHWAY, SpotKind.MERGE -> "진입로에서 속도를 충분히 올릴 준비를 해요"
      SpotKind.JUNCTION, SpotKind.EXIT_HIGHWAY, SpotKind.BESIDE_UNDERPASS, SpotKind.BESIDE_OVERPASS -> "지금부터 천천히 $side 차로로 옮겨 두면 돼요"
      SpotKind.UTURN -> "지금부터 천천히 맨 왼쪽 차로로 옮겨 두면 돼요"
      else -> "미리 속도를 줄일 준비를 해요"
    }
  }

  private fun exitWord(side: Side) = if (side == Side.LEFT) "왼쪽으로" else if (side == Side.RIGHT) "오른쪽으로" else "안내한 쪽으로"

  fun meters(m: Double): String = if (m >= 1000) "%.1fkm".format(m / 1000).replace(".0km", "km") else "${(m / 10).roundToInt() * 10}m"

  /** 소리로 읽을 거리: 1.5킬로미터, 3킬로미터, 300미터 (1km 넘으면 0.5km, 아래는 100m 단위로 반올림) */
  fun spoken(m: Double): String {
    if (m < 950) return "${((m / 100) + 0.5).toInt().coerceAtLeast(1) * 100}미터"
    val halves = ((m / 500) + 0.5).toInt().coerceAtLeast(2)
    return if (halves % 2 == 0) "${halves / 2}킬로미터" else "${halves / 2}.5킬로미터"
  }

  fun km(m: Double): String = if (m >= 1000) "%.1f km".format(m / 1000) else "${(m / 10).roundToInt() * 10} m"

  /** 지난 급제동 한 줄: "11/10 화 74→41 km/h" */
  fun pastLine(past: PastHard): String = "${date(past.atMillis)} ${past.fromKph}→${past.toKph} km/h"

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

  /**
   * 출발 전 1분 동안 읽어 줄 예습. 몇 곳인지 세지 않고, 목적지와 시간 다음에 지점을 순서대로 읽는다.
   * 예: "광교 갤러리아까지 27분이에요. 3분 뒤 판교IC 합류. 지난 화요일엔 여기서 시속 52에서 20까지 급히 줄였어요. …"
   */
  fun preview(spots: List<HardSpot>, minutes: (HardSpot) -> Int, past: (HardSpot) -> PastHard?, now: Long, dest: String? = null, totalMin: Int? = null): String {
    val head = if (dest != null && totalMin != null) "${dest}까지 ${totalMin}분이에요." else ""
    if (spots.isEmpty()) return "$head 합류나 분기 없이 가는 길이에요.".trim()
    val body =
      spots.mapIndexed { i, s ->
        val gap = if (i > 0) s.atM - spots[i - 1].atM else null
        val at = if (gap != null && gap < FOLLOW_M) "이어서 ${meters(gap)} 뒤" else "${minutes(s)}분 뒤"
        val p = past(s)?.let { " ${dayWord(it.atMillis, now)}엔 여기서 시속 ${it.fromKph}에서 ${it.toKph}까지 급히 줄였어요." } ?: ""
        "$at ${s.name}.$p ${advice(s)}"
      }
    return (listOf(head) + body + "가면서 1.5km 앞에서 다시 알려 드릴게요.")
      .filter { it.isNotBlank() }
      .joinToString(" ")
      .replace(Regex("""(\d+(?:\.\d+)?)km"""), "$1킬로미터")
      .replace(Regex("""(\d+)m 뒤"""), "$1미터 뒤")
      .replace(Regex("""(\d+)m 만에"""), "$1미터 만에")
  }
}
