package com.creepyside.liquidclick.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.window.Dialog
import com.creepyside.liquidclick.core.Engine
import com.creepyside.liquidclick.core.Windows
import com.creepyside.liquidclick.data.Repo

@Composable
fun WindowsScreen() {
    val context = LocalContext.current
    Repo.version
    var picking by remember { mutableStateOf(0) }       // 0 — не выбираем, 1 — левое, 2 — правое
    var showApps by remember { mutableStateOf(false) }
    var freeform by remember { mutableStateOf(Windows.freeformEnabled(context)) }
    var status by remember { mutableStateOf("") }

    val apps = remember { Windows.apps(context) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Два окна", "Два приложения рядом + сценарий на каждое") }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (freeform) "Свободные окна включены" else "Свободные окна выключены",
                            color = if (freeform) Mint else Danger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (freeform)
                                "Приложения откроются окнами рядом"
                            else
                                "Без freeform система покажет только одно приложение",
                            color = TextLo,
                            fontSize = 11.sp
                        )
                    }
                    GlassChip("Проверить", false) {
                        freeform = Windows.freeformEnabled(context)
                    }
                }
                if (!freeform) {
                    Spacer(Modifier.height(10.dp))
                    Text("Включение через ADB (один раз, с ПК):", color = TextLo, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "adb shell settings put global enable_freeform_support 1",
                        color = Cyan,
                        fontSize = 11.sp
                    )
                    Text(
                        "adb shell settings put global force_resizable_activities 1",
                        color = Cyan,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip("Скопировать команды", false, Modifier.weight(1f)) {
                            copyToClipboard(
                                context,
                                "adb shell settings put global enable_freeform_support 1\n" +
                                    "adb shell settings put global force_resizable_activities 1"
                            )
                        }
                        GlassChip("Инструкция", false, Modifier.weight(1f)) {
                            copyToClipboard(
                                context,
                                "1. Включите отладку по USB и подключите телефон к ПК\n" +
                                    "2. Выполните две команды adb из приложения\n" +
                                    "3. Перезагрузите телефон\n" +
                                    "4. Вернитесь сюда и нажмите «Проверить»"
                            )
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("Приложения")
                Spacer(Modifier.height(8.dp))
                WindowAppRow(
                    title = "Левое окно",
                    pkg = Repo.leftPkg,
                    label = Windows.label(context, Repo.leftPkg)
                ) { picking = 1; showApps = true }
                Spacer(Modifier.height(8.dp))
                WindowAppRow(
                    title = "Правое окно",
                    pkg = Repo.rightPkg,
                    label = Windows.label(context, Repo.rightPkg)
                ) { picking = 2; showApps = true }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Открыть оба", Modifier.weight(1f), accent = true) {
                        if (!requireA11y(context)) return@GlassButton
                        status = Windows.openPair(context, Repo.leftPkg, Repo.rightPkg)
                    }
                    GlassButton("Свернуть", Modifier.weight(1f)) {
                        com.creepyside.liquidclick.core.A11y.instance?.pressHome()
                        status = "Свёрнуто"
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Открыть заново", Modifier.weight(1f)) {
                        status = Windows.reopenPair(context, Repo.leftPkg, Repo.rightPkg)
                    }
                    GlassButton("Поменять местами", Modifier.weight(1f)) {
                        val a = Repo.leftPkg
                        Repo.leftPkg = Repo.rightPkg
                        Repo.rightPkg = a
                        val s = Repo.leftScenarioId
                        Repo.leftScenarioId = Repo.rightScenarioId
                        Repo.rightScenarioId = s
                        Repo.touch()
                    }
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(status, color = TextLo, fontSize = 11.sp)
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("Сценарии окон")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Координаты шагов считаются от левого верхнего угла окна — один и тот же сценарий работает в любом окне.",
                    color = TextLo,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                ScenarioLine("Слева", Repo.leftScenarioId) { Repo.leftScenarioId = it; Repo.touch() }
                Spacer(Modifier.height(8.dp))
                ScenarioLine("Справа", Repo.rightScenarioId) { Repo.rightScenarioId = it; Repo.touch() }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Слева", Modifier.weight(1f)) {
                        if (!requireA11y(context)) return@GlassButton
                        val scenario = Repo.findScenario(Repo.leftScenarioId) ?: return@GlassButton
                        Engine.runScenario(context, scenario)
                    }
                    GlassButton("Справа", Modifier.weight(1f)) {
                        if (!requireA11y(context)) return@GlassButton
                        val scenario = Repo.findScenario(Repo.rightScenarioId) ?: return@GlassButton
                        Engine.runScenario(context, scenario)
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("Цикл двух окон")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Открывает левое приложение, выполняет сценарий, открывает правое, выполняет его сценарий, сворачивает оба и повторяет.",
                    color = TextLo,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Сворачивать каждый круг", color = TextLo, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    GlassChip(if (Repo.pairCollapse) "да" else "нет", Repo.pairCollapse) {
                        Repo.pairCollapse = !Repo.pairCollapse; Repo.touch()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Повторять бесконечно", color = TextLo, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    GlassChip(if (Repo.pairForever) "да" else "нет", Repo.pairForever) {
                        Repo.pairForever = !Repo.pairForever; Repo.touch()
                    }
                }
                SliderRow(
                    title = "Кругов",
                    value = Repo.pairLoops.toFloat(),
                    min = 1f,
                    max = 100f,
                    valueText = Repo.pairLoops.toString()
                ) { Repo.pairLoops = it.toInt(); Repo.touch() }
                GlassButton(
                    if (Engine.running) "■ Остановить" else "▶ Запустить цикл",
                    Modifier.fillMaxWidth(),
                    accent = !Engine.running
                ) {
                    if (Engine.running) {
                        Engine.stopScenario()
                    } else {
                        if (!requireA11y(context)) return@GlassButton
                        Engine.runPair(
                            ctx = context,
                            leftPkg = Repo.leftPkg,
                            rightPkg = Repo.rightPkg,
                            leftScenario = Repo.findScenario(Repo.leftScenarioId),
                            rightScenario = Repo.findScenario(Repo.rightScenarioId),
                            loops = Repo.pairLoops,
                            forever = Repo.pairForever,
                            collapseEachLoop = Repo.pairCollapse
                        )
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("Честно о клонировании")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Это два окна на одном устройстве, а не второй экземпляр приложения: данные, аккаунт и device ID общие. " +
                        "Настоящий клон (отдельные данные) требует work-профиля с device owner или root — без них Android этого не разрешает. " +
                        "Вторую копию откроют не все приложения: те, что живут в одном окне (мессенджеры, банки), просто перейдут на передний план.",
                    color = TextLo,
                    fontSize = 11.sp
                )
            }
        }
    }

    if (showApps) {
        AppPickerDialog(
            apps = apps,
            onPick = { app ->
                if (picking == 1) Repo.leftPkg = app.packageName else Repo.rightPkg = app.packageName
                Repo.touch()
                showApps = false
            },
            onClose = { showApps = false }
        )
    }
}

@Composable
private fun WindowAppRow(title: String, pkg: String, label: String, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextLo, fontSize = 11.sp)
            Text(
                if (pkg.isBlank()) "не выбрано" else label,
                color = if (pkg.isBlank()) TextLo else TextHi,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        GlassChip("Выбрать", false) { onPick() }
    }
}

@Composable
private fun ScenarioLine(title: String, scenarioId: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = Repo.findScenario(scenarioId)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextLo, fontSize = 11.sp)
            Text(
                current?.name ?: "не выбран",
                color = if (current == null) TextLo else TextHi,
                fontSize = 14.sp
            )
        }
        GlassChip("Выбрать", false) { open = true }
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            GlassCard(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                SectionTitle("Сценарий · " + title)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Repo.scenarios) { scenario ->
                        GlassChip(
                            scenario.name + " · " + scenario.steps.size + " шаг(ов)",
                            scenario.id == scenarioId,
                            Modifier.fillMaxWidth()
                        ) {
                            onPick(scenario.id)
                            open = false
                        }
                    }
                    item {
                        GlassChip("Без сценария", scenarioId.isBlank(), Modifier.fillMaxWidth()) {
                            onPick("")
                            open = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<Windows.App>,
    onPick: (Windows.App) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
        }
    }
    Dialog(onDismissRequest = onClose) {
        GlassCard(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            SectionTitle("Приложение")
            Spacer(Modifier.height(8.dp))
            GlassField(query, { query = it }, "Поиск", placeholder = "название или пакет")
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered) { app ->
                    GlassChip(app.label, false, Modifier.fillMaxWidth()) { onPick(app) }
                }
            }
        }
    }
}
