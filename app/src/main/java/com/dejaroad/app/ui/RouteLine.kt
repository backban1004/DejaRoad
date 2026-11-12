package com.dejaroad.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dejaroad.app.HardSpot

/** 경로 한 줄에 올릴 어려운 곳 하나: 번호, 위치(0~1), 지난번 급제동했는지, 아래 글 */
data class RouteMark(val number: Int, val fraction: Float, val past: Boolean, val name: String, val minutes: Int)

/** 어려운 곳 이름을 경로 한 줄용으로 줄인다 ("판교IC 합류" → "판교IC") */
fun shortName(spot: HardSpot): String = spot.name.removeSuffix(" 합류").removeSuffix(" 출구")

/**
 * 출발부터 도착까지 한 줄. 어려운 곳은 번호 칸으로 올리고 지난번 급제동한 곳은 짙게 채운다.
 * 가까이 붙은 곳은 번호 칸을 나란히 묶는다. 글이 겹치면 번호만 남긴다.
 */
@Composable
fun RouteLine(marks: List<RouteMark>, endName: String?, dark: Boolean, showLabels: Boolean, modifier: Modifier = Modifier) {
  val measurer = rememberTextMeasurer()
  val on = if (dark) Palette.onDark else Palette.dark
  val off = if (dark) Color(0xFF3A3C40) else Palette.segmentOff
  val bg = if (dark) Palette.dark else Palette.surface
  val ink = if (dark) Palette.onDark else Palette.textPrimary
  val sub = if (dark) Palette.onDarkSecondary else Palette.textSecondary
  val labelStyle = Type.labelS
  Canvas(modifier.fillMaxWidth().height(if (showLabels) 112.dp else 72.dp)) {
    val side = 56.dp.toPx()
    val left = side
    val right = size.width - side
    val lineY = 36.dp.toPx()
    val badge = 48.dp.toPx()
    val gap = 6.dp.toPx()
    val labelY = 74.dp.toPx()
    fun x(f: Float) = left + (right - left) * f.coerceIn(0f, 1f)

    drawRoundRect(off, Offset(left, lineY - 3.dp.toPx()), Size(right - left, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
    // 출발(채운 점), 도착(빈 점)
    drawCircle(on, 14.dp.toPx(), Offset(left, lineY))
    drawCircle(on, 14.dp.toPx(), Offset(right, lineY))
    drawCircle(bg, 8.dp.toPx(), Offset(right, lineY))

    // 가까운 곳끼리 묶기
    val groups = mutableListOf<MutableList<RouteMark>>()
    for (m in marks.sortedBy { it.fraction }) {
      val last = groups.lastOrNull()
      if (last != null && x(m.fraction) - x(last.last().fraction) < badge + gap) last += m else groups += mutableListOf(m)
    }
    val placed = mutableListOf<ClosedFloatingPointRange<Float>>()
    fun label(t: TextLayoutResult, cx: Float, color: Color, force: Boolean) {
      val w = t.size.width.toFloat()
      val lx = (cx - w / 2).coerceIn(0f, size.width - w)
      val span = (lx - 12.dp.toPx())..(lx + w + 12.dp.toPx())
      if (!force && placed.any { it.start < span.endInclusive && span.start < it.endInclusive }) return
      placed += span
      drawText(t, color, Offset(lx, labelY))
    }
    if (showLabels) {
      label(measurer.measure("출발", labelStyle), left, sub, true)
      if (endName != null) label(measurer.measure(endName, labelStyle), right, sub, true)
    }
    for (g in groups) {
      val cx = g.map { x(it.fraction) }.average().toFloat()
      val total = g.size * badge + (g.size - 1) * gap
      g.forEachIndexed { i, m ->
        val bx = cx - total / 2 + i * (badge + gap)
        drawBadge(m, Offset(bx, lineY - badge / 2), badge, on, bg, measurer)
      }
      if (showLabels) {
        val name = if (g.size == 1) g[0].name else g.joinToString("·") { it.name }
        label(measurer.measure("$name ${g[0].minutes}분", labelStyle.copy(fontWeight = FontWeight.SemiBold, color = ink)), cx, ink, false)
      }
    }
  }
}

private fun DrawScope.drawBadge(m: RouteMark, at: Offset, size: Float, on: Color, bg: Color, measurer: androidx.compose.ui.text.TextMeasurer) {
  val r = CornerRadius(14.dp.toPx())
  if (m.past) {
    drawRoundRect(on, at, Size(size, size), r)
  } else {
    drawRoundRect(bg, at, Size(size, size), r)
    val w = 3.dp.toPx()
    drawRoundRect(on, Offset(at.x + w / 2, at.y + w / 2), Size(size - w, size - w), CornerRadius(r.x - w / 2), style = Stroke(w))
  }
  val t = measurer.measure("${m.number}", Type.labelM.copy(fontWeight = FontWeight.Bold))
  drawText(t, if (m.past) bg else on, Offset(at.x + (size - t.size.width) / 2, at.y + (size - t.size.height) / 2))
}
