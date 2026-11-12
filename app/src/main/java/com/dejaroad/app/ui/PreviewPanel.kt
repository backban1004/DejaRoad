package com.dejaroad.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dejaroad.app.HardSpot
import com.dejaroad.app.R
import com.dejaroad.app.RoadSession
import com.dejaroad.app.Script

/** 바로 앞 곳과 이만큼 안에 붙어 있으면 시간 대신 '곧이어'로 쓴다 */
private const val FOLLOW_M = 500.0

/** 경로 한 줄에 올릴 표시: 지금 위치부터 도착까지를 0~1로 */
fun routeMarks(s: RoadSession, spots: List<HardSpot>): List<RouteMark> {
  if (spots.isEmpty()) return emptyList()
  val start = s.traveledM
  val end = (s.routeEndM() ?: (spots.last().atM * 1.1)).coerceAtLeast(spots.last().atM + 1)
  return spots.mapIndexed { i, spot ->
    RouteMark(i + 1, ((spot.atM - start) / (end - start)).toFloat(), s.pastOf(spot) != null, shortName(spot), s.minutesTo(spot))
  }
}

/**
 * 출발 전 예습 (시안 A 목록형 + 경로 한 줄): 오늘 길 전체에서 어려운 곳이 어디쯤인지 한 줄로 보이고,
 * 아래 목록에 같은 번호로 할 일을 적는다. 지난번 급제동한 곳은 짙은 칸과 한 줄 더.
 * 절반 창(세로)에서는 경로 한 줄의 글을 빼고 번호만 남기며, 목록이 칸보다 길면 칸 안에서 밀어 본다(정차 중에만 보이는 화면).
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
        Ready(s, narrow, Modifier.fillMaxWidth().weight(1f).padding(top = 26.dp))
      } else {
        if (spots.isNotEmpty()) RouteCard(s, spots, narrow)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp).clip(CardShape).background(Palette.surface)) {
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
              val gap = if (i > 0) spot.atM - spots[i - 1].atM else Double.MAX_VALUE
              Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberBadge(i + 1, past != null, if (narrow) 36.dp else 40.dp)
                Column(Modifier.padding(start = if (narrow) 16.dp else 24.dp).width(if (narrow) 104.dp else 132.dp)) {
                  if (gap < FOLLOW_M) {
                    Text("곧이어", style = Type.titleS, fontWeight = FontWeight.Bold)
                    Text("${Script.meters(gap)} 뒤", style = Type.labelM, color = Palette.textSecondary, modifier = Modifier.padding(top = 2.dp))
                  } else {
                    Text("${s.minutesTo(spot)}분 뒤", style = Type.titleS, fontWeight = FontWeight.Bold)
                    Text(Script.km((spot.atM - s.traveledM).coerceAtLeast(0.0)), style = Type.labelM, color = Palette.textSecondary, modifier = Modifier.padding(top = 2.dp))
                  }
                }
                Column(Modifier.weight(1f).padding(start = if (narrow) 16.dp else 32.dp, end = if (narrow) 16.dp else 32.dp)) {
                  Text(spot.name, style = Type.titleS)
                  Text(Script.advice(spot), style = Type.bodyS, color = Palette.textTertiary, modifier = Modifier.padding(top = 4.dp))
                  if (past != null) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                      Symbol(R.drawable.ic_history, 28.dp)
                      Text(Script.pastLine(past, now), style = Type.labelM, fontWeight = FontWeight.SemiBold)
                    }
                  }
                }
                SpotTile(spot, if (narrow) 80.dp else 96.dp, strong = past != null)
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
      Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
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

/** 경로 한 줄 카드: 위에 짙은 칸/빈 칸 뜻, 아래 출발~도착 한 줄 */
@Composable
private fun RouteCard(s: RoadSession, spots: List<HardSpot>, narrow: Boolean) {
  val anyPast = spots.any { s.pastOf(it) != null }
  Column(Modifier.fillMaxWidth().padding(top = 24.dp).clip(CardShape).background(Palette.surface).padding(top = 22.dp, bottom = 12.dp)) {
    if (anyPast) {
      Row(Modifier.padding(horizontal = if (narrow) 28.dp else 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Palette.dark))
        Text("지난번 급히 줄였던 곳", style = Type.labelS, color = Palette.textSecondary)
        Box(Modifier.padding(start = 14.dp).size(22.dp).clip(RoundedCornerShape(6.dp)).border(3.dp, Palette.dark, RoundedCornerShape(6.dp)))
        Text("처음 가는 어려운 곳", style = Type.labelS, color = Palette.textSecondary)
      }
    }
    RouteLine(routeMarks(s, spots), s.destination?.name, dark = false, showLabels = !narrow, modifier = Modifier.padding(top = 10.dp))
  }
}

/** 목록 줄 앞 번호 칸 (경로 한 줄의 번호와 같다). 지난번 급제동한 곳은 짙게 채운다 */
@Composable
fun NumberBadge(n: Int, filled: Boolean, size: Dp) {
  val shape = RoundedCornerShape(size * 0.3f)
  Box(
    Modifier.size(size).clip(shape).background(if (filled) Palette.dark else Palette.surface).border(3.dp, Palette.dark, shape),
    contentAlignment = Alignment.Center,
  ) {
    Text("$n", style = Type.labelS.copy(fontSize = 20.sp), fontWeight = FontWeight.Bold, color = if (filled) Palette.onDark else Palette.textPrimary)
  }
}

/** '그냥 출발하기'를 누른 뒤: 목록을 접고, 첫 어려운 곳과 오늘 길 한 줄, 언제 알려 주는지만 남긴다 */
@Composable
private fun Ready(s: RoadSession, narrow: Boolean, modifier: Modifier) {
  val first = s.nextSpot ?: return
  val spots = s.upcoming
  val past = s.pastOf(first)
  val pastCount = spots.count { s.pastOf(it) != null }
  val pad = if (narrow) 40.dp else 60.dp
  Column(modifier.clip(CardShape).background(Palette.dark).padding(vertical = 44.dp)) {
    Text("첫 번째 어려운 곳", style = Type.bodyL, color = Palette.onDarkSecondary, modifier = Modifier.padding(horizontal = pad))
    Row(Modifier.padding(start = pad, end = pad, top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
      SpotTile(first, if (narrow) 104.dp else 128.dp, strong = false, onDark = true)
      Column(Modifier.weight(1f)) {
        Text(first.name, style = Type.headlineL.copy(fontSize = if (narrow) 48.sp else 60.sp, lineHeight = if (narrow) 60.sp else 72.sp), color = Palette.onDark)
        val sub = Type.titleS.copy(fontWeight = FontWeight.Normal)
        Text("${s.minutesTo(first)}분 뒤, ${Script.km((first.atM - s.traveledM).coerceAtLeast(0.0))}", style = sub, color = Palette.onDarkSecondary, modifier = Modifier.padding(top = 6.dp))
        if (past != null) {
          Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Symbol(R.drawable.ic_history, 30.dp, Palette.onDarkSecondary)
            Text("${Script.dayWord(past.atMillis, System.currentTimeMillis())} ${past.fromKph}→${past.toKph} km/h", style = sub, color = Palette.onDarkSecondary)
          }
        }
      }
    }
    Box(Modifier.weight(1f))
    RouteLine(routeMarks(s, spots), s.destination?.name, dark = true, showLabels = !narrow)
    Box(Modifier.weight(1f))
    Divider(Modifier.padding(horizontal = pad), color = Palette.onDarkLine)
    Column(Modifier.padding(start = pad, end = pad, top = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Symbol(R.drawable.ic_volume_up, 34.dp, Palette.onDark)
        Text("${spots.size}곳 모두 1.5km 전에 소리로", style = Type.bodyM, color = Palette.onDark)
      }
      if (pastCount > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          Symbol(R.drawable.ic_history, 34.dp, Palette.onDark)
          Text("지난번 급히 줄였던 ${pastCount}곳은 3km 전에 한 번 더", style = Type.bodyM, color = Palette.onDark)
        }
      }
    }
  }
}
