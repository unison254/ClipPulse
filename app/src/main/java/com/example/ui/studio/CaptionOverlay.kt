package com.example.ui.studio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CaptionCue
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.ui.theme.BebasNeueFontFamily
import com.example.ui.theme.ElectricCoral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.NeonLime
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StudioObsidian

@Composable
fun BoxScope.LiveCaptionOverlay(
    activeCue: CaptionCue?,
    hookLine: String,
    currentOffsetSec: Double,
    style: CaptionStyle,
    position: CaptionPosition,
    scale: Double,
    uppercase: Boolean
) {
    // Opening hook banner during first 3 seconds
    if (hookLine.isNotBlank() && currentOffsetSec <= 3.2) {
        Surface(
            color = ElectricCoral,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = if (uppercase) hookLine.uppercase() else hookLine,
                style = TextStyle(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    letterSpacing = 0.6.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }

    val alignment = when (position) {
        CaptionPosition.TOP -> Alignment.TopCenter
        CaptionPosition.CENTER -> Alignment.Center
        CaptionPosition.BOTTOM -> Alignment.BottomCenter
    }

    val topPadding = if (position == CaptionPosition.TOP) 64.dp else 0.dp
    val bottomPadding = if (position == CaptionPosition.BOTTOM) 42.dp else 0.dp

    Box(
        modifier = Modifier
            .align(alignment)
            .fillMaxWidth()
            .padding(
                start = 14.dp,
                end = 14.dp,
                top = topPadding,
                bottom = bottomPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = activeCue,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut()
            },
            label = "CaptionCueTransition"
        ) { cue ->
            if (cue != null) {
                StyledCaptionLine(
                    cue = cue,
                    style = style,
                    scale = scale,
                    uppercase = uppercase
                )
            }
        }
    }
}

@Composable
private fun StyledCaptionLine(
    cue: CaptionCue,
    style: CaptionStyle,
    scale: Double,
    uppercase: Boolean
) {
    val rawText = if (uppercase) cue.text.uppercase() else cue.text
    val targetHighlight = if (uppercase) cue.highlightWord.uppercase() else cue.highlightWord
    val fontSize = (20f * scale.toFloat()).coerceIn(14f, 34f).sp

    val highlightColor = when (style) {
        CaptionStyle.HORMOZI_POP -> NeonLime
        CaptionStyle.CLEAN_GLASS -> ElectricCyan
        CaptionStyle.NEON_PULSE -> ElectricCyan
        CaptionStyle.EDITORIAL -> ElectricCoral
    }

    val baseTextColor = when (style) {
        CaptionStyle.EDITORIAL -> Color(0xFF101118)
        else -> Color.White
    }

    val fontFamily = when (style) {
        CaptionStyle.HORMOZI_POP -> BebasNeueFontFamily
        CaptionStyle.CLEAN_GLASS -> SpaceGroteskFontFamily
        CaptionStyle.NEON_PULSE -> SpaceGroteskFontFamily
        CaptionStyle.EDITORIAL -> InterFontFamily
    }

    val annotated = buildAnnotatedString {
        if (targetHighlight.isNotBlank()) {
            val idx = rawText.indexOf(targetHighlight, ignoreCase = true)
            if (idx >= 0) {
                append(rawText.substring(0, idx))
                withStyle(
                    SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                ) {
                    append(rawText.substring(idx, idx + targetHighlight.length))
                }
                append(rawText.substring(idx + targetHighlight.length))
            } else {
                append(rawText)
            }
        } else {
            append(rawText)
        }
    }

    val textStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = if (style == CaptionStyle.HORMOZI_POP) FontWeight.Normal else FontWeight.Bold,
        fontSize = if (style == CaptionStyle.HORMOZI_POP) (fontSize.value * 1.18f).sp else fontSize,
        lineHeight = (fontSize.value * 1.25f).sp,
        letterSpacing = if (style == CaptionStyle.HORMOZI_POP) 0.8.sp else 0.2.sp,
        color = baseTextColor,
        shadow = if (style == CaptionStyle.HORMOZI_POP || style == CaptionStyle.NEON_PULSE) {
            Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(2f, 3f),
                blurRadius = 6f
            )
        } else null
    )

    when (style) {
        CaptionStyle.HORMOZI_POP -> {
            Box(
                modifier = Modifier
                    .background(
                        color = StudioObsidian.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = annotated,
                    style = textStyle,
                    textAlign = TextAlign.Center
                )
            }
        }
        CaptionStyle.CLEAN_GLASS -> {
            Surface(
                color = Color(0xCC12141E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = annotated,
                    style = textStyle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        CaptionStyle.NEON_PULSE -> {
            Surface(
                color = Color(0xD90A0D18),
                border = BorderStroke(1.5.dp, ElectricCyan.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = annotated,
                    style = textStyle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        CaptionStyle.EDITORIAL -> {
            Surface(
                color = Color(0xF2F7F5EE),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = annotated,
                    style = textStyle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}
