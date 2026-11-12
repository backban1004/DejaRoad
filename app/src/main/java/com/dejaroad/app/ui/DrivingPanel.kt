package com.dejaroad.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dejaroad.app.HardSpot
import com.dejaroad.app.R
import com.dejaroad.app.RoadSession
import com.dejaroad.app.Script
import kotlin.math.roundToInt

/**
 * 주행 중: 다음 어려운 곳 하나만 큰 글씨로. 조작은 없다.
 * 가장 크게 보이는 것은 '얼마나 남았는지'(거리), 그다음이 이름, 그다음이 지금 할 일.
 */
@Composable
fun DrivingPanel(s: RoadSession) {
  val next = s.nextSpot
  val d = s.destination
  val status = d?.let { dd -> s.minutesToDestination()?.let { "${dd.name}까지 ${it}분" } ?: dd.name }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val narrow = maxWidth < NARROW
    val side = if (narrow) 32.dp else 40.dp
    Column(Modifier.fillMaxSize().padding(start = side, end = side, top = 32.dp, bottom = 36.dp)) {
      AppHeader(status)
      Column(Modifier.fillMaxWidth().weight(1f).padding(top = 26.dp).clip(CardShape).background(Palette.dark)) {
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = if (narrow) 48.dp else 72.dp), contentAlignment = Alignment.CenterStart) {
          if (next == null) {
            Column {
              Text(if (s.spots.isEmpty()) "경로 안내를 켜면 어려운 곳을 미리 알려 드려요" else "어려운 곳을 모두 지났어요", style = Type.headlineS, color = Palette.onDark)
              if (s.spots.isNotEmpty()) Text("편하게 도착하세요", style = Type.bodyL.copy(fontSize = 36.sp, lineHeight = 48.sp), color = Palette.onDarkSecondary, modifier = Modifier.padding(top = 22.dp))
            }
          } else if (narrow) {
            Column {
              SpotTile(next, 160.dp, strong = false, onDark = true)
              NextText(s, next, Modifier.padding(top = 40.dp))
            }
          } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(72.dp)) {
              SpotTile(next, 240.dp, strong = false, onDark = true)
              NextText(s, next)
            }
          }
        }
        if (next != null) {
          ApproachBar(s, next, narrow, Modifier.padding(start = if (narrow) 48.dp else 72.dp, end = if (narrow) 40.dp else 56.dp, bottom = 36.dp))
          Divider(color = Palette.onDarkLine)
          Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Symbol(R.drawable.ic_volume_up, 30.dp, Palette.onDark)
            Text(
              if (spoken(s, next)) "소리로도 말씀드렸어요" else "화면을 보지 않아도 소리로 알려 드려요",
              style = Type.labelM,
              color = Palette.onDarkSecondary,
              modifier = Modifier.weight(1f),
            )
            if (s.pastOf(next) != null) {
              Symbol(R.drawable.ic_history, 28.dp, Palette.onDark)
              Text("지난번 급히 줄였던 곳", style = Type.labelM, color = Palette.onDark, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
      val after = next?.let { n -> s.spots.firstOrNull { it.atM > n.atM } }
      if (after != null) {
        Row(Modifier.padding(top = 22.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          Symbol(R.drawable.ic_alt_route, 30.dp, Palette.textSecondary)
          Text("다음", style = Type.labelM, fontWeight = FontWeight.SemiBold)
          Text("${after.name}, ${s.minutesTo(after)}분 뒤", style = Type.labelM, color = Palette.textSecondary)
        }
      }
    }
  }
}

/** 거리(가장 크게) → 이름 → 지금 할 일 */
@Composable
private fun NextText(s: RoadSession, next: HardSpot, modifier: Modifier = Modifier) {
  val ahead = (next.atM - s.traveledM).coerceAtLeast(0.0)
  Column(modifier) {
    val (num, unit) = if (ahead >= 1000) "%.1f".format(ahead / 1000) to "km 앞" else "${(ahead / 10).roundToInt() * 10}" to "m 앞"
    Text(
      buildAnnotatedString {
        append(num)
        withStyle(SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Palette.onDarkSecondary, letterSpacing = 0.sp)) { append(" $unit") }
      },
      style = Type.headlineL.copy(fontSize = 112.sp, lineHeight = 120.sp, letterSpacing = (-2).sp),
      color = Palette.onDark,
    )
    Text(next.name, style = Type.headlineS.copy(fontSize = 56.sp, lineHeight = 68.sp), color = Palette.onDark, modifier = Modifier.padding(top = 10.dp))
    Text(
      when {
        ahead <= RoadSession.NEAR_M -> Script.now(next)
        s.pastOf(next) != null && ahead <= RoadSession.EARLY_M -> "지난번 급히 줄였던 곳이에요. 미리 준비해요"
        else -> "1.5km 전에 소리로 알려 드려요"
      },
      style = Type.bodyL.copy(fontSize = 36.sp, lineHeight = 48.sp),
      color = Palette.button,
      modifier = Modifier.padding(top = 22.dp),
    )
  }
}

/** 이 곳을 이미 소리로 알렸는지 (1.5km 안, 또는 지난번 급제동한 곳은 3km 안) */
private fun spoken(s: RoadSession, next: HardSpot): Boolean {
  val ahead = next.atM - s.traveledM
  return ahead <= RoadSession.NEAR_M || (s.pastOf(next) != null && ahead <= RoadSession.EARLY_M)
}

/**
 * 다가오는 막대: 3km 전부터 차오르고, 지난 기록 안내(3km)·데자로드 안내(1.5km)·내비 안내(1km) 자리에 눈금.
 * 데자로드가 내비보다 먼저 말한다는 것이 한눈에 보인다. 끝에는 그 곳의 길 모양 칸.
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
  val base = Type.labelM.copy(color = Palette.onDarkSecondary)
  val strong = base.copy(color = Palette.onDark, fontWeight = FontWeight.SemiBold)
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
    Canvas(Modifier.weight(1f).height(84.dp)) {
      val barY = 26.dp.toPx()
      val h = 20.dp.toPx()
      val r = CornerRadius(h / 2)
      drawRoundRect(Color(0xFF3A3C40), Offset(0f, barY - h / 2), Size(size.width, h), r)
      if (progress > 0f) drawRoundRect(Palette.onDark, Offset(0f, barY - h / 2), Size(size.width * progress, h), r)
      val tickColor = Palette.onDarkSecondary
      for (f in listOf(0f, nearF, naviF)) {
        drawRoundRect(tickColor, Offset(size.width * f - 2.dp.toPx(), barY - 20.dp.toPx()), Size(4.dp.toPx(), 40.dp.toPx()), CornerRadius(2.dp.toPx()))
      }
      val y = barY + 24.dp.toPx()
      // 3km: 왼쪽 맞춤, 1.5km: 눈금에서 끝나게, 1km: 눈금에서 시작
      val startText = if (past && !narrow) "3 km · 지난 기록 안내" else "3 km"
      drawText(measurer.measure(startText, if (past && ahead <= window) strong else base), topLeft = Offset(0f, y))
      val nearT = measurer.measure(if (narrow) "1.5 km 안내" else "1.5 km · 데자로드 안내", if (ahead <= RoadSession.NEAR_M) strong else base)
      drawText(nearT, topLeft = Offset(size.width * nearF - nearT.size.width - 10.dp.toPx(), y))
      drawText(measurer.measure(if (narrow) "1 km 내비" else "1 km · 내비 안내", base), topLeft = Offset(size.width * naviF + 10.dp.toPx(), y))
    }
    SpotTile(next, 56.dp, strong = false, onDark = true)
  }
}

/** 내비가 스스로 안내하는 거리 (국내 내비의 흔한 1km 안내. 에뮬레이터 내비로는 아직 확인 못 함) */
private const val NAVI_M = 1_000.0
