package com.creepyside.liquidclick.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.creepyside.liquidclick.model.ClickConfig
import com.creepyside.liquidclick.model.MatchMode
import com.creepyside.liquidclick.model.Region
import com.creepyside.liquidclick.model.Scenario
import com.creepyside.liquidclick.model.ScreenSize
import com.creepyside.liquidclick.model.Step
import com.creepyside.liquidclick.model.StepType
import com.creepyside.liquidclick.model.Watcher
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Repo {

    private val gson = Gson()
    private var prefs: SharedPreferences? = null
    private var loaded = false

    val scenarios = mutableStateListOf<Scenario>()
    val watchers = mutableStateListOf<Watcher>()

    var click by mutableStateOf(ClickConfig())
    var screen by mutableStateOf(ScreenSize())
    var a11yReady by mutableStateOf(false)
    var captureReady by mutableStateOf(false)
    var panelEnabled by mutableStateOf(false)
    var watchersEnabled by mutableStateOf(false)
    var lastScenarioId by mutableStateOf("")

    fun init(c: Context) {
        if (loaded) return
        loaded = true
        val p = c.applicationContext.getSharedPreferences("liquidclick", Context.MODE_PRIVATE)
        prefs = p
        runCatching {
            p.getString("click", null)?.let { click = gson.fromJson(it, ClickConfig::class.java) }
        }
        val listType = object : TypeToken<MutableList<Scenario>>() {}.type
        val watchType = object : TypeToken<MutableList<Watcher>>() {}.type
        runCatching {
            p.getString("scenarios", null)?.let { json ->
                val list: MutableList<Scenario>? = gson.fromJson(json, listType)
                list?.let { scenarios.addAll(it) }
            }
        }
        runCatching {
            p.getString("watchers", null)?.let { json ->
                val list: MutableList<Watcher>? = gson.fromJson(json, watchType)
                list?.let { watchers.addAll(it) }
            }
        }
        lastScenarioId = p.getString("lastScenario", "") ?: ""
        panelEnabled = p.getBoolean("panel", false)
        if (scenarios.isEmpty()) seed()
    }

    fun save() {
        val p = prefs ?: return
        runCatching {
            p.edit()
                .putString("scenarios", gson.toJson(scenarios))
                .putString("watchers", gson.toJson(watchers))
                .putString("click", gson.toJson(click))
                .putString("lastScenario", lastScenarioId)
                .putBoolean("panel", panelEnabled)
                .apply()
        }
    }

    private fun seed() {
        val demo = Scenario(
            name = "Демо: ждём текст и жмём",
            steps = mutableListOf(
                Step(type = StepType.TAP, x = 540, y = 1600),
                Step(type = StepType.WAIT, waitMs = 1200L),
                Step(
                    type = StepType.CHECK,
                    expected = "Далее",
                    region = Region(0, 1450, 1080, 1750),
                    timeoutMs = 8000L,
                    tapOnMatch = true,
                    x = 540,
                    y = 1600,
                    required = true
                )
            ),
            loops = 1,
            forever = false
        )
        if (demo.steps.isNotEmpty()) scenarios.add(demo)
        watchers.add(
            Watcher(
                name = "Пример: кнопка «Продолжить»",
                region = Region(80, 1500, 1000, 1750),
                expected = "Продолжить",
                matchMode = MatchMode.CONTAINS,
                action = "tap",
                tapX = 540,
                tapY = 1620,
                enabled = false
            )
        )
        save()
    }

    fun newId(): String = java.util.UUID.randomUUID().toString()

    fun findScenario(id: String): Scenario? = scenarios.firstOrNull { it.id == id }
}
