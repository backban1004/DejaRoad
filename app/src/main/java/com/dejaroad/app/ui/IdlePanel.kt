package com.dejaroad.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dejaroad.app.R
import com.dejaroad.app.RoadSession
import com.dejaroad.app.Script

/** 경로가 없을 때: 쓰는 법과, 기억하고 있는 어려운 곳 */
@Composable
fun IdlePanel(s: RoadSession) {
  val now = System.currentTimeMillis()
  // 같은 곳은 가장 최근 기록만
  val remembered = s.memory.hards.groupBy { it.key }.map { (_, v) -> v.maxBy { it.atMillis } }.sortedByDescending { it.atMillis }.take(4)
  Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 36.dp)) {
    AppHeader()
    Text("내비에 목적지를 넣으면 어려운 곳을 골라 드려요", style = Type.titleL, modifier = Modifier.padding(top = 22.dp))
    Text("고속도로 합류, 분기점, 회전교차로처럼 처음 가면 당황하기 쉬운 곳을 출발 전에 1분 동안 미리 들어요.", style = Type.bodyL, color = Palette.textTertiary, modifier = Modifier.padding(top = 14.dp))
    Column(Modifier.fillMaxWidth().weight(1f).padding(top = 30.dp).clip(CardShape).background(Palette.surface).padding(horizontal = 36.dp, vertical = 28.dp)) {
      Text(if (remembered.isEmpty()) "아직 기억하는 곳이 없어요" else "기억하고 있는 어려운 곳 ${s.memory.hardKeys.size}곳", style = Type.titleS)
      if (remembered.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("주행 중 브레이크를 세게 밟은 곳은 기억했다가, 다음에 그 길을 갈 때 먼저 알려 드려요.", style = Type.bodyM, color = Palette.textSecondary)
        }
      }
      remembered.forEachIndexed { i, p ->
        if (i > 0) Divider() else Spacer(height = 18.dp)
        Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Symbol(R.drawable.ic_history, 32.dp)
          Text(p.name, style = Type.titleS, modifier = Modifier.weight(1f))
          Text("${Script.dayWord(p.atMillis, now)}, ${p.fromKph}→${p.toKph} km/h", style = Type.bodyS, color = Palette.textSecondary, fontWeight = FontWeight.Medium)
        }
      }
    }
  }
}
