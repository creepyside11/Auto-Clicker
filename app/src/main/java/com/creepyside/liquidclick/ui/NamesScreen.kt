package com.creepyside.liquidclick.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.creepyside.liquidclick.data.Repo
import com.creepyside.liquidclick.names.Generated
import com.creepyside.liquidclick.names.Names

@Composable
fun NamesScreen() {
    var mode by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassChip("Имена", mode == 0, Modifier.weight(1f)) { mode = 0 }
            GlassChip("Свой текст", mode == 1, Modifier.weight(1f)) { mode = 1 }
            GlassChip("Сохранённые", mode == 2, Modifier.weight(1f)) { mode = 2 }
        }
        when (mode) {
            0 -> NamesGenerator()
            1 -> ManualText()
            else -> SavedScreen()
        }
    }
}

@Composable
private fun NamesGenerator() {
    val context = LocalContext.current
    var count by remember { mutableStateOf(12f) }
    var results by remember { mutableStateOf(listOf<Generated>()) }
    val selected = if (Repo.nameRegions.isEmpty()) Names.regions.toSet() else Repo.nameRegions.toSet()
    val gender = Repo.nameGender
    val mix = Repo.nameMix
    val custom = Repo.nameCustom
    val appendCustom = Repo.nameAppendCustom

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Генератор имён", "Имя и фамилия отдельно, только латиницей") }

        item {
            GlassCard {
                SectionTitle("Регионы")
                Spacer(Modifier.height(10.dp))
                Names.regions.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { region ->
                            GlassChip(region, selected.contains(region), Modifier.weight(1f)) {
                                val next = if (selected.contains(region)) selected - region else selected + region
                                Repo.nameRegions.clear()
                                Repo.nameRegions.addAll(next)
                                Repo.touch()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SectionTitle("Пол")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Любой", gender == "any") { Repo.nameGender = "any"; Repo.touch() }
                    GlassChip("Мужской", gender == "m") { Repo.nameGender = "m"; Repo.touch() }
                    GlassChip("Женский", gender == "f") { Repo.nameGender = "f"; Repo.touch() }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Смешивать регионы (имя и фамилия из разных культур)",
                        color = TextLo,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    GlassChip(if (mix) "да" else "нет", mix) { Repo.nameMix = !mix; Repo.touch() }
                }
                SliderRow(
                    title = "Сколько сгенерировать",
                    value = count,
                    min = 1f,
                    max = 50f,
                    valueText = count.toInt().toString()
                ) { count = it }
            }
        }

        item {
            GlassCard {
                SectionTitle("Своё слово или цифры в нике")
                Spacer(Modifier.height(8.dp))
                GlassField(
                    value = custom,
                    onValueChange = { Repo.nameCustom = it; Repo.touch() },
                    label = "Своё слово / цифры",
                    placeholder = "например: pro, 228, x"
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Добавлять к нику",
                        color = TextLo,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    GlassChip(if (appendCustom) "да" else "нет", appendCustom) {
                        Repo.nameAppendCustom = !appendCustom
                        Repo.touch()
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Имя и фамилия всегда только латиницей и без цифр. Ник — латиница имени и фамилии. " +
                        "В сценариях эти настройки применяются к подстановкам {имя}, {фамилия}, {ник}.",
                    color = TextLo,
                    fontSize = 11.sp
                )
            }
        }

        item {
            GlassButton("Сгенерировать", Modifier.fillMaxWidth(), accent = true) {
                results = Names.generate(
                    selected = selected.toList(),
                    gender = gender,
                    count = count.toInt().coerceAtLeast(1),
                    mixRegions = mix,
                    custom = custom,
                    appendCustom = appendCustom
                )
                results.forEach { person ->
                    Repo.addSaved(
                        first = person.first,
                        last = person.last,
                        nick = person.nick,
                        region = person.region,
                        source = "генератор"
                    )
                }
            }
        }

        if (results.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Результат · " + results.size)
                    GlassChip("Копировать всё", false) {
                        copyToClipboard(
                            context,
                            results.joinToString("\n") { it.first + " " + it.last }
                        )
                    }
                }
            }
            items(results) { item ->
                GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("Имя: ", color = TextLo, fontSize = 12.sp)
                                Text(
                                    item.first,
                                    color = TextHi,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("Фамилия: ", color = TextLo, fontSize = 12.sp)
                                Text(
                                    item.last,
                                    color = TextHi,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text("ник: " + item.nick, color = Cyan, fontSize = 12.sp)
                            Text(item.region, color = TextLo, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            GlassChip("Имя", false) { copyToClipboard(context, item.first) }
                            Spacer(Modifier.height(4.dp))
                            GlassChip("Фамилия", false) { copyToClipboard(context, item.last) }
                            Spacer(Modifier.height(4.dp))
                            GlassChip("Ник", false) { copyToClipboard(context, item.nick) }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "База: " + Names.allNamesCount() + " имён и фамилий из " + Names.regions.size + " регионов мира.",
                color = TextLo,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ManualText() {
    val context = LocalContext.current
    var raw by remember { mutableStateOf("") }
    var numbering by remember { mutableStateOf(false) }
    var startAt by remember { mutableStateOf("1") }
    var results by remember { mutableStateOf(listOf<String>()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Свой текст", "Полностью ручной ввод — без имён и фамилий") }

        item {
            GlassCard {
                SectionTitle("Ваш текст")
                Spacer(Modifier.height(8.dp))
                GlassField(
                    value = raw,
                    onValueChange = { raw = it },
                    label = "Каждая строка — отдельный результат",
                    placeholder = "первая строка\nвторая строка\nтретья",
                    singleLine = false
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Строки можно разделять переводом строки, точкой с запятой или запятой. " +
                        "Всё, что вы ввели, попадает в результат как есть.",
                    color = TextLo,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Нумеровать строки", color = TextLo, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    GlassChip(if (numbering) "да" else "нет", numbering) { numbering = !numbering }
                }
                if (numbering) {
                    GlassField(startAt, { startAt = it.filter { ch -> ch.isDigit() } }, "Начать с")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Показать", Modifier.weight(1f), accent = true) {
                    results = Names.manualResults(
                        raw,
                        numbering,
                        startAt.toIntOrNull() ?: 1
                    )
                }
                GlassButton("В быстрые тексты", Modifier.weight(1f)) {
                    val lines = Names.manualLines(raw)
                    if (lines.isEmpty()) return@GlassButton
                    lines.forEach { line ->
                        Repo.quickTexts.remove(line)
                        Repo.quickTexts.add(0, line)
                    }
                    while (Repo.quickTexts.size > 30) Repo.quickTexts.removeAt(Repo.quickTexts.lastIndex)
                    Repo.touch()
                    copyToClipboard(context, "Сохранено: " + lines.size)
                }
            }
        }

        if (results.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Результат · " + results.size)
                    GlassChip("Копировать всё", false) {
                        copyToClipboard(context, results.joinToString("\n"))
                    }
                }
            }
            items(results) { line ->
                GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            line,
                            color = TextHi,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        GlassChip("Копировать", false) { copyToClipboard(context, line) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedScreen() {
    val context = LocalContext.current
    Repo.version
    var query by remember { mutableStateOf("") }
    val rows = if (query.isBlank()) Repo.saved else Repo.saved.filter {
        it.first.contains(query, true) || it.last.contains(query, true) || it.nick.contains(query, true)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader("Сохранённые", "Ники и фамилии из сценариев и генератора · время МСК") }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Сохранять при работе сценария", color = TextHi, fontSize = 14.sp)
                        Text(
                            "Каждая подстановка {имя} / {фамилия} / {ник} попадает в этот список",
                            color = TextLo,
                            fontSize = 11.sp
                        )
                    }
                    GlassChip(if (Repo.saveGenerated) "вкл" else "выкл", Repo.saveGenerated) {
                        Repo.saveGenerated = !Repo.saveGenerated
                        Repo.touch()
                    }
                }
                Spacer(Modifier.height(10.dp))
                GlassField(query, { query = it }, "Поиск", placeholder = "имя, фамилия или ник")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Всего: " + Repo.saved.size, false, Modifier.weight(1f)) {}
                    GlassChip("Копировать всё", false, Modifier.weight(1f)) {
                        copyToClipboard(context, Repo.savedText())
                    }
                    GlassChip("Очистить", false, Modifier.weight(1f)) { Repo.clearSaved() }
                }
            }
        }

        if (rows.isEmpty()) {
            item { EmptyHint("Пока пусто. Запустите сценарий с шагом «Вставка текста», где есть {имя} или {фамилия}.") }
        }

        items(rows) { row ->
            GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.full(), color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (row.nick.isNotBlank()) Text(row.nick, color = Cyan, fontSize = 12.sp)
                        Text(
                            Repo.savedTime(row) + " · " + row.source +
                                if (row.scenarioName.isNotBlank()) " · " + row.scenarioName else "",
                            color = TextLo,
                            fontSize = 10.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        GlassChip("Копировать", false) {
                            copyToClipboard(context, listOf(row.first, row.last, row.nick)
                                .filter { it.isNotBlank() }.joinToString(" "))
                        }
                        Spacer(Modifier.height(4.dp))
                        GlassChip("✕", false) {
                            Repo.saved.removeAll { it.id == row.id }
                            Repo.touch()
                        }
                    }
                }
            }
        }
    }
}
