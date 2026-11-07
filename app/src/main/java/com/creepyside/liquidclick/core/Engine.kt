package com.creepyside.liquidclick.core

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.creepyside.liquidclick.data.Repo
import com.creepyside.liquidclick.model.MatchMode
import com.creepyside.liquidclick.names.Names
import com.creepyside.liquidclick.model.Region
import com.creepyside.liquidclick.model.Scenario
import com.creepyside.liquidclick.model.Step
import com.creepyside.liquidclick.model.StepType
import com.creepyside.liquidclick.model.Watcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object Engine {

    var running by mutableStateOf(false)
        private set

    var runningName by mutableStateOf("")
        private set

    var watchersLive by mutableStateOf(false)
        private set

    val logs = mutableStateListOf<String>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var offsetX = 0
    private var offsetY = 0
    private var watchJob: Job? = null
    private val lastFired = mutableMapOf<String, Long>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val line = timeFormat.format(Date()) + "  " + message
        logs.add(0, line)
        while (logs.size > 250) {
            logs.removeAt(logs.lastIndex)
        }
    }

    // ---------- Автокликер ----------

    fun startClicker(ctx: Context) {
        stopScenario()
        val cfg = Repo.click
        running = true
        runningName = "Автокликер"
        log("▶ Кликер: " + StepType.title(cfg.mode) + ", интервал " + cfg.intervalMs + " мс")
        job = scope.launch {
            var done = 0
            try {
                while (isActive) {
                    val service = A11y.instance
                    if (service == null) {
                        log("✖ Служба доступности не включена")
                        break
                    }
                    val jx = if (cfg.jitter > 0) Random.nextInt(-cfg.jitter, cfg.jitter + 1) else 0
                    val jy = if (cfg.jitter > 0) Random.nextInt(-cfg.jitter, cfg.jitter + 1) else 0
                    when (cfg.mode) {
                        StepType.SWIPE -> service.swipe(
                            cfg.x.toFloat() + jx,
                            cfg.y.toFloat() + jy,
                            cfg.x2.toFloat() + jx,
                            cfg.y2.toFloat() + jy,
                            cfg.durationMs
                        )

                        StepType.LONG -> service.longPress(
                            cfg.x.toFloat() + jx,
                            cfg.y.toFloat() + jy,
                            cfg.durationMs
                        )

                        else -> service.tap(cfg.x.toFloat() + jx, cfg.y.toFloat() + jy)
                    }
                    done++
                    if (cfg.count > 0 && done >= cfg.count) {
                        break
                    }
                    delay(cfg.intervalMs.coerceAtLeast(10))
                }
            } catch (e: CancellationException) {
                // остановлено пользователем
            } catch (e: Throwable) {
                log("✖ " + (e.message ?: "ошибка"))
            } finally {
                log("■ Кликер остановлен (выполнено " + done + ")")
                running = false
                runningName = ""
            }
        }
    }

    // ---------- Сценарии ----------

    fun runScenario(ctx: Context, scenario: Scenario) {
        stopScenario()
        Repo.lastScenarioId = scenario.id
        Repo.save()
        running = true
        runningName = scenario.name
        offsetX = scenario.offsetX
        offsetY = scenario.offsetY
        Bubbles.hideForRun()
        log("▶ " + scenario.name)
        job = scope.launch {
            var loop = 0
            try {
                while (isActive) {
                    loop++
                    if (!playOnce(ctx, scenario)) break
                    if (!scenario.forever && loop >= scenario.loops.coerceAtLeast(1)) break
                    delay(scenario.delayBetweenLoopsMs.coerceAtLeast(0))
                }
            } catch (e: CancellationException) {
                // остановлено
            } catch (e: Throwable) {
                log("✖ " + (e.message ?: "ошибка"))
            } finally {
                running = false
                runningName = ""
                offsetX = 0
                offsetY = 0
                Bubbles.restoreAfterRun()
                log("■ " + scenario.name + " завершён")
            }
        }
    }

    /**
     * Подставляет {имя}/{фамилия}/{ник} в текст шага и сохраняет полученную персону в приложении.
     */
    private fun resolveText(template: String): String {
        if (!Names.hasTokens(template)) return template
        val person = Names.onePerson(
            gender = Repo.nameGender,
            regions = Repo.nameRegions,
            custom = Repo.nameCustom,
            appendCustom = Repo.nameAppendCustom
        ) ?: return template
        Repo.addSaved(
            first = person.first,
            last = person.last,
            nick = person.nick,
            region = person.region,
            source = "сценарий",
            scenarioName = runningName
        )
        val text = Names.applyTokens(template, person)
        log("✎ " + person.full() + " · " + person.nick + " → сохранено")
        return text
    }

    private suspend fun playOnce(ctx: Context, scenario: Scenario): Boolean {
        offsetX = scenario.offsetX
        offsetY = scenario.offsetY
        for (step in scenario.steps) {
            if (!currentCoroutineContext().isActive) return false
            if (!exec(ctx, step)) return false
        }
        return true
    }

    private suspend fun exec(ctx: Context, step: Step): Boolean {
        val service = A11y.instance
        if (service == null && step.type != StepType.WAIT) {
            log("✖ Служба доступности не включена")
            return false
        }
        when (step.type) {
            StepType.TAP -> {
                service!!.tap((step.x + offsetX).toFloat(), (step.y + offsetY).toFloat())
                log("• тап " + (step.x + offsetX) + ", " + (step.y + offsetY))
                delay(90)
            }

            StepType.LONG -> {
                service!!.longPress(
                    (step.x + offsetX).toFloat(),
                    (step.y + offsetY).toFloat(),
                    step.durationMs
                )
                log("• зажатие " + step.durationMs + " мс")
                delay(90)
            }

            StepType.SWIPE -> {
                service!!.swipe(
                    (step.x + offsetX).toFloat(),
                    (step.y + offsetY).toFloat(),
                    (step.x2 + offsetX).toFloat(),
                    (step.y2 + offsetY).toFloat(),
                    step.durationMs
                )
                log("• свайп " + step.x + "," + step.y + " → " + step.x2 + "," + step.y2)
                delay(90)
            }

            StepType.TEXT -> {
                if (step.x > 0 && step.y > 0) {
                    service!!.tap((step.x + offsetX).toFloat(), (step.y + offsetY).toFloat())
                    delay(380)
                }
                val text = resolveText(step.text)
                val ok = service!!.typeText(text)
                log(if (ok) "• текст вставлен" else "✖ поле ввода не найдено")
                delay(150)
            }

            StepType.WAIT -> {
                log("• пауза " + step.waitMs + " мс")
                delay(step.waitMs.coerceAtLeast(1))
            }

            StepType.BACK -> {
                service!!.pressBack()
                log("• назад")
                delay(250)
            }

            StepType.HOME -> {
                service!!.pressHome()
                log("• домой")
                delay(250)
            }

            StepType.OPEN -> {
                val pkg = step.text.trim()
                if (pkg.isEmpty()) {
                    log("✖ не указан пакет приложения")
                } else {
                    val ok = launchPackage(ctx, pkg, offsetX, offsetY, step.x, step.y, step.x2, step.y2)
                    log(if (ok) "• открыто " + pkg else "✖ не удалось открыть " + pkg)
                    delay(step.waitMs.coerceAtLeast(200))
                }
            }

            StepType.CHECK -> return check(ctx, step)
        }
        return true
    }

    private suspend fun check(ctx: Context, step: Step): Boolean {
        val region = step.region?.let { r ->
            Region(r.left + offsetX, r.top + offsetY, r.right + offsetX, r.bottom + offsetY)
        }
        if (region == null || !region.valid) {
            log("✖ область условия не задана")
            return !step.required
        }
        if (step.expected.isBlank()) {
            log("✖ ожидаемый текст не задан")
            return !step.required
        }
        val deadline = System.currentTimeMillis() + step.timeoutMs.coerceAtLeast(300)
        while (System.currentTimeMillis() < deadline) {
            val text = readRegion(region)
            if (matches(text, step.expected, step.matchMode)) {
                log("✔ найдено «" + step.expected + "»")
                if (step.tapOnMatch) {
                    A11y.instance?.tap((step.x + offsetX).toFloat(), (step.y + offsetY).toFloat())
                    delay(140)
                }
                return true
            }
            delay(450)
        }
        log("✖ не найдено «" + step.expected + "» за " + step.timeoutMs + " мс")
        return !step.required
    }

    // ---------- Наблюдение ----------

    fun startWatchers(ctx: Context) {
        if (watchJob?.isActive == true) return
        watchersLive = true
        watchJob = scope.launch {
            log("◎ Наблюдение включено")
            try {
                while (isActive) {
                    val list = Repo.watchers.filter { it.enabled }
                    if (list.isEmpty() || !CaptureService.isReady()) {
                        delay(1200)
                        continue
                    }
                    for (watcher in list) {
                        if (!isActive) break
                        if (!watcher.enabled) continue
                        if (watcher.onlyInScenario && !running) continue
                        val now = System.currentTimeMillis()
                        val previous = lastFired[watcher.id] ?: 0L
                        if (now - previous < watcher.cooldownMs) continue
                        val region = watcher.region
                        if (!region.valid) continue
                        val text = readRegion(region)
                        if (matches(text, watcher.expected, watcher.matchMode)) {
                            lastFired[watcher.id] = now
                            fire(ctx, watcher)
                        }
                    }
                    delay(350)
                }
            } catch (e: CancellationException) {
                // остановлено
            } catch (e: Throwable) {
                log("✖ " + (e.message ?: "ошибка"))
            } finally {
                watchersLive = false
                log("◎ Наблюдение выключено")
            }
        }
    }

    private fun fire(ctx: Context, watcher: Watcher) {
        log("◎ " + watcher.name + ": «" + watcher.expected + "»")
        when (watcher.action) {
            "tap" -> A11y.instance?.tap(watcher.tapX.toFloat(), watcher.tapY.toFloat())
            "scenario" -> Repo.findScenario(watcher.scenarioId)?.let { runScenario(ctx, it) }
            "stop" -> stopAll()
        }
    }

    fun stopWatchers() {
        watchJob?.cancel()
        watchJob = null
        watchersLive = false
    }

    fun toggleWatchers(ctx: Context) {
        if (watchJob?.isActive == true) stopWatchers() else startWatchers(ctx)
    }

    // ---------- Общее ----------

    fun stopScenario() {
        job?.cancel()
        job = null
        running = false
        runningName = ""
    }

    fun stopAll() {
        stopScenario()
        stopWatchers()
        Repo.watchersEnabled = false
    }

    /** Цикл «два окна»: открыть левое → сценарий → открыть правое → сценарий → свернуть. */
    fun runPair(
        ctx: Context,
        leftPkg: String,
        rightPkg: String,
        leftScenario: Scenario?,
        rightScenario: Scenario?,
        loops: Int = 1,
        forever: Boolean = false,
        collapseEachLoop: Boolean = true
    ) {
        stopScenario()
        running = true
        runningName = "Два окна"
        log("▶ Два окна: " + leftPkg.substringAfterLast('.') + " | " + rightPkg.substringAfterLast('.'))
        job = scope.launch {
            val leftBounds = Windows.boundsFor(Windows.LEFT)
            val rightBounds = Windows.boundsFor(Windows.RIGHT)
            var loop = 0
            try {
                while (isActive) {
                    loop++
                    log("· круг " + loop)

                    Windows.open(ctx, leftPkg, Windows.LEFT)
                    delay(1200)
                    leftScenario?.let {
                        it.offsetX = leftBounds.left
                        it.offsetY = leftBounds.top
                        it.offsetW = leftBounds.width()
                        it.offsetH = leftBounds.height()
                        if (!playOnce(ctx, it)) log("✖ левый сценарий прерван")
                    }

                    Windows.open(ctx, rightPkg, Windows.RIGHT)
                    delay(1200)
                    rightScenario?.let {
                        it.offsetX = rightBounds.left
                        it.offsetY = rightBounds.top
                        it.offsetW = rightBounds.width()
                        it.offsetH = rightBounds.height()
                        if (!playOnce(ctx, it)) log("✖ правый сценарий прерван")
                    }

                    if (collapseEachLoop) {
                        A11y.instance?.pressHome()
                        log("· свернул оба")
                        delay(700)
                    }
                    if (!forever && loop >= loops.coerceAtLeast(1)) break
                    delay(400)
                }
            } catch (e: CancellationException) {
                // остановлено
            } catch (e: Throwable) {
                log("✖ " + (e.message ?: "ошибка"))
            } finally {
                running = false
                runningName = ""
                offsetX = 0
                offsetY = 0
                log("■ Два окна: стоп")
            }
        }
    }

    fun launchPackage(
        ctx: Context,
        pkg: String,
        ox: Int = 0,
        oy: Int = 0,
        x: Int = 0,
        y: Int = 0,
        x2: Int = 0,
        y2: Int = 0
    ): Boolean {
        val width = if (x2 > x) x2 - x else 0
        val height = if (y2 > y) y2 - y else 0
        return Windows.openInBounds(ctx, pkg, ox, oy, width, height)
    }

    suspend fun readRegion(region: Region): String {
        val full = CaptureService.capture() ?: return ""
        return try {
            cropAndRead(full, region)
        } finally {
            full.recycle()
        }
    }

    suspend fun previewRegion(region: Region): String {
        val full = CaptureService.capture() ?: return ""
        return try {
            cropAndRead(full, region)
        } finally {
            full.recycle()
        }
    }

    private suspend fun cropAndRead(full: Bitmap, region: Region): String {
        val left = region.left.coerceIn(0, (full.width - 2).coerceAtLeast(0))
        val top = region.top.coerceIn(0, (full.height - 2).coerceAtLeast(0))
        val w = region.w.coerceAtMost(full.width - left).coerceAtLeast(2)
        val h = region.h.coerceAtMost(full.height - top).coerceAtLeast(2)
        val crop = runCatching { Bitmap.createBitmap(full, left, top, w, h) }.getOrNull() ?: return ""
        return try {
            Ocr.read(crop)
        } catch (e: Throwable) {
            ""
        } finally {
            crop.recycle()
        }
    }

    fun matches(text: String, expected: String, mode: String): Boolean {
        if (expected.isBlank()) return false
        val haystack = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val needle = expected.trim()
        if (haystack.isEmpty()) return false
        return when (mode) {
            MatchMode.EXACT -> haystack.equals(needle, ignoreCase = true)
            MatchMode.REGEX -> runCatching {
                Regex(needle, RegexOption.IGNORE_CASE).containsMatchIn(haystack)
            }.getOrDefault(false)

            else -> haystack.contains(needle, ignoreCase = true)
        }
    }
}
