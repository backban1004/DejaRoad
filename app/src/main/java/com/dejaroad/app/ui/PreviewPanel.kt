package com.dejaroad.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dejaroad.app.R
import com.dejaroad.app.RoadSession
import com.dejaroad.app.Script

/**
 * 출발 전 예습 (시안 A 목록형): 오늘 경로의 어려운 곳을 한 장에, 지난번 급제동한 곳은 한 줄 더.
 * Pleos 기본 배치인 절반 창(세로)에서도 잘리지 않게, 목록이 칸보다 길면 칸 안에서 밀어 본다(정차 중에만 보이는 화면).
 */
@Composable
fun PreviewPanel(s: RoadSession) {
  val d = s.destination
  val spots = s.upcoming
  val now = System.currentTimeMillis()
  val status = d?.let { dd -> s.minutesToDestination()?.let { "${dd.name}까지 ${it}분, ${Script.km(s.remainingM() ?: 0.0)}" } ?: dd.name }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val narrow = maxWidth < NARROW
    val side = if (narrow) 32.dp else 40.dp
    Column(Modifier.fillMaxSize().padding(start = side, end = side, top = 32.dp, bottom = 36.dp)) {
      AppHeader(status)
      Text(
        when {
          spots.isEmpty() -> "오늘 길에는 특별히 어려운 곳이 없어요"
          s.previewDone -> "준비됐어요"
          else -> "오늘 길에 어려운 곳은 ${spots.size}곳이에요"
        },
        style = Type.titleL,
        modifier = Modifier.padding(top = 22.dp),
      )
      if (s.previewDone && spots.isNotEmpty()) {
        Ready(s, Modifier.fillMaxWidth().weight(1f).padding(top = 26.dp))
      } else {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 26.dp).clip(CardShape).background(Palette.surface)) {
          val cardH = maxHeight
          val inner = if (narrow) 28.dp else 36.dp
          Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = cardH).padding(horizontal = inner),
            verticalArrangement = Arrangement.SpaceEvenly,
          ) {
            if (spots.isEmpty()) {
              Box(Modifier.fillMaxWidth().heightIn(min = cardH), contentAlignment = Alignment.Center) {
                Text(if (d == null) "내비 경로를 기다리는 중이에요" else "고속도로 합류, 분기점, 회전교차로가 없는 길이에요", style = Type.bodyL, color = Palette.textSecondary)
              }
            }
            spots.forEachIndexed { i, spot ->
              if (i > 0) Divider()
              val past = s.pastOf(spot)
              Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(if (narrow) 112.dp else 150.dp)) {
                  Text("${s.minutesTo(spot)}분 뒤", style = Type.titleS, fontWeight = FontWeight.Bold)
                  Text(Script.km((spot.atM - s.traveledM).coerceAtLeast(0.0)), style = Type.labelM, color = Palette.textSecondary, modifier = Modifier.padding(top = 4.dp))
                }
                Column(Modifier.weight(1f).padding(start = if (narrow) 20.dp else 36.dp, end = if (narrow) 20.dp else 36.dp)) {
                  Text(spot.name, style = Type.titleS)
                  Text(Script.advice(spot), style = Type.bodyS, color = Palette.textTertiary, modifier = Modifier.padding(top = 6.dp))
                  if (past != null) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                      Symbol(R.drawable.ic_history, 28.dp)
                      Text(Script.pastLine(past, now), style = Type.labelM, fontWeight = FontWeight.SemiBold)
                    }
                  }
                }
                SpotTile(spot, if (narrow) 88.dp else 112.dp, strong = past != null)
              }
            }
          }
        }
      }
      Text(
        if (s.previewDone) "목록은 접어 두었어요. 출발하면 화면 대신 소리로 알려 드려요" else "주행 중에는 각 지점 1.5km 전에 다시 말해 드려요",
        style = Type.labelM,
        color = Palette.textSecondary,
        modifier = Modifier.padding(top = 18.dp, start = 4.dp),
      )
      Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (s.voice.talking) {
          WideButton("읽기 멈추기", dark = true, modifier = Modifier.weight(1.3f), icon = R.drawable.ic_close) { s.stopPreview() }
        } else {
          WideButton("1분 듣고 출발하기", dark = true, modifier = Modifier.weight(1.3f), icon = R.drawable.ic_volume_up) { s.playPreview() }
        }
        if (s.previewDone) {
          WideButton("목록 다시 보기", dark = false, modifier = Modifier.weight(1f)) { s.reopenPreview() }
        } else {
          WideButton("그냥 출발하기", dark = false, modifier = Modifier.weight(1f)) { s.skipPreview() }
        }
      }
    }
  }
}

/** '그냥 출발하기'를 누른 뒤: 목록을 접고, 첫 어려운 곳과 언제 알려 주는지만 남긴다 */
@Composable
private fun Ready(s: RoadSession, modifier: Modifier) {
  val first = s.nextSpot ?: return
  val pastCount = s.upcoming.count { s.pastOf(it) != null }
  Column(modifier.clip(CardShape).background(Palette.dark).padding(horizontal = 48.dp, vertical = 40.dp), verticalArrangement = Arrangement.Center) {
    Text("첫 번째", style = Type.titleS, color = Palette.onDarkSecondary)
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
      SpotTile(first, 112.dp, strong = false, onDark = true)
      Column(Modifier.weight(1f)) {
        Text(first.name, style = Type.headlineS, color = Palette.onDark)
        Text("${s.minutesTo(first)}분 뒤, ${Script.km((first.atM - s.traveledM).coerceAtLeast(0.0))}", style = Type.bodyL, color = Palette.onDarkSecondary, modifier = Modifier.padding(top = 6.dp))
      }
    }
    Divider(Modifier.padding(vertical = 36.dp), color = Palette.onDarkLine)
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Symbol(R.drawable.ic_volume_up, 32.dp, Palette.onDark)
      Text("어려운 곳 ${s.upcoming.size}곳 모두 1.5km 전에 소리로 알려 드려요", style = Type.bodyL, color = Palette.onDark, modifier = Modifier.weight(1f))
    }
    if (pastCount > 0) {
      Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Symbol(R.drawable.ic_history, 32.dp, Palette.onDark)
        Text("지난번 급히 줄였던 ${pastCount}곳은 3km 전에 한 번 더", style = Type.bodyL, color = Palette.onDark, modifier = Modifier.weight(1f))
      }
    }
  }
}
