package com.creepyside.liquidclick.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.creepyside.liquidclick.core.A11y
import com.creepyside.liquidclick.core.CaptureService
import com.creepyside.liquidclick.data.Repo
import com.creepyside.liquidclick.model.ScreenSize

class MainActivity : ComponentActivity() {

    private lateinit var captureLauncher: ActivityResultLauncher<Intent>

    companion object {
        @Volatile
        var visible: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        readIntent(intent)
        captureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                CaptureService.pendingResultCode = result.resultCode
                CaptureService.pendingData = data
                CaptureService.start(this)
                Repo.captureReady = true
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001) }
        }
        setContent {
            LiquidClickTheme {
                LiquidApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        val scenarioId = intent?.getStringExtra("scenario_id")
        if (scenarioId != null) {
            Nav.pendingScenarioId = scenarioId
            Nav.pendingStepId = intent.getStringExtra("step_id")
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        Repo.a11yReady = A11y.ready()
        Repo.captureReady = CaptureService.isReady()
        val metrics = resources.displayMetrics
        Repo.screen = ScreenSize(metrics.widthPixels, metrics.heightPixels)
        com.creepyside.liquidclick.core.Bubbles.hideForRun()
    }

    override fun onPause() {
        visible = false
        com.creepyside.liquidclick.core.Bubbles.restoreAfterRun()
        super.onPause()
    }

    fun requestCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        runCatching { captureLauncher.launch(manager.createScreenCaptureIntent()) }
    }
}

private data class NavItem(val icon: ImageVector, val label: String)

@Composable
fun LiquidApp() {
    var tab by remember { mutableStateOf(0) }
    var showScenarioPicker by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Nav.pendingScenarioId) {
        if (Nav.pendingScenarioId != null) tab = 1
    }
    val items = listOf(
        NavItem(Icons.Rounded.PlayArrow, "Кликер"),
        NavItem(Icons.Rounded.List, "Сценарии"),
        NavItem(Icons.Rounded.Visibility, "Наблюдение"),
        NavItem(Icons.Rounded.Person, "Имена"),
        NavItem(Icons.Rounded.Settings, "Ещё")
    )
    Box(Modifier.fillMaxSize()) {
        GlassBackground()
        Row(Modifier.fillMaxSize()) {
            SideRail(
                items = items,
                selected = tab,
                onSelect = { tab = it },
                onPickScenario = { showScenarioPicker = true }
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (tab) {
                    0 -> ClickerScreen()
                    1 -> ScenariosScreen()
                    2 -> WatchScreen()
                    3 -> NamesScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }

    if (showScenarioPicker) {
        ScenarioPickerDialog(onClose = { showScenarioPicker = false })
    }
}

@Composable
private fun SideRail(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onPickScenario: () -> Unit
) {
    val activeScenario = Repo.findScenario(Repo.lastScenarioId)

    Column(
        Modifier
            .width(84.dp)
            .fillMaxHeight()
            .padding(start = 8.dp, top = 10.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.035f))
                )
            )
            .background(Color(0x66000000))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items.forEachIndexed { index, item ->
            val active = index == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (active) {
                            Brush.linearGradient(
                                listOf(Aqua.copy(alpha = 0.40f), Violet.copy(alpha = 0.30f))
                            )
                        } else {
                            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (active) TextHi else TextLo,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    item.label,
                    color = if (active) TextHi else TextLo,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .clickable { onPickScenario() }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("СЦЕНАРИЙ", color = TextLo, fontSize = 8.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                activeScenario?.name ?: "не выбран",
                color = if (activeScenario != null) Mint else TextLo,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ScenarioPickerDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(20.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Сценарий для запуска", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    GlassChip("Закрыть", false) { onClose() }
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (Repo.scenarios.isEmpty()) {
                        Text(
                            "Сценариев пока нет. Создайте в разделе «Сценарии».",
                            color = TextLo,
                            fontSize = 13.sp
                        )
                    }
                    Repo.scenarios.forEach { scenario ->
                        val active = scenario.id == Repo.lastScenarioId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (active) Aqua.copy(alpha = 0.18f) else Color.Transparent
                                )
                                .clickable {
                                    Repo.lastScenarioId = scenario.id
                                    Repo.save()
                                    onClose()
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    scenario.name,
                                    color = TextHi,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    scenario.steps.size.toString() + " шагов",
                                    color = TextLo,
                                    fontSize = 11.sp
                                )
                            }
                            if (active) {
                                Text("выбран", color = Cyan, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
