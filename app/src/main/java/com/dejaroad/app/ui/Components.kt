package com.dejaroad.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dejaroad.app.HardSpot
import com.dejaroad.app.R
import com.dejaroad.app.Side
import com.dejaroad.app.SpotKind

val CardShape = RoundedCornerShape(24.dp)
val ButtonShape = RoundedCornerShape(20.dp)

/** 이보다 좁으면 절반 창(Pleos 기본: 내비 옆 1:1, 세로 841dp)으로 보고 배치를 줄인다 */
val NARROW = 1100.dp

/** Pleos 버튼 인터랙션: 누르는 동안 95%로 줄고 배경이 한 단계 진해진다 */
@Composable
fun PressableSurface(onClick: () -> Unit, color: Color, pressedColor: Color, shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(90), label = "press")
  Box(
    modifier
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(shape)
      .background(if (pressed) pressedColor else color)
      .clickable(interactionSource = source, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { content() }
}

/** Material Symbols Rounded 아이콘 (res/drawable/ic_*.xml) */
@Composable
fun Symbol(@DrawableRes id: Int, size: Dp, tint: Color = Palette.textPrimary, modifier: Modifier = Modifier) {
  Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** 화면 위쪽 머리글: 앱 이름 + 오른쪽 상태 글 */
@Composable
fun AppHeader(status: String? = null) {
  Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("데자로드", style = Type.titleS, fontWeight = FontWeight.Bold)
    Box(Modifier.weight(1f))
    if (status != null) Text(status, style = Type.labelM, color = Palette.textSecondary)
  }
}

/** 넓은 버튼 (짙은 기본 동작 / 회색 보조 동작) */
@Composable
fun WideButton(text: String, dark: Boolean, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, onClick: () -> Unit) {
  PressableSurface(onClick, if (dark) Palette.dark else Palette.button, if (dark) Palette.darkPressed else Palette.buttonPressed, ButtonShape, modifier.height(96.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      if (icon != null) Symbol(icon, 40.dp, if (dark) Palette.onDark else Palette.textPrimary)
      Text(text, style = Type.titleS, color = if (dark) Palette.onDark else Palette.textPrimary)
    }
  }
}

@Composable
fun Divider(modifier: Modifier = Modifier, color: Color = Palette.divider) {
  Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** 어려운 곳 종류를 나타내는 길 모양 아이콘 */
@DrawableRes
fun spotIcon(spot: HardSpot): Int {
  val right = spot.side != Side.LEFT
  return when (spot.kind) {
    SpotKind.ENTER_HIGHWAY, SpotKind.MERGE -> R.drawable.ic_merge
    SpotKind.JUNCTION -> if (right) R.drawable.ic_fork_right else R.drawable.ic_fork_left
    SpotKind.EXIT_HIGHWAY -> if (right) R.drawable.ic_ramp_right else R.drawable.ic_ramp_left
    SpotKind.ROUNDABOUT -> if (right) R.drawable.ic_roundabout_right else R.drawable.ic_roundabout_left
    SpotKind.UTURN -> R.drawable.ic_u_turn_left
    SpotKind.BESIDE_UNDERPASS, SpotKind.BESIDE_OVERPASS -> R.drawable.ic_alt_route
    SpotKind.INTO_UNDERPASS, SpotKind.INTO_OVERPASS -> R.drawable.ic_straight
    SpotKind.SHARP, SpotKind.QUICK_TURNS -> if (right) R.drawable.ic_turn_right else R.drawable.ic_turn_left
  }
}

/**
 * 길 모양 칸. 내비가 차로 수를 주지 않으므로 차로 그림 대신 어느 쪽으로 가는지만 크게 보여 준다.
 * [strong]이면(지난번 급제동한 곳) 짙은 칸에 흰 아이콘.
 */
@Composable
fun SpotTile(spot: HardSpot, size: Dp, strong: Boolean, onDark: Boolean = false) {
  val bg = when {
    onDark -> Palette.onDark
    strong -> Palette.dark
    else -> Palette.segmentOff
  }
  val fg = if (onDark || !strong) Palette.textPrimary else Palette.onDark
  Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.2f)).background(bg), contentAlignment = Alignment.Center) {
    Symbol(spotIcon(spot), size * 0.56f, fg)
  }
}

@Composable
fun Spacer(width: Dp = 0.dp, height: Dp = 0.dp) {
  Box(Modifier.width(width).height(height))
}
