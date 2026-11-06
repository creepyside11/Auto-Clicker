package com.creepyside.liquidclick.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Отложенная навигация из оверлеев в приложение (открыть сценарий/шаг). */
object Nav {
    var pendingScenarioId by mutableStateOf<String?>(null)
    var pendingStepId by mutableStateOf<String?>(null)

    fun clear() {
        pendingScenarioId = null
        pendingStepId = null
    }
}
