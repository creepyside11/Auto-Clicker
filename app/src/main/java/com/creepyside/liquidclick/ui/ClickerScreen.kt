package com.creepyside.liquidclick.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creepyside.liquidclick.core.A11y
import com.creepyside.liquidclick.core.CaptureService
import com.creepyside.liquidclick.core.Engine
import com.creepyside.liquidclick.core.Picker
import com.creepyside.liquidclick.data.Repo
import com.creepyside.liquidclick.model.StepType

private fun ensureService(context: Context): Boolean {
    if (A11y.ready()) return true
    Toast.makeText(context, "Включите службу доступности LiquidClick", Toast.LENGTH_SHORT).show()
    return false
}

@Composable
fun ClickerScreen() {
    val context = LocalContext.current
    val cfg = Repo.click
    val running = Engine.running && Engine.runningName == "Автокликер"

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader("Кликер", "Тап · свайп · зажатие")
        }

        if (!Repo.a11yReady) {
            item {
                GlassCard {
                    Text(
                        "Шаг 1 · Служба доступности",
                        color = TextHi,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Без неё приложение не может нажимать и вставлять текст. Откройте настройки и включите «LiquidClick».",
                        color = TextLo,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassButton("Включить службу", Modifier.fillMaxWidth(), accent = true) {
                        openAccessibilitySettings(context)
                    }
                }
            }
        }

        item {
            GlassCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill("Доступность", Repo.a11yReady)
                    StatusPill("Захват экрана", CaptureService.isReady())
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("Режим")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Тап", cfg.mode == StepType.TAP) {
                        Repo.click = Repo.click.copy(mode = StepType.TAP); Repo.save()
                    }
                    GlassChip("Зажатие", cfg.mode == StepType.LONG) {
                        Repo.click = Repo.click.copy(mode = StepType.LONG); Repo.save()
                    }
                    GlassChip("Свайп", cfg.mode == StepType.SWIPE) {
                        Repo.click = Repo.click.copy(mode = StepType.SWIPE); Repo.save()
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("Координаты")
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x22000000))
                ) {
                    val totalW = Repo.screen.w.toFloat().coerceAtLeast(1f)
                    val totalH = Repo.screen.h.toFloat().coerceAtLeast(1f)
                    Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                        val sx = size.width / totalW
                        val sy = size.height / totalH
                        val start = Offset(cfg.x * sx, cfg.y * sy)
                        val end = Offset(cfg.x2 * sx, cfg.y2 * sy)
                        if (cfg.mode == StepType.SWIPE) {
                            drawLine(
                                color = Violet.copy(alpha = 0.9f),
                                start = start,
                                end = end,
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        }
                        drawCircle(color = Cyan.copy(alpha = 0.22f), radius = 22f, center = start)
                        drawCircle(color = Cyan, radius = 7f, center = start)
                        if (cfg.mode == StepType.SWIPE) {
                            drawCircle(color = Violet, radius = 7f, center = end)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ValueRow("Точка", "${cfg.x}, ${cfg.y}", Cyan)
                if (cfg.mode == StepType.SWIPE) {
                    ValueRow("Конец свайпа", "${cfg.x2}, ${cfg.y2}", Violet)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Выбрать точку", Modifier.weight(1f)) {
                        if (!ensureService(context)) return@GlassButton
                        Picker.pickPoint { x, y ->
                            Repo.click = Repo.click.copy(x = x, y = y)
                            Repo.save()
                        }
                    }
                    if (cfg.mode == StepType.SWIPE) {
                        GlassButton("Конец", Modifier.weight(1f)) {
                            if (!ensureService(context)) return@GlassButton
                            Picker.pickPoint { x, y ->
                                Repo.click = Repo.click.copy(x2 = x, y2 = y)
                                Repo.save()
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("Параметры")
                Spacer(Modifier.height(6.dp))
                SliderRow(
                    title = "Интервал",
                    value = cfg.intervalMs.toFloat(),
                    min = 10f,
                    max = 3000f,
                    valueText = formatMs(cfg.intervalMs)
                ) {
                    Repo.click = Repo.click.copy(intervalMs = it.toLong())
                    Repo.save()
                }
                if (cfg.mode != StepType.TAP) {
                    SliderRow(
                        title = "Длительность",
                        value = cfg.durationMs.toFloat(),
                        min = 100f,
                        max = if (cfg.mode == StepType.SWIPE) 2000f else 8000f,
                        valueText = formatMs(cfg.durationMs)
                    ) {
                        Repo.click = Repo.click.copy(durationMs = it.toLong())
                        Repo.save()
                    }
                }
                SliderRow(
                    title = "Количество (0 — бесконечно)",
                    value = cfg.count.toFloat(),
                    min = 0f,
                    max = 2000f,
                    valueText = if (cfg.count == 0) "∞" else cfg.count.toString()
                ) {
                    Repo.click = Repo.click.copy(count = it.toInt())
                    Repo.save()
                }
                SliderRow(
                    title = "Разброс точки",
                    value = cfg.jitter.toFloat(),
                    min = 0f,
                    max = 60f,
                    valueText = if (cfg.jitter == 0) "выкл" else "±${cfg.jitter} px"
                ) {
                    Repo.click = Repo.click.copy(jitter = it.toInt())
                    Repo.save()
                }
            }
        }

        item {
            GlassButton(
                text = if (running) "СТОП" else "СТАРТ",
                modifier = Modifier.fillMaxWidth(),
                accent = !running
            ) {
                if (running) {
                    Engine.stopScenario()
                } else {
                    if (!ensureService(context)) return@GlassButton
                    Engine.startClicker(context)
                }
            }
        }

        item {
            GlassCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Журнал")
                    if (Engine.running) {
                        Text(Engine.runningName, color = Mint, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (Engine.logs.isEmpty()) {
                    Text("Пока пусто", color = TextLo, fontSize = 12.sp)
                } else {
                    Engine.logs.take(7).forEach { line ->
                        Text(
                            line,
                            color = if (line.contains("✖")) Danger else TextLo,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Плавающая панель", color = TextHi, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    GlassChip(if (Repo.panelEnabled) "включена" else "выключена", Repo.panelEnabled) {
                        Repo.panelEnabled = !Repo.panelEnabled
                        Repo.save()
                        if (Repo.panelEnabled) {
                            val service = A11y.instance
                            if (service != null) com.creepyside.liquidclick.core.Panel.show(service)
                            else Toast.makeText(context, "Сначала включите службу доступности", Toast.LENGTH_SHORT).show()
                        } else {
                            com.creepyside.liquidclick.core.Panel.hide()
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Панель с кнопками запуска, выбора точки и области поверх любых приложений.",
                    color = TextLo,
                    fontSize = 12.sp
                )
            }
        }
    }
}
