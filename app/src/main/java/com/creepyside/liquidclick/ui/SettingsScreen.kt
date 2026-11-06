package com.creepyside.liquidclick.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creepyside.liquidclick.BuildConfig
import com.creepyside.liquidclick.core.A11y
import com.creepyside.liquidclick.core.CaptureService
import com.creepyside.liquidclick.core.Engine
import com.creepyside.liquidclick.core.Panel
import com.creepyside.liquidclick.data.Repo

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val activity = context as? MainActivity
    var confirmReset by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Настройки", "Доступы и состояние") }

        item {
            GlassCard {
                Text("Служба доступности", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                StatusPill(
                    if (Repo.a11yReady || A11y.ready()) "включена" else "выключена",
                    Repo.a11yReady || A11y.ready()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Без неё приложение не сможет нажимать, свайпать и вставлять текст.",
                    color = TextLo,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                GlassButton("Открыть настройки доступности", Modifier.fillMaxWidth(), accent = true) {
                    openAccessibilitySettings(context)
                }
            }
        }

        item {
            GlassCard {
                Text("Захват экрана", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                StatusPill(
                    if (CaptureService.isReady() || Repo.captureReady) "разрешён" else "не разрешён",
                    CaptureService.isReady() || Repo.captureReady
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Нужен, чтобы распознавать текст в выделенных областях. Изображение обрабатывается только на устройстве.",
                    color = TextLo,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Разрешить", Modifier.weight(1f), accent = true) {
                        activity?.requestCapture()
                    }
                    GlassButton("Остановить", Modifier.weight(1f)) {
                        CaptureService.stop(context)
                        Repo.captureReady = false
                    }
                }
            }
        }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Плавающая панель", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Все настройки доступны прямо из панели", color = TextLo, fontSize = 12.sp)
                    }
                    GlassChip(if (Repo.panelEnabled) "вкл" else "выкл", Repo.panelEnabled) {
                        Repo.panelEnabled = !Repo.panelEnabled
                        Repo.save()
                        val service = A11y.instance
                        if (Repo.panelEnabled && service != null) {
                            Panel.show(service)
                        } else {
                            Panel.hide()
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SectionTitle("Сторона экрана")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Слева", Repo.panelSide == 0, Modifier.weight(1f)) {
                        Repo.panelSide = 0; Repo.touch()
                    }
                    GlassChip("Справа", Repo.panelSide == 1, Modifier.weight(1f)) {
                        Repo.panelSide = 1; Repo.touch()
                    }
                }
                SliderRow(
                    title = "Прозрачность панели",
                    value = Repo.panelAlpha.toFloat(),
                    min = 30f,
                    max = 100f,
                    valueText = Repo.panelAlpha.toString() + "%"
                ) { Repo.panelAlpha = it.toInt(); Repo.touch() }
                Spacer(Modifier.height(4.dp))
                Text(
                    "В панели: ⚙ — настройки (сценарий, режим кликера, интервал, повторы, текст, наблюдение), " +
                        "✚ — новая кнопка-шаг на экране, ▶ — запуск.",
                    color = TextLo,
                    fontSize = 11.sp
                )
            }
        }

        item {
            GlassCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Быстрые тексты")
                    GlassChip("Очистить", false) {
                        Repo.quickTexts.clear()
                        Repo.touch()
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (Repo.quickTexts.isEmpty()) {
                    Text(
                        "Пусто. Добавьте тексты в разделе «Имена → Свой текст» или через панель (⚙ → Ввести текст).",
                        color = TextLo,
                        fontSize = 12.sp
                    )
                } else {
                    Repo.quickTexts.take(10).forEach { quick ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                quick.take(40),
                                color = TextHi,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            GlassChip("✕", false) {
                                Repo.quickTexts.remove(quick)
                                Repo.touch()
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                Text("Остановить всё", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Сценарии, кликер и наблюдение будут выключены.", color = TextLo, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                GlassButton("СТОП", Modifier.fillMaxWidth()) { Engine.stopAll() }
            }
        }

        item {
            GlassCard {
                Text("О приложении", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                ValueRow("Версия", BuildConfig.VERSION_NAME, Cyan)
                ValueRow("Сценариев", Repo.scenarios.size.toString())
                ValueRow("Наблюдений", Repo.watchers.size.toString())
                ValueRow("Данные", "только на устройстве", Mint)
                Spacer(Modifier.height(10.dp))
                if (confirmReset) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("Точно удалить", Modifier.weight(1f), accent = true) {
                            Repo.scenarios.clear()
                            Repo.watchers.clear()
                            Repo.save()
                            confirmReset = false
                        }
                        GlassButton("Отмена", Modifier.weight(1f)) { confirmReset = false }
                    }
                } else {
                    GlassButton("Удалить все данные", Modifier.fillMaxWidth()) { confirmReset = true }
                }
            }
        }

        item {
            Text(
                "LiquidClick · liquid glass · " + BuildConfig.VERSION_NAME,
                color = TextLo,
                fontSize = 11.sp
            )
        }
    }
}
