package com.dejaroad.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dejaroad.app.Outcome
import com.dejaroad.app.Review
import com.dejaroad.app.ReviewRow
import com.dejaroad.app.RoadSession
import com.dejaroad.app.Script
import com.dejaroad.app.SpeedProfile
import com.dejaroad.app.TripReview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 지난번 곡선(점선) 색 */
private val LastLine = Color(0xFF9A9DA3)

/**
 * 도착 후 기록: 맨 위에 큰 숫자 셋(급제동·나아짐·무난), 아래에 지점마다 결과.
 * 급제동했거나 나아진 곳은 지점 800m 전부터 150m 뒤까지 오늘 속도(실선)를 지난번 속도(점선) 위에 겹쳐 보인다.
 * 범례는 화면에 한 번만.
 */
@Composable
fun ReviewPanel(s: RoadSession) {
  val r = s.review ?: return
  val hardName = r.rows.firstOrNull { it.outcome == Outcome.HARD }?.spot?.let { Script.title(it) }
  BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background)) {
    val narrow = maxWidth < NARROW
    val side = if (narrow) 40.dp else 56.dp
    val graphs = r.rows.filter { hasGraph(r, it) }
    val lastLabel = graphs.firstNotNullOfOrNull { row -> row.before?.takeIf { lastProfile(row) != null }?.let { Script.date(it.atMillis) } }
    Column(Modifier.fillMaxSize()) {
      Row(Modifier.fillMaxWidth().padding(start = side, end = side, top = if (narrow) 32.dp else 40.dp), verticalAlignment = Alignment.Bottom) {
        Text(
          r.destName?.let { "$it 도착" } ?: "주행 기록",
          style = Type.headlineS.copy(fontSize = if (narrow) 36.sp else 44.sp, lineHeight = if (narrow) 44.sp else 52.sp, letterSpacing = (-1).sp),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Text(meta(r), style = Type.labelM.copy(fontSize = if (narrow) 22.sp else 26.sp, lineHeight = 32.sp), color = Palette.textSecondary, modifier = Modifier.padding(start = 20.dp))
      }
      Row(Modifier.fillMaxWidth().padding(start = side, end = side, top = if (narrow) 20.dp else 28.dp, bottom = if (narrow) 20.dp else 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Stats(r.rows, narrow)
        if (!narrow && graphs.isNotEmpty()) {
          Box(Modifier.weight(1f))
          Legend(lastLabel)
        }
      }
      Divider()
      if (narrow && graphs.isNotEmpty()) Legend(lastLabel, Modifier.padding(start = side, end = side, top = 16.dp))
      Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        if (r.rows.isEmpty()) {
          Text("이번 주행에서 지난 합류·분기 지점이 없어요", style = Type.bodyL, color = Palette.textSecondary, modifier = Modifier.padding(horizontal = side, vertical = 40.dp))
        }
        groupQuiet(r.rows).forEach { group ->
          val row = group.first()
          if (row.outcome == Outcome.OK || row.outcome == Outcome.FIRST_OK) {
            Row(Modifier.fillMaxWidth().padding(horizontal = side, vertical = 26.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(group.joinToString(" · ") { it.spot.name }, style = Type.titleS, modifier = Modifier.weight(1f))
              Text(Review.state(row), style = Type.bodyM, color = Palette.textSecondary, modifier = Modifier.padding(start = 20.dp))
            }
          } else {
            SpotRecord(r, row, narrow, Modifier.padding(start = side, end = side, top = 26.dp, bottom = 18.dp))
          }
          Divider()
        }
      }
      val note = hardName?.let { "$it 기록을 남겨 두고 다음 출발 전에 먼저 알려요" }
      val noteStyle = Type.labelM.copy(fontSize = if (narrow) 22.sp else 24.sp, lineHeight = 30.sp)
      if (narrow) {
        if (note != null) Text(note, style = noteStyle, color = Palette.textSecondary, modifier = Modifier.padding(start = side, end = side, top = 18.dp))
        Row(Modifier.fillMaxWidth().padding(start = side, end = side, top = 18.dp, bottom = 36.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          WideButton("이번 기록 지우기", dark = false, modifier = Modifier.weight(1f)) { s.forgetLastTrip() }
          WideButton("닫기", dark = true, modifier = Modifier.weight(1f)) { s.closeReview() }
        }
      } else {
        Row(Modifier.fillMaxWidth().padding(start = side, end = side, top = 24.dp, bottom = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
          Text(note ?: "", style = noteStyle, color = Palette.textSecondary, modifier = Modifier.weight(1f))
          WideButton("이번 기록 지우기", dark = false, modifier = Modifier.width(330.dp)) { s.forgetLastTrip() }
          WideButton("닫기", dark = true, modifier = Modifier.width(330.dp)) { s.closeReview() }
        }
      }
    }
  }
}

/** 도착 시각과 걸린 시간: "오후 4:00 · 27분" */
private fun meta(r: TripReview): String {
  val time = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(r.endedAt))
  val min = r.startedAt?.let { ((r.endedAt - it) / 60_000L).toInt() }?.takeIf { it > 0 }
  return if (min != null) "$time · ${min}분" else time
}

private fun lastProfile(row: ReviewRow): List<Float?>? = row.before?.profile?.takeIf { it.size == SpeedProfile.SIZE && it.count { v -> v != null } >= 2 }

private fun hasGraph(r: TripReview, row: ReviewRow): Boolean =
  (row.outcome == Outcome.HARD || row.outcome == Outcome.BETTER) && r.profiles[row.spot.key]?.hasData == true

/** 급제동했거나 나아진 곳 하나: 이름·종류, 오른쪽 결과, 기록 한 줄, 겹쳐 본 속도 */
@Composable
private fun SpotRecord(r: TripReview, row: ReviewRow, narrow: Boolean, modifier: Modifier) {
  val today = r.profiles[row.spot.key]
  val hard = row.outcome == Outcome.HARD
  Column(modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        buildAnnotatedString {
          append(Script.title(row.spot))
          val tag = Script.kindTag(row.spot)
          if (tag.isNotEmpty()) withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Palette.textTertiary, letterSpacing = 0.sp)) { append("  $tag") }
        },
        style = Type.titleM.copy(letterSpacing = (-0.5).sp),
        modifier = Modifier.weight(1f),
      )
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (hard) Box(Modifier.size(20.dp).background(Palette.textPrimary))
        Text(Review.state(row), style = Type.bodyM, fontWeight = FontWeight.Bold)
      }
    }
    Text(
      Review.detail(row, r.endedAt, today?.takeIf { it.hasData }?.minKph),
      style = Type.bodyM,
      fontWeight = if (hard) FontWeight.SemiBold else FontWeight.Normal,
      color = if (hard) Palette.textPrimary else Palette.textTertiary,
      modifier = Modifier.padding(top = 8.dp),
    )
    if (today != null && hasGraph(r, row)) {
      val last = lastProfile(row)
      val lastLabel = row.before?.let { Script.date(it.atMillis) }.takeIf { last != null }
      SpeedGraph(today, last, lastLabel, row.event?.toKph, row.outcome == Outcome.BETTER, Script.title(row.spot), if (narrow) 170.dp else 200.dp, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
  }
}

/** 큰 숫자 셋: 급제동 n, 지난번보다 나아짐 n, 무난 n (0인 것은 뺀다). 점수가 아니라 개수만 */
@Composable
private fun Stats(rows: List<ReviewRow>, narrow: Boolean) {
  if (rows.isEmpty()) return
  val hard = rows.count { it.outcome == Outcome.HARD }
  val better = rows.count { it.outcome == Outcome.BETTER }
  val ok = rows.size - hard - better
  val items = listOf("급제동" to hard, (if (narrow) "나아짐" else "지난번보다 나아짐") to better, "무난" to ok).filter { it.second > 0 }
  Row(verticalAlignment = Alignment.CenterVertically) {
    items.forEachIndexed { i, (label, n) ->
      if (i > 0) VLine(56.dp, Modifier.padding(horizontal = if (narrow) 24.dp else 44.dp))
      Text(
        buildAnnotatedString {
          append("$n")
          withStyle(SpanStyle(fontSize = if (narrow) 22.sp else 26.sp, fontWeight = FontWeight.Normal, color = Palette.textSecondary, letterSpacing = 0.sp)) { append("  $label") }
        },
        style = Type.headlineL.copy(fontSize = if (narrow) 48.sp else 64.sp, lineHeight = if (narrow) 56.sp else 68.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp),
      )
    }
  }
}

/** 실선 = 오늘, 점선 = 지난번(날짜), 회색 칸 = 오늘 브레이크 */
@Composable
private fun Legend(lastLabel: String?, modifier: Modifier = Modifier) {
  val style = Type.labelS.copy(fontSize = 22.sp, lineHeight = 28.sp)
  Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Canvas(Modifier.width(36.dp).height(6.dp)) { drawLine(Palette.dark, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 4.dp.toPx()) }
    Text("오늘", style = style, color = Palette.textSecondary)
    if (lastLabel != null) {
      Canvas(Modifier.padding(start = 12.dp).width(36.dp).height(6.dp)) {
        drawLine(LastLine, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())))
      }
      Text(lastLabel, style = style, color = Palette.textSecondary)
    }
    Box(Modifier.padding(start = 12.dp).width(24.dp).height(18.dp).background(Palette.button))
    Text("브레이크", style = style, color = Palette.textSecondary)
  }
}

/**
 * 지점 앞뒤 속도 (거리 기준). 오늘은 짙은 실선, 지난번은 회색 점선, 오늘 브레이크를 밟은 구간은 회색 칸.
 * 아래에 '800 m 전'과 지점 이름, 곡선 위에 오늘 처음 속도·가장 낮은 속도와 지난번 가장 낮은 속도.
 */
@Composable
private fun SpeedGraph(today: SpeedProfile, last: List<Float?>?, lastLabel: String?, lowKph: Int?, better: Boolean, spotName: String, height: Dp, modifier: Modifier = Modifier) {
  val measurer = rememberTextMeasurer()
  val strong = Type.labelS.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Palette.textPrimary)
  val soft = Type.labelS.copy(fontSize = 22.sp, lineHeight = 28.sp, color = Palette.textSecondary)
  val all = today.speeds.filterNotNull() + (last?.filterNotNull() ?: emptyList())
  val hi = (all.max() + 4f).coerceAtLeast(20f)
  val lo = (all.min() - 16f).coerceAtLeast(0f)
  val lastWord = lastLabel ?: "지난번"
  Canvas(modifier.height(height)) {
    val top = 30.dp.toPx()
    val bottom = 36.dp.toPx()
    val h = size.height - top - bottom
    val n = SpeedProfile.SIZE
    fun x(i: Int) = i.toFloat() / (n - 1) * size.width
    fun y(v: Float) = top + h - (v - lo) / (hi - lo).coerceAtLeast(1f) * h

    // 오늘 브레이크 밟은 구간
    var start = -1
    for (i in 0..n) {
      val on = i < n && today.brakes[i]
      if (on && start < 0) start = i
      if (!on && start >= 0) {
        drawRect(Palette.button, Offset(x(start), top), Size((x(i - 1) - x(start)).coerceAtLeast(x(1)), h))
        start = -1
      }
    }
    // 바닥선, 지점 자리 세로선
    drawLine(Palette.divider, Offset(0f, top + h), Offset(size.width, top + h), 2.dp.toPx())
    val sx = x(SpeedProfile.SPOT_INDEX)
    drawLine(Palette.textPrimary, Offset(sx, top - 6.dp.toPx()), Offset(sx, top + h), 2.dp.toPx())

    val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
    if (last != null) for (seg in segments(last)) drawSeg(seg, ::x, ::y, LastLine, 3.dp.toPx(), dash)
    for (seg in segments(today.speeds)) drawSeg(seg, ::x, ::y, Palette.dark, 4.dp.toPx(), null)

    // 오늘 처음 속도
    val firstI = today.speeds.indexOfFirst { it != null }
    val firstV = today.speeds[firstI]!!
    drawText(measurer.measure("${Math.round(firstV)} km/h", strong), topLeft = Offset(x(firstI).coerceAtMost(size.width - 120.dp.toPx()), (y(firstV) - 30.dp.toPx()).coerceAtLeast(0f)))
    val known = today.speeds.indices.filter { today.speeds[it] != null }
    val lastLow = last?.let { l -> l.indices.filter { l[it] != null }.minBy { l[it]!! } }
    // 오늘 표시할 점: 나아진 곳은 지난번 급히 줄였던 자리(같은 자리의 오늘 속도), 급제동한 곳은 오늘 가장 낮은 곳
    val lowI = if (better && lastLow != null) known.minBy { kotlin.math.abs(it - lastLow) } else known.minBy { today.speeds[it]!! }
    val lowV = today.speeds[lowI]!!
    drawCircle(Palette.dark, 7.dp.toPx(), Offset(x(lowI), y(lowV)))
    val todayText = "오늘 ${if (better) Math.round(lowV) else lowKph ?: Math.round(lowV)} km/h"
    // 나아진 곳은 점 위에, 급제동한 곳은 점 왼쪽 아래에
    fun todayPos(t: androidx.compose.ui.text.TextLayoutResult) =
      if (better) Offset((x(lowI) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), (y(lowV) - t.size.height - 12.dp.toPx()).coerceAtLeast(0f))
      else Offset((x(lowI) - t.size.width - 14.dp.toPx()).coerceAtLeast(0f), (y(lowV) + 8.dp.toPx()).coerceAtMost(top + h + 4.dp.toPx() - t.size.height))
    val lowT = measurer.measure(todayText, strong)
    val lowPos = todayPos(lowT)
    if (last != null && lastLow != null) {
      // 지난번 가장 낮은 곳: 점 아래 가운데. 오늘 글과 겹치면 오늘 글 하나에 함께 쓴다
      val lv = last[lastLow]!!
      val t = measurer.measure("$lastWord ${Math.round(lv)} km/h", soft)
      val lx = (x(lastLow) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
      val ly = (y(lv) + 10.dp.toPx()).coerceAtMost(top + h + 4.dp.toPx() - t.size.height)
      val overlap = lx < lowPos.x + lowT.size.width && lowPos.x < lx + t.size.width && ly < lowPos.y + lowT.size.height && lowPos.y < ly + t.size.height
      if (overlap) {
        val both = measurer.measure(buildAnnotatedString {
          withStyle(strong.toSpanStyle()) { append(todayText) }
          withStyle(soft.toSpanStyle()) { append("  ·  $lastWord ${Math.round(lv)} km/h") }
        })
        drawText(both, topLeft = todayPos(both))
      } else {
        drawText(lowT, topLeft = lowPos)
        drawText(t, topLeft = Offset(lx, ly))
      }
    } else {
      drawText(lowT, topLeft = lowPos)
    }
    // 아래: 800 m 전, 지점 이름
    val axisY = top + h + 8.dp.toPx()
    drawText(measurer.measure("${Review.BEFORE_M.toInt()} m 전", soft), topLeft = Offset(0f, axisY))
    val spotT = measurer.measure(spotName, strong)
    drawText(spotT, topLeft = Offset((sx - spotT.size.width / 2).coerceAtMost(size.width - spotT.size.width), axisY))
  }
}

/** 이어서 무난했던 곳들은 한 줄로 묶는다 (예: "동수원IC 출구 · 연속 회전") */
private fun groupQuiet(rows: List<ReviewRow>): List<List<ReviewRow>> {
  val out = mutableListOf<MutableList<ReviewRow>>()
  for (r in rows) {
    val last = out.lastOrNull()?.last()
    val quiet = r.outcome == Outcome.FIRST_OK || r.outcome == Outcome.OK
    val lastQuiet = last != null && (last.outcome == Outcome.FIRST_OK || last.outcome == Outcome.OK)
    if (quiet && lastQuiet) out.last() += r else out += mutableListOf(r)
  }
  return out
}

/** 값이 이어진 구간들 (null에서 끊는다): (칸 번호, 속도) 목록 */
private fun segments(values: List<Float?>): List<List<Pair<Int, Float>>> {
  val out = mutableListOf<MutableList<Pair<Int, Float>>>()
  var cur: MutableList<Pair<Int, Float>>? = null
  values.forEachIndexed { i, v ->
    if (v == null) {
      cur = null
    } else {
      val c = cur ?: mutableListOf<Pair<Int, Float>>().also { out += it; cur = it }
      c += i to v
    }
  }
  return out.filter { it.size >= 2 }
}

private fun DrawScope.drawSeg(seg: List<Pair<Int, Float>>, x: (Int) -> Float, y: (Float) -> Float, color: Color, width: Float, effect: PathEffect?) {
  val path = Path()
  seg.forEachIndexed { k, (i, v) -> if (k == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
  drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
}
