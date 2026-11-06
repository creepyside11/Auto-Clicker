package com.creepyside.liquidclick.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Deep = Color(0xFF05070D)
val DeepMid = Color(0xFF0A1224)
val Cyan = Color(0xFF7DF9FF)
val Aqua = Color(0xFF22D3EE)
val Violet = Color(0xFFA78BFA)
val Mint = Color(0xFF5EEAD4)
val TextHi = Color(0xFFEAF7FF)
val TextLo = Color(0xFF9FB6C9)
val Danger = Color(0xFFFF8FA3)

private val scheme = darkColorScheme(
    primary = Aqua,
    onPrimary = Color(0xFF04121A),
    secondary = Violet,
    background = Deep,
    onBackground = TextHi,
    surface = Color(0xFF0B1424),
    onSurface = TextHi
)

@Composable
fun LiquidClickTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

fun DrawScope.blob(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

@Composable
fun GlassBackground() {
    val transition = rememberInfiniteTransition(label = "bg")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF05070D), Color(0xFF0A1326), Color(0xFF04060B))
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            blob(
                Offset(w * 0.18f, h * (0.10f + 0.08f * phase)),
                w * 0.95f,
                Color(0xFF22D3EE).copy(alpha = 0.22f)
            )
            blob(
                Offset(w * 0.92f, h * (0.42f - 0.10f * phase)),
                w * 0.85f,
                Color(0xFFA78BFA).copy(alpha = 0.20f)
            )
            blob(
                Offset(w * 0.45f, h * (1.02f - 0.05f * phase)),
                w * 1.1f,
                Color(0xFF0EA5E9).copy(alpha = 0.14f)
            )
            blob(
                Offset(w * 0.75f, h * 0.02f),
                w * 0.5f,
                Color(0xFF5EEAD4).copy(alpha = 0.10f)
            )
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.035f))
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))
                ),
                RoundedCornerShape(corner)
            )
            .padding(padding),
        content = content
    )
}

@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                if (accent) {
                    Brush.linearGradient(
                        listOf(Aqua.copy(alpha = 0.38f), Violet.copy(alpha = 0.32f))
                    )
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))
                    )
                }
            )
            .border(
                1.dp,
                if (accent) Cyan.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.18f),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) TextHi else TextLo,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                if (selected) {
                    Brush.linearGradient(
                        listOf(Aqua.copy(alpha = 0.42f), Violet.copy(alpha = 0.30f))
                    )
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))
                    )
                }
            )
            .border(
                1.dp,
                if (selected) Cyan.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected) TextHi else TextLo,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = TextLo,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun ValueRow(label: String, value: String, valueColor: Color = TextHi) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextLo, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    valueText: String,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = TextLo, fontSize = 13.sp)
            Text(valueText, color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = Cyan,
                activeTrackColor = Aqua.copy(alpha = 0.85f),
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, color = TextLo, fontSize = 12.sp) },
        placeholder = {
            if (placeholder.isNotEmpty()) {
                Text(placeholder, color = TextLo.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        },
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextHi,
            unfocusedTextColor = TextHi,
            focusedBorderColor = Cyan.copy(alpha = 0.7f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
            focusedContainerColor = Color.White.copy(alpha = 0.05f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
            cursorColor = Cyan
        )
    )
}

@Composable
fun StatusPill(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) Mint else Danger)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (ok) Mint else Danger, fontSize = 12.sp)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String = "", trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = TextLo, fontSize = 13.sp)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.07f))
    )
}

fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("liquidclick", text))
}

fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

fun formatMs(value: Long): String = when {
    value >= 60000L -> (value / 60000L).toString() + " мин"
    value >= 1000L -> String.format("%.1f", value / 1000f) + " с"
    else -> value.toString() + " мс"
}

@Composable
fun EmptyHint(text: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(text, color = TextLo, fontSize = 13.sp)
    }
}

fun requireA11y(context: Context): Boolean {
    if (com.creepyside.liquidclick.core.A11y.ready()) return true
    android.widget.Toast.makeText(
        context,
        "Включите службу доступности LiquidClick",
        android.widget.Toast.LENGTH_SHORT
    ).show()
    return false
}
