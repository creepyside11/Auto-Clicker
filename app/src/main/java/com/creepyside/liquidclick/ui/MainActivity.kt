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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    override fun onResume() {
        super.onResume()
        visible = true
        Repo.a11yReady = A11y.ready()
        Repo.captureReady = CaptureService.isReady()
        val metrics = resources.displayMetrics
        Repo.screen = ScreenSize(metrics.widthPixels, metrics.heightPixels)
    }

    override fun onPause() {
        visible = false
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
    val items = listOf(
        NavItem(Icons.Rounded.PlayArrow, "Кликер"),
        NavItem(Icons.Rounded.List, "Сценарии"),
        NavItem(Icons.Rounded.Visibility, "Наблюдение"),
        NavItem(Icons.Rounded.Person, "Имена"),
        NavItem(Icons.Rounded.Settings, "Ещё")
    )
    Box(Modifier.fillMaxSize()) {
        GlassBackground()
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> ClickerScreen()
                    1 -> ScenariosScreen()
                    2 -> WatchScreen()
                    3 -> NamesScreen()
                    else -> SettingsScreen()
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
                        )
                    )
                    .background(Color(0x66000000))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                items.forEachIndexed { index, item ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (index == tab) {
                                    Brush.linearGradient(
                                        listOf(Aqua.copy(alpha = 0.35f), Violet.copy(alpha = 0.30f))
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(Color.Transparent, Color.Transparent)
                                    )
                                }
                            )
                            .clickable { tab = index }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (index == tab) TextHi else TextLo,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.label,
                            color = if (index == tab) TextHi else TextLo,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
