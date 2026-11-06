package com.creepyside.liquidclick.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.creepyside.liquidclick.core.Engine
import com.creepyside.liquidclick.core.Picker
import com.creepyside.liquidclick.data.Repo
import com.creepyside.liquidclick.model.MatchMode
import com.creepyside.liquidclick.model.Watcher
import kotlinx.coroutines.launch

@Composable
fun WatchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "Наблюдение",
                subtitle = "Реакция на текст в области",
                trailing = {
                    GlassButton("＋ Добавить", accent = true) {
                        val watcher = Watcher(name = "Наблюдение " + (Repo.watchers.size + 1))
                        Repo.watchers.add(watcher)
                        Repo.save()
                        editingId = watcher.id
                    }
                }
            )
        }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Слежение за экранами", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (Engine.watchersLive) "работает" else "остановлено",
                            color = if (Engine.watchersLive) Mint else TextLo,
                            fontSize = 12.sp
                        )
                    }
                    GlassChip(if (Engine.watchersLive) "■ Стоп" else "▶ Старт", Engine.watchersLive) {
                        if (!com.creepyside.liquidclick.core.CaptureService.isReady()) {
                            Toast.makeText(context, "Разрешите захват экрана в разделе «Ещё»", Toast.LENGTH_SHORT).show()
                        } else {
                            Engine.toggleWatchers(context)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Когда в выделенной области появляется нужный текст — приложение нажимает в точке, запускает сценарий или останавливает работу.",
                    color = TextLo,
                    fontSize = 12.sp
                )
            }
        }

        if (Repo.watchers.isEmpty()) {
            item { EmptyHint("Наблюдений пока нет. Добавьте область и ожидаемый текст.") }
        }

        items(Repo.watchers, key = { it.id }) { watcher ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(watcher.name, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "«" + watcher.expected.ifBlank { "текст не задан" } + "» · " + MatchMode.title(watcher.matchMode),
                            color = TextLo,
                            fontSize = 12.sp
                        )
                    }
                    GlassChip(if (watcher.enabled) "вкл" else "выкл", watcher.enabled) {
                        val index = Repo.watchers.indexOfFirst { it.id == watcher.id }
                        if (index >= 0) {
                            Repo.watchers[index] = watcher.copy(enabled = !watcher.enabled)
                        }
                        Repo.save()
                    }
                }
                Spacer(Modifier.height(6.dp))
                ValueRow("Область", watcher.region.short(), Cyan)
                ValueRow(
                    "Действие",
                    when (watcher.action) {
                        "tap" -> "тап " + watcher.tapX + ", " + watcher.tapY
                        "scenario" -> "сценарий"
                        "stop" -> "остановить"
                        else -> watcher.action
                    },
                    Violet
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Проверить", Modifier.weight(1f)) {
                        scope.launch {
                            val text = Engine.previewRegion(watcher.region)
                            Toast.makeText(
                                context,
                                if (text.isBlank()) "Текст не распознан" else text.take(90),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    GlassButton("Изменить", Modifier.weight(1f)) { editingId = watcher.id }
                    GlassButton("Удалить", Modifier.weight(1f)) {
                        Repo.watchers.remove(watcher)
                        Repo.save()
                    }
                }
            }
        }
    }

    val editing = Repo.watchers.firstOrNull { it.id == editingId }
    if (editing != null) {
        WatcherEditor(editing) { editingId = null }
    }
}

@Composable
private fun WatcherEditor(original: Watcher, onClose: () -> Unit) {
    val context = LocalContext.current
    var draft by remember(original.id) { mutableStateOf(original) }
    var name by remember(original.id) { mutableStateOf(original.name) }
    var expected by remember(original.id) { mutableStateOf(original.expected) }
    var tapX by remember(original.id) { mutableStateOf(original.tapX.toString()) }
    var tapY by remember(original.id) { mutableStateOf(original.tapY.toString()) }

    fun commit() {
        val updated = draft.copy(
            name = if (name.isBlank()) "Наблюдение" else name,
            expected = expected,
            tapX = tapX.toIntOrNull() ?: draft.tapX,
            tapY = tapY.toIntOrNull() ?: draft.tapY
        )
        val index = Repo.watchers.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            Repo.watchers[index] = updated
        }
        Repo.save()
    }

    Dialog(
        onDismissRequest = {
            commit()
            onClose()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Наблюдение", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    GlassChip("Готово", true) {
                        commit()
                        onClose()
                    }
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    GlassField(name, { name = it }, "Название")
                    Spacer(Modifier.height(8.dp))
                    GlassField(expected, { expected = it }, "Ожидаемый текст", placeholder = "Например: Купить")
                    Spacer(Modifier.height(8.dp))
                    SectionTitle("Совпадение")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassChip("содержит", draft.matchMode == MatchMode.CONTAINS) {
                            draft = draft.copy(matchMode = MatchMode.CONTAINS)
                        }
                        GlassChip("точно", draft.matchMode == MatchMode.EXACT) {
                            draft = draft.copy(matchMode = MatchMode.EXACT)
                        }
                        GlassChip("regex", draft.matchMode == MatchMode.REGEX) {
                            draft = draft.copy(matchMode = MatchMode.REGEX)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ValueRow("Область", draft.region.short(), Cyan)
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Выделить область", Modifier.fillMaxWidth()) {
                        if (requireA11y(context)) {
                            Picker.pickRegion { region -> draft = draft.copy(region = region) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SectionTitle("Действие при совпадении")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassChip("тап", draft.action == "tap") { draft = draft.copy(action = "tap") }
                        GlassChip("сценарий", draft.action == "scenario") { draft = draft.copy(action = "scenario") }
                        GlassChip("стоп", draft.action == "stop") { draft = draft.copy(action = "stop") }
                    }
                    if (draft.action == "tap") {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassField(tapX, { tapX = it }, "X", Modifier.weight(1f))
                            GlassField(tapY, { tapY = it }, "Y", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        GlassButton("Указать точку нажатия", Modifier.fillMaxWidth()) {
                            if (requireA11y(context)) {
                                Picker.pickPoint { x, y ->
                                    tapX = x.toString()
                                    tapY = y.toString()
                                    draft = draft.copy(tapX = x, tapY = y)
                                }
                            }
                        }
                    }
                    if (draft.action == "scenario") {
                        Spacer(Modifier.height(8.dp))
                        if (Repo.scenarios.isEmpty()) {
                            Text("Нет сценариев", color = TextLo, fontSize = 12.sp)
                        } else {
                            Repo.scenarios.forEach { scenario ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        scenario.name,
                                        color = TextHi,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    GlassChip(
                                        if (draft.scenarioId == scenario.id) "выбран" else "выбрать",
                                        draft.scenarioId == scenario.id
                                    ) { draft = draft.copy(scenarioId = scenario.id) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Только во время сценария",
                            color = TextLo,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        GlassChip(if (draft.onlyInScenario) "да" else "нет", draft.onlyInScenario) {
                            draft = draft.copy(onlyInScenario = !draft.onlyInScenario)
                        }
                    }
                    SliderRow(
                        title = "Пауза между проверками",
                        value = draft.intervalMs.toFloat(),
                        min = 300f,
                        max = 5000f,
                        valueText = formatMs(draft.intervalMs)
                    ) { draft = draft.copy(intervalMs = it.toLong()) }
                    SliderRow(
                        title = "Кулдаун после срабатывания",
                        value = draft.cooldownMs.toFloat(),
                        min = 500f,
                        max = 30000f,
                        valueText = formatMs(draft.cooldownMs)
                    ) { draft = draft.copy(cooldownMs = it.toLong()) }
                    Spacer(Modifier.height(12.dp))
                    GlassButton("Отмена", Modifier.fillMaxWidth()) { onClose() }
                }
            }
        }
    }
}
