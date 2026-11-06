package com.creepyside.liquidclick.ui

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
import com.creepyside.liquidclick.model.Scenario
import com.creepyside.liquidclick.model.Step
import com.creepyside.liquidclick.model.StepType

@Composable
fun ScenariosScreen() {
    val context = LocalContext.current
    var editingId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "Сценарии",
                subtitle = "Шаги, паузы и условия",
                trailing = {
                    GlassButton("＋ Создать", accent = true) {
                        val scenario = Scenario(name = "Сценарий " + (Repo.scenarios.size + 1))
                        Repo.scenarios.add(scenario)
                        Repo.save()
                        editingId = scenario.id
                    }
                }
            )
        }

        if (Repo.scenarios.isEmpty()) {
            item {
                EmptyHint("Сценариев пока нет. Создайте первый — например «нажать, подождать текст, нажать по тексту».")
            }
        }

        items(Repo.scenarios, key = { it.id }) { scenario ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            scenario.name,
                            color = TextHi,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            scenario.steps.size.toString() + " шагов · " +
                                if (scenario.forever) "бесконечно" else scenario.loops.toString() + " цикл(ов)",
                            color = TextLo,
                            fontSize = 12.sp
                        )
                    }
                    GlassChip("▶ Запуск", true) {
                        if (requireA11y(context)) Engine.runScenario(context, scenario)
                    }
                }
                Spacer(Modifier.height(8.dp))
                scenario.steps.take(4).forEach { step ->
                    Text(
                        "• " + StepType.title(step.type) + " — " + step.summary(),
                        color = TextLo,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
                if (scenario.steps.isEmpty()) {
                    Text("Нет шагов", color = TextLo, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Изменить", Modifier.weight(1f)) { editingId = scenario.id }
                    GlassButton("Копия", Modifier.weight(1f)) {
                        val copy = Scenario(
                            name = scenario.name + " (копия)",
                            steps = scenario.steps.toMutableList(),
                            loops = scenario.loops,
                            forever = scenario.forever
                        )
                        Repo.scenarios.add(copy)
                        Repo.save()
                    }
                    GlassButton("Удалить", Modifier.weight(1f)) {
                        Repo.scenarios.remove(scenario)
                        Repo.save()
                    }
                }
            }
        }
    }

    val editing = Repo.scenarios.firstOrNull { it.id == editingId }
    if (editing != null) {
        ScenarioEditor(editing) { editingId = null }
    }
}

@Composable
private fun ScenarioEditor(scenario: Scenario, onClose: () -> Unit) {
    val context = LocalContext.current
    var name by remember(scenario.id) { mutableStateOf(scenario.name) }
    var steps by remember(scenario.id) { mutableStateOf(scenario.steps.toList()) }
    var stepIndex by remember { mutableStateOf(-1) }
    var showTypes by remember { mutableStateOf(false) }

    fun persist() {
        scenario.name = if (name.isBlank()) "Сценарий" else name
        scenario.steps.clear()
        scenario.steps.addAll(steps)
        val index = Repo.scenarios.indexOfFirst { it.id == scenario.id }
        if (index >= 0) {
            Repo.scenarios[index] = scenario.copy(steps = scenario.steps.toMutableList())
        }
        Repo.save()
    }

    Dialog(
        onDismissRequest = {
            persist()
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
                    Text("Сценарий", color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    GlassChip("Готово", true) {
                        persist()
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Бесконечный цикл", color = TextLo, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        GlassChip(if (scenario.forever) "да" else "нет", scenario.forever) {
                            scenario.forever = !scenario.forever
                        }
                    }
                    if (!scenario.forever) {
                        SliderRow(
                            title = "Повторов",
                            value = scenario.loops.toFloat(),
                            min = 1f,
                            max = 50f,
                            valueText = scenario.loops.toString()
                        ) { scenario.loops = it.toInt().coerceAtLeast(1) }
                    }
                    SliderRow(
                        title = "Пауза между циклами",
                        value = scenario.delayBetweenLoopsMs.toFloat(),
                        min = 0f,
                        max = 5000f,
                        valueText = formatMs(scenario.delayBetweenLoopsMs)
                    ) { scenario.delayBetweenLoopsMs = it.toLong() }

                    Spacer(Modifier.height(6.dp))
                    Divider()
                    Spacer(Modifier.height(10.dp))
                    SectionTitle("Шаги")
                    Spacer(Modifier.height(8.dp))

                    steps.forEachIndexed { index, step ->
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            GlassCard(corner = 18.dp, padding = 12.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        (index + 1).toString() + ". " + StepType.title(step.type),
                                        color = TextHi,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    GlassChip("✎", false) { stepIndex = index }
                                    Spacer(Modifier.width(6.dp))
                                    GlassChip("↑", false) {
                                        if (index > 0) {
                                            val list = steps.toMutableList()
                                            val prev = list[index - 1]
                                            list[index - 1] = list[index]
                                            list[index] = prev
                                            steps = list
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    GlassChip("✕", false) {
                                        steps = steps.filterIndexed { i, _ -> i != index }
                                    }
                                }
                                Text(step.summary(), color = TextLo, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    GlassButton(if (showTypes) "Скрыть типы" else "＋ Добавить шаг", Modifier.fillMaxWidth(), accent = !showTypes) {
                        showTypes = !showTypes
                    }
                    if (showTypes) {
                        Spacer(Modifier.height(8.dp))
                        val types = listOf(
                            StepType.TAP, StepType.LONG, StepType.SWIPE, StepType.TEXT,
                            StepType.WAIT, StepType.CHECK, StepType.BACK, StepType.HOME
                        )
                        types.chunked(2).forEach { pair ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                pair.forEach { type ->
                                    GlassChip(StepType.title(type), false, Modifier.weight(1f)) {
                                        val step = when (type) {
                                            StepType.CHECK -> newCheckStep()
                                            StepType.TEXT -> Step(type = StepType.TEXT, text = "")
                                            StepType.WAIT -> Step(type = StepType.WAIT, waitMs = 1000L)
                                            StepType.SWIPE -> Step(
                                                type = StepType.SWIPE,
                                                x = 540, y = 1400, x2 = 540, y2 = 600,
                                                durationMs = 300L
                                            )
                                            StepType.LONG -> Step(
                                                type = StepType.LONG,
                                                durationMs = 800L
                                            )
                                            else -> Step(type = type)
                                        }
                                        val list = steps.toMutableList()
                                        list.add(step)
                                        steps = list
                                        showTypes = false
                                        stepIndex = list.size - 1
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (stepIndex >= 0 && stepIndex < steps.size) {
        val step = steps[stepIndex]
        StepEditorDialog(step, onDismiss = { stepIndex = -1 }) { updated ->
            val list = steps.toMutableList()
            list[stepIndex] = updated
            steps = list
            stepIndex = -1
        }
    }
}

@Composable
private fun StepEditorDialog(
    original: Step,
    onDismiss: () -> Unit,
    onSave: (Step) -> Unit
) {
    val context = LocalContext.current
    var draft by remember(original.id) { mutableStateOf(original) }
    var xText by remember(original.id) { mutableStateOf(original.x.toString()) }
    var yText by remember(original.id) { mutableStateOf(original.y.toString()) }
    var x2Text by remember(original.id) { mutableStateOf(original.x2.toString()) }
    var y2Text by remember(original.id) { mutableStateOf(original.y2.toString()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        StepType.title(draft.type),
                        color = TextHi,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    GlassChip("Сохранить", true) {
                        val updated = draft.copy(
                            x = xText.toIntOrNull() ?: draft.x,
                            y = yText.toIntOrNull() ?: draft.y,
                            x2 = x2Text.toIntOrNull() ?: draft.x2,
                            y2 = y2Text.toIntOrNull() ?: draft.y2
                        )
                        onSave(updated)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (draft.type) {
                        StepType.TAP, StepType.LONG -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassField(xText, { xText = it }, "X", Modifier.weight(1f))
                                GlassField(yText, { yText = it }, "Y", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            GlassButton("Указать точку на экране", Modifier.fillMaxWidth()) {
                                if (requireA11y(context)) {
                                    Picker.pickPoint { x, y ->
                                        xText = x.toString()
                                        yText = y.toString()
                                        draft = draft.copy(x = x, y = y)
                                    }
                                }
                            }
                            if (draft.type == StepType.LONG) {
                                SliderRow(
                                    title = "Длительность зажатия",
                                    value = draft.durationMs.toFloat(),
                                    min = 100f,
                                    max = 10000f,
                                    valueText = formatMs(draft.durationMs)
                                ) { draft = draft.copy(durationMs = it.toLong()) }
                            }
                        }

                        StepType.SWIPE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassField(xText, { xText = it }, "X старт", Modifier.weight(1f))
                                GlassField(yText, { yText = it }, "Y старт", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassField(x2Text, { x2Text = it }, "X конец", Modifier.weight(1f))
                                GlassField(y2Text, { y2Text = it }, "Y конец", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassButton("Старт", Modifier.weight(1f)) {
                                    if (requireA11y(context)) {
                                        Picker.pickPoint { x, y ->
                                            xText = x.toString()
                                            yText = y.toString()
                                            draft = draft.copy(x = x, y = y)
                                        }
                                    }
                                }
                                GlassButton("Финиш", Modifier.weight(1f)) {
                                    if (requireA11y(context)) {
                                        Picker.pickPoint { x, y ->
                                            x2Text = x.toString()
                                            y2Text = y.toString()
                                            draft = draft.copy(x2 = x, y2 = y)
                                        }
                                    }
                                }
                            }
                            SliderRow(
                                title = "Длительность свайпа",
                                value = draft.durationMs.toFloat(),
                                min = 60f,
                                max = 3000f,
                                valueText = formatMs(draft.durationMs)
                            ) { draft = draft.copy(durationMs = it.toLong()) }
                        }

                        StepType.TEXT -> {
                            GlassField(
                                value = draft.text,
                                onValueChange = { draft = draft.copy(text = it) },
                                label = "Текст для вставки",
                                placeholder = "Например: hello@mail.com",
                                singleLine = false
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassField(xText, { xText = it }, "X поля", Modifier.weight(1f))
                                GlassField(yText, { yText = it }, "Y поля", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            GlassButton("Указать поле на экране", Modifier.fillMaxWidth()) {
                                if (requireA11y(context)) {
                                    Picker.pickPoint { x, y ->
                                        xText = x.toString()
                                        yText = y.toString()
                                        draft = draft.copy(x = x, y = y)
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Если координаты 0, текст вставится в уже активное поле ввода.",
                                color = TextLo,
                                fontSize = 11.sp
                            )
                        }

                        StepType.WAIT -> {
                            SliderRow(
                                title = "Пауза",
                                value = draft.waitMs.toFloat(),
                                min = 100f,
                                max = 30000f,
                                valueText = formatMs(draft.waitMs)
                            ) { draft = draft.copy(waitMs = it.toLong()) }
                        }

                        StepType.CHECK -> {
                            GlassField(
                                value = draft.expected,
                                onValueChange = { draft = draft.copy(expected = it) },
                                label = "Ожидаемый текст",
                                placeholder = "Например: Продолжить"
                            )
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
                            Spacer(Modifier.height(8.dp))
                            ValueRow("Область", draft.region?.short() ?: "не задана", Cyan)
                            Spacer(Modifier.height(8.dp))
                            GlassButton("Выделить область на экране", Modifier.fillMaxWidth()) {
                                if (requireA11y(context)) {
                                    Picker.pickRegion { region ->
                                        draft = draft.copy(region = region)
                                    }
                                }
                            }
                            SliderRow(
                                title = "Ждать появления",
                                value = draft.timeoutMs.toFloat(),
                                min = 500f,
                                max = 60000f,
                                valueText = formatMs(draft.timeoutMs)
                            ) { draft = draft.copy(timeoutMs = it.toLong()) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Нажать при совпадении", color = TextLo, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                GlassChip(if (draft.tapOnMatch) "да" else "нет", draft.tapOnMatch) {
                                    draft = draft.copy(tapOnMatch = !draft.tapOnMatch)
                                }
                            }
                            if (draft.tapOnMatch) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GlassField(xText, { xText = it }, "X", Modifier.weight(1f))
                                    GlassField(yText, { yText = it }, "Y", Modifier.weight(1f))
                                }
                                GlassButton("Указать точку нажатия", Modifier.fillMaxWidth()) {
                                    if (requireA11y(context)) {
                                        Picker.pickPoint { x, y ->
                                            xText = x.toString()
                                            yText = y.toString()
                                            draft = draft.copy(x = x, y = y)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Прервать сценарий, если текста нет", color = TextLo, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                GlassChip(if (draft.required) "да" else "нет", draft.required) {
                                    draft = draft.copy(required = !draft.required)
                                }
                            }
                        }

                        else -> {
                            Text(
                                "Шаг выполняется сразу, без параметров.",
                                color = TextLo,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassButton("Отмена", Modifier.fillMaxWidth()) { onDismiss() }
                }
            }
        }
    }
}

private fun newCheckStep(): Step = Step(
    type = StepType.CHECK,
    expected = "",
    region = null,
    timeoutMs = 8000L
)
