package com.dejaroad.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dejaroad.app.HardSpot
import com.dejaroad.app.PastHard
import com.dejaroad.app.R
import com.dejaroad.app.RoadSession
import com.dejaroad.app.Script
import com.dejaroad.app.SpeedProfile

/**
 * 출발 전 예습: 고속도로 노선도처럼 세로 한 줄.
 * 왼쪽에 지금 위치부터의 거리(km), 가운데 줄 위에 지점(채운 네모 = 지난번 급제동, 빈 네모 = 처음),
 * 오른쪽에 그날 속도가 떨어진 모양. 넓은 창에서는 오른쪽 칸에 음성 안내 거리와 버튼을 둔다.
 * 다 듣거나 '바로 출발'을 누르면 목록을 접고 출발 대기 안내판(주행 화면과 같은 모양)을 보인다.
 */
@Composable
fun PreviewPanel(s: RoadSession) {
  val spots = s.upcoming
  if (s.previewDone && spots.isNotEmpty()) {
    DrivingBoard(s, ready = true)
    return
  }
  BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background)) {
    val narrow = maxWidth < NARROW
    val side = if (narrow) 40.dp else 56.dp
    Column(Modifier.fillMaxSize()) {
      RouteHeader(s, narrow, side)
      Divider()
      if (spots.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
          Text(if (s.destination == null) "내비 경로를 기다리는 중" else "합류·분기·출구 없는 길이에요", style = Type.bodyL, color = Palette.textSecondary)
        }
        return@Column
      }
      if (narrow) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 32.dp, top = 20.dp, bottom = 12.dp)) {
          Strip(s, spots, narrow = true)
        }
        Text(timingNote(s, spots), style = Type.labelS.copy(fontSize = 22.sp, lineHeight = 28.sp), color = Palette.textSecondary, modifier = Modifier.padding(start = side, end = side, bottom = 16.dp))
        Row(Modifier.fillMaxWidth().padding(start = side, end = side, bottom = 36.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          ListenButton(s, Modifier.weight(1.3f))
          WideButton("바로 출발", dark = false, modifier = Modifier.weight(1f)) { s.skipPreview() }
        }
      } else {
        Row(Modifier.fillMaxWidth().weight(1f)) {
          Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = side, top = 24.dp, bottom = 24.dp)) {
            Strip(s, spots, narrow = false)
          }
          Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.divider))
          VoiceSide(s, spots, Modifier.width(560.dp).fillMaxHeight().padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 36.dp))
        }
      }
    }
  }
}

/** 제목: 목적지 이름(문장 대신 값), 오른쪽에 남은 시간과 거리 */
@Composable
private fun RouteHeader(s: RoadSession, narrow: Boolean, side: Dp) {
  val d = s.destination
  val style = Type.headlineS.copy(fontSize = if (narrow) 40.sp else 48.sp, lineHeight = if (narrow) 48.sp else 56.sp, letterSpacing = (-1).sp)
  val unit = SpanStyle(fontSize = if (narrow) 20.sp else 24.sp, fontWeight = FontWeight.Medium, color = Palette.textSecondary, letterSpacing = 0.sp)
  Row(Modifier.fillMaxWidth().padding(start = side, end = side, top = if (narrow) 32.dp else 40.dp, bottom = if (narrow) 24.dp else 28.dp), verticalAlignment = Alignment.Bottom) {
    Text(d?.name ?: "경로 없음", style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    val min = s.minutesToDestination()
    val left = s.remainingM()
    if (min != null && left != null) {
      Text(
        buildAnnotatedString {
          append("$min")
          withStyle(unit) { append(" 분   ") }
          append("%.1f".format(left / 1000))
          withStyle(unit) { append(" km") }
        },
        style = style.copy(fontSize = if (narrow) 36.sp else 48.sp),
        modifier = Modifier.padding(start = 24.dp),
      )
    }
  }
}

private enum class Node { START, PAST, NEW, END }

/** 노선도: 출발 → 지점들 → 도착 */
@Composable
private fun Strip(s: RoadSession, spots: List<HardSpot>, narrow: Boolean) {
  val kmW = if (narrow) 112.dp else 132.dp
  val edgeStyle = Type.bodyS.copy(fontSize = 28.sp, lineHeight = 40.sp)
  StripRow("0", Node.START, first = true, last = false, small = true, kmW = kmW, minH = 72.dp) {
    Text("출발", style = edgeStyle, color = Palette.textSecondary)
  }
  for (spot in spots) {
    val past = s.pastOf(spot)
    val km = "%.1f".format((spot.atM - s.traveledM).coerceAtLeast(0.0) / 1000)
    StripRow(km, if (past != null) Node.PAST else Node.NEW, first = false, last = false, small = false, kmW = kmW, minH = if (narrow) 176.dp else 196.dp) {
      Column(Modifier.weight(1f).padding(top = 4.dp, end = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          Symbol(spotIcon(spot), 40.dp)
          Text(
            buildAnnotatedString {
              append(Script.title(spot))
              val tag = Script.kindTag(spot)
              if (tag.isNotEmpty()) withStyle(SpanStyle(fontSize = if (narrow) 26.sp else 30.sp, fontWeight = FontWeight.Medium, color = Palette.textTertiary, letterSpacing = 0.sp)) { append("  $tag") }
            },
            style = Type.titleM.copy(fontSize = if (narrow) 32.sp else 36.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
          )
        }
        Text(Script.cue(spot), style = Type.bodyS.copy(fontSize = if (narrow) 24.sp else 26.sp, lineHeight = 34.sp), color = Palette.textSecondary, modifier = Modifier.padding(start = 54.dp, top = 8.dp))
        if (narrow && past != null) {
          Text("${Script.pastLine(past)} 급제동", style = Type.labelM.copy(lineHeight = 32.sp), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 54.dp, top = 4.dp))
        }
      }
      if (!narrow) {
        Column(Modifier.width(230.dp).padding(top = 6.dp)) {
          if (past != null) {
            DropMark(past, Modifier.size(150.dp, 52.dp))
            Text(
              buildAnnotatedString {
                append("${Script.date(past.atMillis)} ")
                withStyle(SpanStyle(color = Palette.textPrimary, fontWeight = FontWeight.Bold)) { append("${past.fromKph}→${past.toKph}") }
                append(" km/h")
              },
              style = Type.labelS.copy(fontSize = 22.sp, lineHeight = 28.sp),
              color = Palette.textSecondary,
              modifier = Modifier.padding(top = 8.dp),
            )
          } else {
            Text(if (s.memory.passCount(spot.key) > 0) "급제동 없음" else "처음", style = Type.labelS.copy(fontSize = 22.sp, lineHeight = 28.sp), color = Palette.textSecondary, modifier = Modifier.padding(top = 14.dp))
          }
        }
      }
    }
  }
  val endKm = s.remainingM()?.let { "%.1f".format(it / 1000) } ?: ""
  StripRow(endKm, Node.END, first = false, last = true, small = true, kmW = kmW, minH = 72.dp) {
    Text(s.destination?.name ?: "도착", style = edgeStyle, fontWeight = FontWeight.Bold)
  }
}

/** 노선도 한 줄: 거리 | 줄과 표시 | 내용. 줄은 첫 줄은 표시 아래로만, 끝 줄은 표시 위로만 긋는다 */
@Composable
private fun StripRow(km: String, node: Node, first: Boolean, last: Boolean, small: Boolean, kmW: Dp, minH: Dp, content: @Composable RowScope.() -> Unit) {
  val kmStyle =
    if (small) Type.bodyS.copy(fontSize = 26.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
    else Type.headlineS.copy(fontSize = if (kmW < 130.dp) 36.sp else 44.sp, lineHeight = 52.sp, letterSpacing = (-1).sp)
  Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = minH)) {
    Text(km, style = kmStyle, color = if (small) Palette.textSecondary else Palette.textPrimary, textAlign = TextAlign.End, modifier = Modifier.width(kmW).padding(end = 20.dp))
    Box(
      Modifier.width(56.dp).fillMaxHeight().drawBehind {
        val ink = Palette.textPrimary
        val cx = 28.dp.toPx()
        val cy = (if (small) 20.dp else 26.dp).toPx()
        val w = 4.dp.toPx()
        val top = if (first) cy else 0f
        val bottom = if (last) cy else size.height
        drawRect(ink, Offset(cx - w / 2, top), Size(w, bottom - top))
        val half = 14.dp.toPx()
        when (node) {
          Node.PAST -> drawRect(ink, Offset(cx - half, cy - half), Size(half * 2, half * 2))
          Node.NEW -> {
            drawRect(Palette.background, Offset(cx - half, cy - half), Size(half * 2, half * 2))
            drawRect(ink, Offset(cx - half + w / 2, cy - half + w / 2), Size(half * 2 - w, half * 2 - w), style = Stroke(w))
          }
          Node.START -> {
            drawCircle(Palette.background, 12.dp.toPx(), Offset(cx, cy))
            drawCircle(ink, 12.dp.toPx() - w / 2, Offset(cx, cy), style = Stroke(w))
          }
          Node.END -> drawCircle(ink, 12.dp.toPx(), Offset(cx, cy))
        }
      },
    )
    Row(Modifier.weight(1f).padding(start = 16.dp), content = content)
  }
}

/** 그날 속도가 떨어진 모양. 저장된 속도 곡선이 있으면 그것을, 없으면 처음·가장 낮은 속도로 그린다 */
@Composable
private fun DropMark(past: PastHard, modifier: Modifier) {
  val profile = past.profile.takeIf { it.size == SpeedProfile.SIZE && it.count { v -> v != null } >= 2 }
  Canvas(modifier) {
    val pts: List<Pair<Float, Float>> =
      profile?.mapIndexedNotNull { i, v -> v?.let { i.toFloat() / (profile.size - 1) to it } }
        ?: listOf(0f to past.fromKph.toFloat(), 0.45f to past.fromKph.toFloat(), 0.62f to past.toKph.toFloat(), 1f to past.toKph + (past.fromKph - past.toKph) * 0.55f)
    val hi = maxOf(80f, pts.maxOf { it.second })
    fun x(f: Float) = f * size.width
    fun y(v: Float) = size.height - 4.dp.toPx() - v / hi * (size.height - 8.dp.toPx())
    val path = Path()
    pts.forEachIndexed { k, (f, v) -> if (k == 0) path.moveTo(x(f), y(v)) else path.lineTo(x(f), y(v)) }
    drawPath(path, Palette.textPrimary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    val low = pts.minBy { it.second }
    drawCircle(Palette.textPrimary, 5.dp.toPx(), Offset(x(low.first), y(low.second)))
  }
}

/** 넓은 창 오른쪽: 언제 소리로 알려 주는지(3km·1.5km·1km)와 버튼 */
@Composable
private fun VoiceSide(s: RoadSession, spots: List<HardSpot>, modifier: Modifier) {
  val pastCount = spots.count { s.pastOf(it) != null }
  Column(modifier) {
    Text("음성 안내", style = Type.labelM, color = Palette.textSecondary)
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
      if (pastCount > 0) Ladder("3 km", "급제동했던 ${pastCount}곳", dim = false)
      Ladder("1.5 km", "${spots.size}곳 모두", dim = false)
      Ladder("1 km", "내비 안내", dim = true)
    }
    Box(Modifier.weight(1f))
    ListenButton(s, Modifier.fillMaxWidth())
    WideButton("바로 출발", dark = false, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { s.skipPreview() }
  }
}

@Composable
private fun Ladder(at: String, what: String, dim: Boolean) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(at, style = Type.titleS, fontWeight = FontWeight.Bold, color = if (dim) Palette.onDarkSecondary else Palette.textPrimary, modifier = Modifier.width(120.dp))
    Text(what, style = Type.bodyM, color = if (dim) Palette.onDarkSecondary else Palette.textTertiary)
  }
}

@Composable
private fun ListenButton(s: RoadSession, modifier: Modifier) {
  if (s.voice.talking) {
    WideButton("멈추기", dark = true, modifier = modifier, icon = R.drawable.ic_close) { s.stopPreview() }
  } else {
    WideButton("미리 듣기 · 1분", dark = true, modifier = modifier, icon = R.drawable.ic_volume_up) { s.playPreview() }
  }
}

private fun timingNote(s: RoadSession, spots: List<HardSpot>): String =
  if (spots.any { s.pastOf(it) != null }) "1.5 km 앞 음성 안내 · 급제동했던 곳은 3 km 앞부터" else "1.5 km 앞 음성 안내"
