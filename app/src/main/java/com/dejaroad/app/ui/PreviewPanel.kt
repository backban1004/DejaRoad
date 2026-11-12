package com.dejaroad.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/** 출발 전 예습 (시안 A 목록형): 오늘 경로의 어려운 곳을 한 장에, 지난번 급제동한 곳은 한 줄 더 */
@Composable
fun PreviewPanel(s: RoadSession) {
  val d = s.destination
  val spots = s.spots
  val now = System.currentTimeMillis()
  val status = d?.let { dd -> s.minutesToDestination()?.let { "${dd.name}까지 ${it}분, ${Script.km(dd.remainM.toDouble())}" } ?: dd.name }
  Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 36.dp)) {
    AppHeader(status)
    Text(
      if (spots.isEmpty()) "오늘 길에는 특별히 어려운 곳이 없어요" else "오늘 길에 어려운 곳은 ${spots.size}곳이에요",
      style = Type.titleL,
      modifier = Modifier.padding(top = 22.dp),
    )
    Column(Modifier.fillMaxWidth().weight(1f).padding(top = 26.dp).clip(CardShape).background(Palette.surface).padding(horizontal = 36.dp)) {
      if (spots.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(if (d == null) "내비 경로를 기다리는 중이에요" else "고속도로 합류, 분기점, 회전교차로가 없는 길이에요", style = Type.bodyL, color = Palette.textSecondary)
        }
      }
      spots.forEachIndexed { i, spot ->
        if (i > 0) Divider()
        val past = s.pastOf(spot)
        Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.width(150.dp)) {
            Text("${s.minutesTo(spot)}분 뒤", style = Type.titleS, fontWeight = FontWeight.Bold)
            Text(Script.km((spot.atM - s.traveledM).coerceAtLeast(0.0)), style = Type.labelM, color = Palette.textSecondary, modifier = Modifier.padding(top = 4.dp))
          }
          Column(Modifier.weight(1f).padding(start = 36.dp, end = 36.dp)) {
            Text(spot.name, style = Type.titleS)
            Text(Script.advice(spot), style = Type.bodyS, color = Palette.textTertiary, modifier = Modifier.padding(top = 6.dp))
            if (past != null) {
              Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Symbol(R.drawable.ic_history, 28.dp)
                Text(Script.pastLine(past, now), style = Type.labelM, fontWeight = FontWeight.SemiBold)
              }
            }
          }
          SpotTile(spot, 112.dp, strong = past != null)
        }
      }
    }
    Text("주행 중에는 각 지점 1km 전에 다시 말해 드려요", style = Type.labelM, color = Palette.onDarkTertiary, modifier = Modifier.padding(top = 18.dp, start = 4.dp))
    Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
      if (s.voice.talking) {
        WideButton("읽기 멈추기", dark = true, modifier = Modifier.weight(1.3f), icon = R.drawable.ic_close) { s.stopPreview() }
      } else {
        WideButton("1분 듣고 출발하기", dark = true, modifier = Modifier.weight(1.3f), icon = R.drawable.ic_volume_up) { s.playPreview() }
      }
      WideButton("그냥 출발하기", dark = false, modifier = Modifier.weight(1f)) { s.skipPreview() }
    }
  }
}
