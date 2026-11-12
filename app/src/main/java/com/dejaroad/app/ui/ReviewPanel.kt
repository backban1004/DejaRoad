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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dejaroad.app.Outcome
import com.dejaroad.app.R
import com.dejaroad.app.Review
import com.dejaroad.app.ReviewRow
import com.dejaroad.app.RoadSession
import com.dejaroad.app.Script
import com.dejaroad.app.SpeedProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 지난번 곡선(점선) 색 */
private val LastLine = Color(0xFFA7AAB0)

/**
 * 도착 후 복습: 요약 칸(나아진 곳·급제동·무난), 곳마다 결과.
 * 급제동했거나 나아진 곳은 지점 800m 전부터 150m 뒤까지 오늘 속도(실선)를 지난번 속도(점선) 위에 겹쳐 보인다.
 */
@Composable
fun ReviewPanel(s: RoadSession) {
  val r = s.review ?: return
  val time = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(r.endedAt))
  val hardName = r.rows.firstOrNull { it.outcome == Outcome.HARD }?.spot?.name
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val narrow = maxWidth < NARROW
    val side = if (narrow) 32.dp else 40.dp
    Column(Modifier.fillMaxSize().padding(start = side, end = side, top = 32.dp, bottom = 36.dp)) {
      AppHeader("오늘 $time 도착")
      Text(r.headline, style = Type.titleL, modifier = Modifier.padding(top = 22.dp))
      Summary(r.rows)
      BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp).clip(CardShape).background(Palette.surface)) {
        val cardH = maxHeight
        Column(
          Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = cardH).padding(horizontal = if (narrow) 28.dp else 36.dp),
          verticalArrangement = Arrangement.SpaceEvenly,
        ) {
          if (r.rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().heightIn(min = cardH), contentAlignment = Alignment.Center) { Text("이번 주행에서 지난 어려운 곳이 없어요", style = Type.bodyL, color = Palette.textSecondary) }
          }
          groupQuiet(r.rows).forEachIndexed { i, group ->
            if (i > 0) Divider()
            val row = group.first()
            val today = r.profiles[row.spot.key]
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.joinToString(" · ") { it.spot.name }, style = Type.titleS, modifier = Modifier.weight(1f))
                StateBadge(row)
              }
              Text(Review.detail(row, r.endedAt, today?.takeIf { it.hasData }?.minKph), style = Type.bodyS, color = Palette.textTertiary, modifier = Modifier.padding(top = 6.dp))
              val last = row.before?.profile?.takeIf { it.size == SpeedProfile.SIZE && it.count { v -> v != null } >= 2 }
              if ((row.outcome == Outcome.HARD || row.outcome == Outcome.BETTER) && today != null && today.hasData) {
                val lastLabel = row.before?.let { Script.dayWord(it.atMillis, r.endedAt) }.takeIf { last != null }
                val lowLabel = row.event?.toKph
                if (narrow) {
                  Column(Modifier.padding(top = 12.dp)) {
                    SpeedGraph(today, last, lowLabel, row.outcome == Outcome.BETTER, row.spot.name, Modifier.fillMaxWidth())
                    Legend(lastLabel, Modifier.padding(top = 6.dp), horizontal = true)
                  }
                } else {
                  Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                    SpeedGraph(today, last, lowLabel, row.outcome == Outcome.BETTER, row.spot.name, Modifier.weight(1f))
                    Legend(lastLabel, Modifier.padding(start = 28.dp, bottom = 40.dp).width(200.dp), horizontal = false)
                  }
                }
              }
            }
          }
        }
      }
      if (hardName != null) {
        Text(fixObject("다음에 ${hardName}을 지나는 길이면 출발 전에 먼저 알려 드려요", hardName), style = Type.labelM, color = Palette.textSecondary, modifier = Modifier.padding(top = 18.dp, start = 4.dp))
      }
      Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        WideButton("확인", dark = true, modifier = Modifier.weight(1.3f)) { s.closeReview() }
        WideButton("이번 기록은 빼기", dark = false, modifier = Modifier.weight(1f)) { s.forgetLastTrip() }
      }
    }
  }
}

/** 요약 칸: 나아진 곳 n, 급제동 n, 무난 n (0인 칸은 뺀다). 점수가 아니라 개수만 */
@Composable
private fun Summary(rows: List<ReviewRow>) {
  if (rows.isEmpty()) return
  val better = rows.count { it.outcome == Outcome.BETTER }
  val hard = rows.count { it.outcome == Outcome.HARD }
  val ok = rows.count { it.outcome == Outcome.OK || it.outcome == Outcome.FIRST_OK }
  val shape = RoundedCornerShape(16.dp)
  Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    if (better > 0) {
      Row(Modifier.height(52.dp).clip(shape).background(Palette.surface).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Symbol(R.drawable.ic_check_circle, 30.dp)
        Text("나아진 곳 $better", style = Type.labelM, fontWeight = FontWeight.SemiBold)
      }
    }
    if (hard > 0) {
      Box(Modifier.height(52.dp).clip(shape).background(Palette.dark).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
        Text("급제동 $hard", style = Type.labelM, fontWeight = FontWeight.SemiBold, color = Palette.onDark)
      }
    }
    if (ok > 0) {
      Box(Modifier.height(52.dp).clip(shape).background(Palette.surface).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
        Text("무난 $ok", style = Type.labelM, color = Palette.textSecondary)
      }
    }
  }
}

/**
 * 결과 표시. 굵기만이 아니라 모양으로 구분한다:
 * 급제동은 짙은 칸, 나아졌어요는 체크 아이콘, 무난했어요는 회색 글씨.
 */
@Composable
private fun StateBadge(row: ReviewRow) {
  val label = Review.state(row)
  when (row.outcome) {
    Outcome.HARD ->
      Box(Modifier.padding(start = 24.dp).clip(RoundedCornerShape(14.dp)).background(Palette.dark).padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(label, style = Type.labelM, fontWeight = FontWeight.SemiBold, color = Palette.onDark)
      }
    Outcome.BETTER ->
      Row(Modifier.padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Symbol(R.drawable.ic_check_circle, 30.dp)
        Text(label, style = Type.labelM, fontWeight = FontWeight.SemiBold)
      }
    Outcome.OK, Outcome.FIRST_OK -> Text(label, style = Type.labelM, color = Palette.textSecondary, modifier = Modifier.padding(start = 24.dp))
  }
}

/** 실선 = 오늘, 점선 = 지난번, 회색 띠 = 오늘 브레이크 */
@Composable
private fun Legend(lastLabel: String?, modifier: Modifier, horizontal: Boolean) {
  val items: @Composable () -> Unit = {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Canvas(Modifier.width(40.dp).height(6.dp)) { drawLine(Palette.dark, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 5.dp.toPx()) }
      Text("오늘", style = Type.labelS, color = Palette.textSecondary)
    }
    if (lastLabel != null) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.width(40.dp).height(6.dp)) {
          drawLine(LastLine, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 9.dp.toPx())))
        }
        Text(lastLabel, style = Type.labelS, color = Palette.textSecondary)
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(Modifier.width(40.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(Palette.segmentOff))
      Text("오늘 브레이크", style = Type.labelS, color = Palette.textSecondary)
    }
  }
  if (horizontal) Row(modifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) { items() } else Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) { items() }
}

/** 을/를을 받침에 맞춘다 */
private fun fixObject(text: String, name: String): String {
  val c = name.trim().lastOrNull() ?: return text
  val final = if (c in '가'..'힣') (c - '가') % 28 != 0 else c.uppercaseChar() in "LMNR" || c in "013678"
  return text.replace("${name}을", name + if (final) "을" else "를")
}

/**
 * 지점 앞뒤 속도 (거리 기준). 오늘은 짙은 실선, 지난번은 회색 점선, 오늘 브레이크를 밟은 구간은 회색 띠.
 * 아래에 '800m 전'과 지점 이름, 곡선 위에 오늘 처음 속도·가장 낮은 속도와 지난번 가장 낮은 속도.
 */
@Composable
private fun SpeedGraph(today: SpeedProfile, last: List<Float?>?, lowKph: Int?, better: Boolean, spotName: String, modifier: Modifier = Modifier) {
  val measurer = rememberTextMeasurer()
  val strong = Type.labelS.copy(fontWeight = FontWeight.SemiBold, color = Palette.textPrimary)
  val soft = Type.labelS.copy(color = Palette.textSecondary)
  val all = today.speeds.filterNotNull() + (last?.filterNotNull() ?: emptyList())
  val hi = (all.max() + 4f).coerceAtLeast(20f)
  val lo = (all.min() - 16f).coerceAtLeast(0f)
  Canvas(modifier.height(156.dp)) {
    val top = 30.dp.toPx()
    val bottom = 34.dp.toPx()
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
        drawRect(Palette.segmentOff, Offset(x(start), top), Size((x(i - 1) - x(start)).coerceAtLeast(x(1)), h))
        start = -1
      }
    }
    // 지점 자리 세로선
    val sx = x(SpeedProfile.SPOT_INDEX)
    drawLine(Palette.indicator, Offset(sx, top - 6.dp.toPx()), Offset(sx, top + h), 2.dp.toPx())

    val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 9.dp.toPx()))
    if (last != null) for (seg in segments(last)) drawSeg(seg, ::x, ::y, LastLine, 4.dp.toPx(), dash)
    for (seg in segments(today.speeds)) drawSeg(seg, ::x, ::y, Palette.dark, 5.dp.toPx(), null)

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
      val t = measurer.measure("지난번 ${Math.round(lv)} km/h", soft)
      val lx = (x(lastLow) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
      val ly = (y(lv) + 10.dp.toPx()).coerceAtMost(top + h + 4.dp.toPx() - t.size.height)
      val overlap = lx < lowPos.x + lowT.size.width && lowPos.x < lx + t.size.width && ly < lowPos.y + lowT.size.height && lowPos.y < ly + t.size.height
      if (overlap) {
        val both = measurer.measure(androidx.compose.ui.text.buildAnnotatedString {
          withStyle(strong.toSpanStyle()) { append(todayText) }
          withStyle(soft.toSpanStyle()) { append("  ·  지난번 ${Math.round(lv)} km/h") }
        })
        drawText(both, topLeft = todayPos(both))
      } else {
        drawText(lowT, topLeft = lowPos)
        drawText(t, topLeft = Offset(lx, ly))
      }
    } else {
      drawText(lowT, topLeft = lowPos)
    }
    // 아래: 800m 전, 지점 이름
    val axisY = top + h + 8.dp.toPx()
    drawText(measurer.measure("${Review.BEFORE_M.toInt()}m 전", soft), topLeft = Offset(0f, axisY))
    val spotT = measurer.measure(spotName, soft.copy(fontWeight = FontWeight.SemiBold, color = Palette.textPrimary))
    drawText(spotT, topLeft = Offset((sx - spotT.size.width / 2).coerceAtMost(size.width - spotT.size.width), axisY))
  }
}

/** 이어서 무난했던 곳들은 한 줄로 묶는다 (예: "동수원IC 출구 · 연속 회전") */
private fun groupQuiet(rows: List<ReviewRow>): List<List<ReviewRow>> {
  val out = mutableListOf<MutableList<ReviewRow>>()
  for (r in rows) {
    val last = out.lastOrNull()?.last()
    val quiet = r.outcome == Outcome.FIRST_OK || r.outcome == Outcome.OK
    if (last != null && quiet && last.outcome == r.outcome) out.last() += r else out += mutableListOf(r)
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
