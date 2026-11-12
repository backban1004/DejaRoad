package com.dejaroad.app.ui

import androidx.compose.foundation.background
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
    Text(
      buildAnnotatedString {
        append(Script.km(ahead))
        withStyle(SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Palette.onDarkSecondary)) { append(" 앞") }
      },
      style = Type.headlineL.copy(fontSize = 96.sp, lineHeight = 108.sp),
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
