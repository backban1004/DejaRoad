package com.dejaroad.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dejaroad.app.HardSpot
import com.dejaroad.app.R
import com.dejaroad.app.RoadSession
import com.dejaroad.app.Script
import kotlin.math.roundToInt

/** 주행 중: 앱 화면 전체를 쓰는 검은 안내판. 조작은 없다 */
@Composable
fun DrivingPanel(s: RoadSession) = DrivingBoard(s, ready = false)

/**
 * 검은 안내판. 가장 크게 보이는 것은 남은 거리, 그다음이 지점 이름, 그다음이 할 일.
 * 지난번 급제동한 곳이면 그날 기록을 테두리 칸에 한 줄. 아래에 3km부터 차오르는 막대와 다음 지점.
 * [ready]면 출발 전(예습을 마친 뒤)이라 다음 지점 대신 '목록 다시 보기' 버튼을 둔다.
 */
@Composable
fun DrivingBoard(s: RoadSession, ready: Boolean) {
  val next = s.nextSpot
  val d = s.destination
  BoxWithConstraints(Modifier.fillMaxSize().background(Palette.board)) {
    val narrow = maxWidth < NARROW
    val side = if (narrow) 40.dp else 56.dp
    Column(Modifier.fillMaxSize()) {
      Row(Modifier.fillMaxWidth().padding(start = side, end = side, top = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        if (d != null) {
          Text(d.name, style = Type.labelM.copy(fontSize = 26.sp, lineHeight = 32.sp), color = Palette.onDarkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
          s.minutesToDestination()?.let { Text("${it}분", style = Type.labelM.copy(fontSize = 26.sp, lineHeight = 32.sp), fontWeight = FontWeight.SemiBold, color = Palette.onDark, modifier = Modifier.padding(start = 10.dp)) }
        }
        Box(Modifier.weight(1f))
        if (ready) Text("출발하면 안내 시작", style = Type.labelM.copy(fontSize = 26.sp, lineHeight = 32.sp), color = Palette.onDarkSecondary)
      }
      Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = side), contentAlignment = Alignment.CenterStart) {
        when {
          next == null -> Text(if (s.spots.isEmpty()) "내비 경로가 아직 없어요" else "도착까지 남은 지점이 없어요", style = Type.headlineS, color = Palette.onDark)
          narrow ->
            Column {
              Symbol(spotIcon(next), 160.dp, Palette.onDark)
              BoardText(s, next, narrow = true, modifier = Modifier.padding(top = 28.dp))
            }
          else ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(64.dp)) {
              Symbol(spotIcon(next), 300.dp, Palette.onDark)
              BoardText(s, next, narrow = false)
            }
        }
      }
      if (next != null) {
        ApproachBar(s, next, narrow, Modifier.padding(horizontal = side))
        Divider(Modifier.padding(top = 20.dp), color = Palette.boardLine)
        if (ready) {
          Row(Modifier.fillMaxWidth().padding(start = side, end = side, top = 24.dp, bottom = 36.dp), horizontalArrangement = Arrangement.End) {
            WideButton("목록 다시 보기", dark = false, modifier = Modifier.width(if (narrow) 300.dp else 360.dp)) { s.reopenPreview() }
          }
        } else {
          NextRow(s, next, Modifier.padding(start = side, end = side, top = 28.dp, bottom = 36.dp))
        }
      }
    }
  }
}

/** 거리(가장 크게) → 이름과 종류 → 할 일 → 지난 기록 */
@Composable
private fun BoardText(s: RoadSession, next: HardSpot, narrow: Boolean, modifier: Modifier = Modifier) {
  val ahead = (next.atM - s.traveledM).coerceAtLeast(0.0)
  val past = s.pastOf(next)
  Column(modifier) {
    val (num, unit) = if (ahead >= 1000) "%.1f".format(ahead / 1000) to "km" else "${(ahead / 10).roundToInt() * 10}" to "m"
    Text(
      buildAnnotatedString {
        append(num)
        withStyle(SpanStyle(fontSize = if (narrow) 44.sp else 64.sp, fontWeight = FontWeight.SemiBold, color = Palette.onDarkSecondary, letterSpacing = 0.sp)) { append(" $unit") }
      },
      style = Type.headlineL.copy(fontSize = if (narrow) 150.sp else 220.sp, lineHeight = if (narrow) 150.sp else 200.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = if (narrow) (-5).sp else (-8).sp),
      color = Palette.onDark,
    )
    Text(
      buildAnnotatedString {
        append(Script.title(next))
        val tag = Script.kindTag(next)
        if (tag.isNotEmpty()) withStyle(SpanStyle(fontSize = if (narrow) 30.sp else 40.sp, fontWeight = FontWeight.Medium, color = Palette.onDarkSecondary, letterSpacing = (-0.5).sp)) { append("  $tag") }
      },
      style = Type.headlineL.copy(fontSize = if (narrow) 56.sp else 80.sp, lineHeight = if (narrow) 66.sp else 92.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp),
      color = Palette.onDark,
      modifier = Modifier.padding(top = 20.dp),
    )
    Text(
      if (ahead <= RoadSession.NEAR_M) Script.now(next) else Script.cue(next),
      style = Type.bodyL.copy(fontSize = if (narrow) 32.sp else 40.sp, lineHeight = if (narrow) 42.sp else 52.sp),
      color = Palette.onBoardText,
      modifier = Modifier.padding(top = 12.dp),
    )
    if (past != null) {
      Row(
        Modifier.padding(top = if (narrow) 28.dp else 40.dp).border(2.dp, Palette.onDark, RoundedCornerShape(8.dp)).padding(start = 20.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Symbol(R.drawable.ic_history, if (narrow) 30.dp else 36.dp, Palette.onDark)
        Text(
          if (narrow) "${Script.date(past.atMillis)} ${past.fromKph} → ${past.toKph} 급제동" else "${Script.date(past.atMillis)} 여기서 ${past.fromKph} → ${past.toKph} km/h 급제동",
          style = Type.bodyL.copy(fontSize = if (narrow) 26.sp else 30.sp, lineHeight = 36.sp),
          fontWeight = FontWeight.SemiBold,
          color = Palette.onDark,
        )
      }
    }
  }
}

/** 맨 아래: 그다음 지점 */
@Composable
private fun NextRow(s: RoadSession, next: HardSpot, modifier: Modifier) {
  val after = s.spots.firstOrNull { it.atM > next.atM } ?: return
  val style = Type.bodyL.copy(fontSize = 30.sp, lineHeight = 38.sp)
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
    Symbol(spotIcon(after), 40.dp, Palette.onDarkSecondary)
    Text("다음", style = style, color = Palette.onDarkSecondary)
    Text(after.name, style = style, fontWeight = FontWeight.Bold, color = Palette.onDark, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Text("${Script.km((after.atM - s.traveledM).coerceAtLeast(0.0))} · ${s.minutesTo(after)}분", style = style, color = Palette.onDarkSecondary)
  }
}

/**
 * 다가오는 막대: 3km 앞부터 차오르고, 지난 기록 안내(3km)·데자로드 안내(1.5km)·내비 안내(1km) 자리에 눈금.
 * 지난 눈금은 희게, 아직인 눈금은 어둡게. 데자로드가 내비보다 먼저 말한다는 것이 한눈에 보인다.
 */
@Composable
private fun ApproachBar(s: RoadSession, next: HardSpot, narrow: Boolean, modifier: Modifier = Modifier) {
  val ahead = (next.atM - s.traveledM).coerceAtLeast(0.0)
  val window = RoadSession.EARLY_M
  val progress = ((window - ahead) / window).toFloat().coerceIn(0f, 1f)
  val nearF = ((window - RoadSession.NEAR_M) / window).toFloat()
  val naviF = ((window - NAVI_M) / window).toFloat()
  val past = s.pastOf(next) != null
  val measurer = rememberTextMeasurer()
  val soft = Type.labelM.copy(color = Palette.onDarkSecondary)
  val bold = SpanStyle(color = Palette.onDark, fontWeight = FontWeight.SemiBold)
  fun label(at: String, what: String) =
    buildAnnotatedString {
      withStyle(bold) { append(at) }
      if (what.isNotEmpty()) append("  $what")
    }
  Canvas(modifier.fillMaxWidth().height(84.dp)) {
    val cy = 20.dp.toPx()
    val h = 12.dp.toPx()
    val r = CornerRadius(h / 2)
    drawRoundRect(Palette.boardTrack, Offset(0f, cy - h / 2), Size(size.width, h), r)
    if (progress > 0f) drawRoundRect(Palette.onDark, Offset(0f, cy - h / 2), Size(size.width * progress, h), r)
    val tw = 3.dp.toPx()
    for (f in listOf(0f, nearF, naviF, 1f)) {
      val x = (size.width * f).coerceAtMost(size.width - tw)
      drawRect(if (progress >= f && f < 1f) Palette.onDark else Palette.boardTickOff, Offset(x, 0f), Size(tw, 40.dp.toPx()))
    }
    val y = 50.dp.toPx()
    val pad = 8.dp.toPx()
    drawText(measurer.measure(label("3 km", if (past && !narrow) "지난 기록" else ""), soft), topLeft = Offset(0f, y))
    drawText(measurer.measure(label("1.5 km", if (narrow) "" else "데자로드"), soft), topLeft = Offset(size.width * nearF - pad, y))
    drawText(measurer.measure(label("1 km", if (narrow) "" else "내비"), soft), topLeft = Offset(size.width * naviF - pad, y))
    if (!narrow) {
      val end = measurer.measure(Script.title(next), soft)
      drawText(end, topLeft = Offset(size.width - end.size.width, y))
    }
  }
}

/** 내비가 스스로 안내하는 거리 (국내 내비의 흔한 1km 안내. 에뮬레이터 내비로는 아직 확인 못 함) */
private const val NAVI_M = 1_000.0
