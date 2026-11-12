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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dejaroad.app.RoadSession
import com.dejaroad.app.Script

/** 경로가 없을 때: 할 일 한 줄과, 남겨 둔 급제동 기록 */
@Composable
fun IdlePanel(s: RoadSession) {
  // 같은 곳은 가장 최근 기록만
  val remembered = s.memory.hards.groupBy { it.key }.map { (_, v) -> v.maxBy { it.atMillis } }.sortedByDescending { it.atMillis }.take(5)
  BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background)) {
    val narrow = maxWidth < NARROW
    val side = if (narrow) 40.dp else 56.dp
    Column(Modifier.fillMaxSize()) {
      Column(Modifier.padding(start = side, end = side, top = if (narrow) 32.dp else 40.dp, bottom = 28.dp)) {
        Text("데자로드", style = Type.labelM, fontWeight = FontWeight.SemiBold, color = Palette.textSecondary)
        Text("내비에서 목적지를 정하세요", style = Type.headlineS.copy(fontSize = if (narrow) 40.sp else 48.sp, lineHeight = if (narrow) 50.sp else 58.sp, letterSpacing = (-1).sp), modifier = Modifier.padding(top = 10.dp))
        Text("경로의 합류·분기·출구를 출발 전에 미리 보여 드려요", style = Type.bodyM, color = Palette.textSecondary, modifier = Modifier.padding(top = 12.dp))
      }
      Divider()
      Row(Modifier.padding(start = side, end = side, top = 28.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("급제동 기록", style = Type.labelM, color = Palette.textSecondary)
        Text("${s.memory.hardKeys.size}", style = Type.labelM, fontWeight = FontWeight.Bold)
      }
      if (remembered.isEmpty()) {
        Text(
          "브레이크를 세게 밟은 지점은 남겨 두었다가, 다음에 그 길로 갈 때 출발 전에 먼저 알려요",
          style = Type.bodyM,
          color = Palette.textSecondary,
          modifier = Modifier.padding(horizontal = side, vertical = 16.dp),
        )
      }
      remembered.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(horizontal = side, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(20.dp).background(Palette.textPrimary))
          Text(p.name, style = Type.titleS, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 20.dp))
          Text(Script.pastLine(p), style = Type.bodyS, color = Palette.textSecondary)
        }
        Divider(Modifier.padding(horizontal = side))
      }
    }
  }
}
