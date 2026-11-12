package com.dejaroad.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.dejaroad.app.R

/**
 * Pleos Connect 기본 UI에 맞춘 무채색 팔레트.
 * 강조색 없이 상태는 밝기(회색 ↔ 짙은 회색)로 구분한다.
 */
object Palette {
  val background = Color(0xFFF6F7F8)
  val surface = Color(0xFFFFFFFF)
  val button = Color(0xFFE9EAEC)
  val buttonPressed = Color(0xFFDCDDE0)
  val dark = Color(0xFF1C1D1F)
  val darkPressed = Color(0xFF343538)
  val divider = Color(0xFFECEDEF)
  /** 비어 있는 칸·체크 테두리 */
  val indicator = Color(0xFFD4D5D8)
  val segmentOff = Color(0xFFE3E5E8)
  val danger = Color(0xFFD92D20)
  val textPrimary = Color(0xFF111214)
  val textSecondary = Color(0xFF6E7177)
  val textTertiary = Color(0xFF3C3E42)
  val onDark = Color(0xFFFFFFFF)
  val onDarkSecondary = Color(0xFFA7AAB0)
  val onDarkLine = Color(0x1AFFFFFF)
}

/**
 * Pleos 시스템 UI 글꼴 Asta Sans (42dot, SIL OFL 1.1). 가변 글꼴 하나를 굵기별로 나눠 쓴다.
 * 숫자는 값이 바뀌어도 글자 폭이 흔들리지 않게 고정폭 숫자(tnum)를 쓴다.
 */
@OptIn(ExperimentalTextApi::class)
private fun asta(weight: FontWeight) = Font(R.font.asta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val AstaSans = FontFamily(asta(FontWeight.Normal), asta(FontWeight.Medium), asta(FontWeight.SemiBold), asta(FontWeight.Bold), asta(FontWeight.ExtraBold))

private val base =
  TextStyle(
    fontFamily = AstaSans,
    color = Palette.textPrimary,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    // 한국어를 글자 단위가 아니라 어절 단위로 줄바꿈한다 ("아니\n에요" 방지, API 33+)
    lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase),
  )

private fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = (-0.2).sp) =
  base.copy(fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight, letterSpacing = letterSpacing)

/** Pleos Typography 규격 (160dpi 차량 화면, 1dp = 1px). 크기/줄 높이는 가이드 표를 그대로 따른다 */
object Type {
  val headlineL = style(60, 80, FontWeight.ExtraBold, (-1).sp)
  val headlineS = style(48, 64, FontWeight.Bold, (-0.8).sp)
  val titleL = style(40, 52, FontWeight.Bold, (-0.6).sp)
  val titleM = style(36, 44, FontWeight.Bold, (-0.4).sp)
  val titleS = style(32, 40, FontWeight.SemiBold)
  val bodyL = style(30, 38)
  val bodyM = style(28, 36)
  val bodyS = style(26, 34)
  val labelM = style(24, 28)
  val labelS = style(20, 24)
}

@Composable
fun DejaRoadTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = lightColorScheme(background = Palette.background, surface = Palette.surface, onSurface = Palette.textPrimary)) {
    ProvideTextStyle(Type.bodyM, content)
  }
}
