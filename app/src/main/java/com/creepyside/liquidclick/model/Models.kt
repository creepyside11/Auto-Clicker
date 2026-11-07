package com.creepyside.liquidclick.model

import java.util.UUID

object StepType {
    const val TAP = "tap"
    const val LONG = "long"
    const val SWIPE = "swipe"
    const val TEXT = "text"
    const val WAIT = "wait"
    const val CHECK = "check"
    const val BACK = "back"
    const val HOME = "home"
    const val OPEN = "open"

    fun title(type: String): String = when (type) {
        TAP -> "Тап"
        LONG -> "Зажатие"
        SWIPE -> "Свайп"
        TEXT -> "Вставка текста"
        WAIT -> "Пауза"
        CHECK -> "Условие по тексту"
        BACK -> "Назад"
        HOME -> "Домой"
        OPEN -> "Открыть приложение"
        else -> type
    }
}

object MatchMode {
    const val CONTAINS = "contains"
    const val EXACT = "exact"
    const val REGEX = "regex"

    fun title(mode: String): String = when (mode) {
        CONTAINS -> "содержит"
        EXACT -> "точно равно"
        REGEX -> "регулярное выражение"
        else -> mode
    }
}

data class Region(
    var left: Int = 0,
    var top: Int = 0,
    var right: Int = 0,
    var bottom: Int = 0
) {
    val w: Int get() = (right - left).coerceAtLeast(1)
    val h: Int get() = (bottom - top).coerceAtLeast(1)
    val valid: Boolean get() = right - left > 8 && bottom - top > 8
    fun cx(): Int = (left + right) / 2
    fun cy(): Int = (top + bottom) / 2
    fun copyRegion(): Region = Region(left, top, right, bottom)
    fun short(): String = if (valid) "$left,$top — $right,$bottom" else "не задана"
}

data class Step(
    var id: String = UUID.randomUUID().toString(),
    var type: String = StepType.TAP,
    var x: Int = 0,
    var y: Int = 0,
    var x2: Int = 0,
    var y2: Int = 0,
    var durationMs: Long = 60L,
    var waitMs: Long = 800L,
    var text: String = "",
    var region: Region? = null,
    var expected: String = "",
    var matchMode: String = MatchMode.CONTAINS,
    var timeoutMs: Long = 5000L,
    var tapOnMatch: Boolean = false,
    var required: Boolean = false
) {
    fun summary(): String = when (type) {
        StepType.TAP -> "тап $x, $y"
        StepType.LONG -> "зажатие $x, $y · $durationMs мс"
        StepType.SWIPE -> "свайп $x,$y → $x2,$y2"
        StepType.TEXT -> "текст «${text.take(18)}»" + if (x > 0 || y > 0) " в $x,$y" else ""
        StepType.WAIT -> "пауза $waitMs мс"
        StepType.CHECK -> "если «${expected.take(18)}» в области"
        StepType.BACK -> "кнопка Назад"
        StepType.HOME -> "кнопка Домой"
        StepType.OPEN -> "открыть " + text.substringAfterLast('.').take(22)
        else -> type
    }
}

data class Scenario(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "Новый сценарий",
    var steps: MutableList<Step> = mutableListOf(),
    var loops: Int = 1,
    var forever: Boolean = false,
    var delayBetweenLoopsMs: Long = 300L,
    // Смещение окна: сценарий задан в координатах окна, движок прибавляет это смещение.
    var offsetX: Int = 0,
    var offsetY: Int = 0,
    var offsetW: Int = 0,
    var offsetH: Int = 0
) {
    fun shiftedX(x: Int): Int = x + offsetX
    fun shiftedY(y: Int): Int = y + offsetY
}

data class Watcher(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "Наблюдение",
    var region: Region = Region(),
    var expected: String = "",
    var matchMode: String = MatchMode.CONTAINS,
    var action: String = "tap",
    var tapX: Int = 0,
    var tapY: Int = 0,
    var scenarioId: String = "",
    var intervalMs: Long = 700L,
    var cooldownMs: Long = 3000L,
    var enabled: Boolean = true,
    var onlyInScenario: Boolean = false
)

data class ClickConfig(
    var mode: String = StepType.TAP,
    var x: Int = 540,
    var y: Int = 1200,
    var x2: Int = 540,
    var y2: Int = 600,
    var durationMs: Long = 60L,
    var intervalMs: Long = 250L,
    var count: Int = 0,
    var jitter: Int = 0
)

/** Сохранённые имя/фамилия/ник, полученные при работе сценария или генератора. */
data class SavedPerson(
    var id: String = UUID.randomUUID().toString(),
    var first: String = "",
    var last: String = "",
    var nick: String = "",
    var region: String = "",
    var source: String = "",
    var scenarioName: String = "",
    var time: Long = System.currentTimeMillis()
) {
    fun full(): String = (first + " " + last).trim()
}

data class ScreenSize(var w: Int = 1080, var h: Int = 1920)
