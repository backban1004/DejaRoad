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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dejaroad.app.BrakeEvent
import com.dejaroad.app.Outcome
import com.dejaroad.app.R
import com.dejaroad.app.Review
import com.dejaroad.app.ReviewRow
import com.dejaroad.app.RoadSession
import com.dejaroad.app.Tick
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 도착 후 복습: 어려운 곳마다 어떻게 지났는지, 급제동한 곳은 앞뒤 20초 속도 */
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
      BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 26.dp).clip(CardShape).background(Palette.surface)) {
          val cardH = maxHeight
        Column(
          Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = cardH).padding(horizontal = if (narrow) 28.dp else 36.dp),
          verticalArrangement = Arrangement.SpaceEvenly,
        ) {
          if (r.rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().heightIn(min = cardH), contentAlignment = Alignment.Center) { Text("이번 주행에서 지난 어려운 곳이 없어요", style = Type.bodyL, color = Palette.textSecondary) }
          }
          r.rows.forEachIndexed { i, row ->
            if (i > 0) Divider()
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.spot.name, style = Type.titleS, modifier = Modifier.weight(1f))
                StateBadge(row)
              }
              Text(Review.detail(row, r.endedAt), style = Type.bodyS, color = Palette.textTertiary, modifier = Modifier.padding(top = 8.dp))
              val trace = r.traces[row.spot.key]
              if (row.event != null && !trace.isNullOrEmpty()) SpeedGraph(trace, row.event, Modifier.fillMaxWidth().padding(top = 18.dp))
            }
          }
        }
      }
      if (hardName != null) {
        Text(fixObject("다음에 ${hardName}을 지나는 길이면 출발 전에 먼저 알려 드려요", hardName), style = Type.labelM, color = Palette.textSecondary, modifier = Modifier.padding(top = 18.dp, start = 4.dp))
      }
      Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        WideButton("확인", dark = true, modifier = Modifier.weight(1.3f)) { s.closeReview() }
        WideButton("이번 기록은 빼기", dark = false, modifier = Modifier.weight(1f)) { s.forgetLastTrip() }
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

/** 을/를을 받침에 맞춘다 */
private fun fixObject(text: String, name: String): String {
  val c = name.trim().lastOrNull() ?: return text
  val final = if (c in '가'..'힣') (c - '가') % 28 != 0 else c.uppercaseChar() in "LMNR" || c in "013678"
  return text.replace("${name}을", name + if (final) "을" else "를")
}

/** 급제동 앞뒤 20초 속도 곡선. 브레이크를 밟은 동안은 회색 띠, 처음 속도와 줄인 뒤 속도는 곡선 위에 적는다 */
@Composable
private fun SpeedGraph(trace: List<Tick>, event: BrakeEvent, modifier: Modifier = Modifier) {
  val measurer = rememberTextMeasurer()
  val labelStyle = Type.labelS.copy(fontWeight = FontWeight.SemiBold)
  val t0 = trace.first().atMillis
  val t1 = trace.last().atMillis.coerceAtLeast(t0 + 1)
  val hi = (trace.maxOf { it.speedKph } + 4f).coerceAtLeast(20f)
  val lo = (trace.minOf { it.speedKph } - 4f).coerceAtLeast(0f)
  // 브레이크를 뗀 때(급제동이 끝난 때)의 점: 문장의 '→ 41 km/h'와 같은 값
  val low = trace.minBy { kotlin.math.abs(it.atMillis - event.endMillis) }
  Column(modifier) {
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
      val top = 30.dp.toPx()
      val bottom = 30.dp.toPx()
      val h = size.height - top - bottom
      fun x(t: Long) = (t - t0).toFloat() / (t1 - t0) * size.width
      fun y(v: Float) = top + h - (v - lo) / (hi - lo) * h
      // 밟은 동안
      var start: Long? = null
      for ((i, p) in trace.withIndex()) {
        if (p.brake && start == null) start = p.atMillis
        val end = !p.brake || i == trace.lastIndex
        if (start != null && end) {
          drawRect(Palette.segmentOff, topLeft = Offset(x(start), top), size = Size(x(p.atMillis) - x(start), h))
          start = null
        }
      }
      val path = Path()
      trace.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.atMillis), y(p.speedKph)) else path.lineTo(x(p.atMillis), y(p.speedKph)) }
      drawPath(path, Palette.dark, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

      // 처음 속도: 곡선 시작점 위
      val first = trace.first()
      val startLabel = measurer.measure("${event.fromKph} km/h", labelStyle)
      drawText(startLabel, Palette.textPrimary, Offset(0f, (y(first.speedKph) - startLabel.size.height - 6.dp.toPx()).coerceAtLeast(0f)))
      // 줄인 뒤 속도: 그 점 바로 아래, 그래프 밖으로 나가지 않게
      val lowX = x(low.atMillis)
      val lowY = y(low.speedKph)
      drawCircle(Palette.dark, 7.dp.toPx(), Offset(lowX, lowY))
      val lowLabel = measurer.measure("${event.toKph} km/h", labelStyle)
      val lx = (lowX - lowLabel.size.width / 2f).coerceIn(0f, size.width - lowLabel.size.width)
      drawText(lowLabel, Palette.textPrimary, Offset(lx, (lowY + 10.dp.toPx()).coerceAtMost(size.height - lowLabel.size.height)))
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(Modifier.size(18.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(Palette.segmentOff))
      Text("브레이크 밟은 동안", style = Type.labelS, color = Palette.textSecondary)
      Spacer(width = 8.dp)
      Text("밟기 8초 전부터 12초 뒤까지 속도", style = Type.labelS, color = Palette.textSecondary, modifier = Modifier.weight(1f))
    }
  }
}
