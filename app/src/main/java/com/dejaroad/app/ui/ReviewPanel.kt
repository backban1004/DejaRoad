package com.dejaroad.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dejaroad.app.BrakeEvent
import com.dejaroad.app.Outcome
import com.dejaroad.app.Review
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
  Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 36.dp)) {
    AppHeader("오늘 $time 도착")
    Text(r.headline, style = Type.titleL, modifier = Modifier.padding(top = 22.dp))
    Column(Modifier.fillMaxWidth().weight(1f).padding(top = 26.dp).clip(CardShape).background(Palette.surface).padding(horizontal = 36.dp), verticalArrangement = Arrangement.SpaceAround) {
      if (r.rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("이번 주행에서 지난 어려운 곳이 없어요", style = Type.bodyL, color = Palette.textSecondary) }
      }
      r.rows.forEachIndexed { i, row ->
        if (i > 0) Divider()
        Row(Modifier.fillMaxWidth().padding(vertical = 26.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(row.spot.name, style = Type.titleS)
            Text(Review.detail(row, r.endedAt), style = Type.bodyS, color = Palette.textTertiary, modifier = Modifier.padding(top = 6.dp))
            val trace = r.traces[row.spot.key]
            if (row.event != null && !trace.isNullOrEmpty()) SpeedGraph(trace, row.event, Modifier.padding(top = 18.dp))
          }
          Text(
            Review.state(row),
            style = Type.bodyS,
            fontWeight = if (row.outcome == Outcome.HARD || row.outcome == Outcome.BETTER) FontWeight.SemiBold else FontWeight.Medium,
            color = if (row.outcome == Outcome.HARD || row.outcome == Outcome.BETTER) Palette.textPrimary else Palette.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 220.dp).padding(start = 32.dp),
          )
        }
      }
    }
    if (hardName != null) Text("다음에 ${hardName}을 지나는 길이면 출발 전에 먼저 알려 드려요".let { fixObject(it, hardName) }, style = Type.labelM, color = Palette.onDarkTertiary, modifier = Modifier.padding(top = 18.dp, start = 4.dp))
    Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
      WideButton("확인", dark = true, modifier = Modifier.weight(1.3f)) { s.closeReview() }
      WideButton("이번 기록은 빼기", dark = false, modifier = Modifier.weight(1f)) { s.forgetLastTrip() }
    }
  }
}

/** 을/를을 받침에 맞춘다 */
private fun fixObject(text: String, name: String): String {
  val c = name.trim().lastOrNull() ?: return text
  val final = if (c in '가'..'힣') (c - '가') % 28 != 0 else c.uppercaseChar() in "LMNR" || c in "013678"
  return text.replace("${name}을", name + if (final) "을" else "를")
}

/** 급제동 앞뒤 20초 속도 곡선. 브레이크를 밟은 동안은 회색 띠 */
@Composable
private fun SpeedGraph(trace: List<Tick>, event: BrakeEvent, modifier: Modifier = Modifier) {
  val t0 = trace.first().atMillis
  val t1 = trace.last().atMillis.coerceAtLeast(t0 + 1)
  val hi = (trace.maxOf { it.speedKph } + 6f).coerceAtLeast(20f)
  val lo = (trace.minOf { it.speedKph } - 10f).coerceAtLeast(0f)
  Column(modifier) {
    Row(verticalAlignment = Alignment.Bottom) {
      Canvas(Modifier.width(900.dp).height(130.dp)) {
        fun x(t: Long) = (t - t0).toFloat() / (t1 - t0) * size.width
        fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
        // 밟은 동안
        var start: Long? = null
        for ((i, p) in trace.withIndex()) {
          if (p.brake && start == null) start = p.atMillis
          val end = !p.brake || i == trace.lastIndex
          if (start != null && end) {
            drawRect(Palette.segmentOff, topLeft = Offset(x(start), 0f), size = Size(x(p.atMillis) - x(start), size.height))
            start = null
          }
        }
        val path = Path()
        trace.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.atMillis), y(p.speedKph)) else path.lineTo(x(p.atMillis), y(p.speedKph)) }
        drawPath(path, Palette.dark, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
      }
      Text("${event.toKph} km/h", style = Type.labelS, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp))
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(Modifier.size(18.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(Palette.segmentOff))
      Text("브레이크 밟은 동안", style = Type.labelS, color = Palette.textSecondary)
      Spacer(width = 18.dp)
      Text("밟기 8초 전부터 12초 뒤까지 속도, 처음 ${event.fromKph} km/h", style = Type.labelS, color = Palette.textSecondary)
    }
  }
}
