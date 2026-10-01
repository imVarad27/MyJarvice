package com.example.myjarvice.ui.main

import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.SmartMode

/** User-facing copy for response routing. Keep technical engine names out of primary UI. */
internal data class ResponseModeUi(
    val title: String,
    val compactLabel: String,
    val status: String,
    val description: String,
    val recommended: Boolean = false
)

internal fun responseModeUi(
    mode: SmartMode,
    connection: ConnectionStatus,
    hasLocalModel: Boolean
): ResponseModeUi = when (mode) {
    SmartMode.AUTO -> ResponseModeUi(
        title = "Automatic",
        compactLabel = "Auto",
        status = when {
            connection == ConnectionStatus.CONNECTED -> "Using PC now"
            connection == ConnectionStatus.CONNECTING -> "Connecting to PC"
            hasLocalModel -> "Using phone now"
            else -> "No model available"
        },
        description = "Uses your connected PC when available, then your phone model as a private fallback.",
        recommended = true
    )
    SmartMode.FAST_ON_DEVICE -> ResponseModeUi(
        title = "On this phone",
        compactLabel = "Phone",
        status = if (hasLocalModel) "Ready offline" else "Model required",
        description = "Private text replies that work without your PC. Your prompt stays on this device."
    )
    SmartMode.STRONG_HOST -> ResponseModeUi(
        title = "Connected PC",
        compactLabel = "PC",
        status = when (connection) {
            ConnectionStatus.CONNECTED -> "Connected"
            ConnectionStatus.CONNECTING -> "Connecting"
            ConnectionStatus.ERROR -> "Connection issue"
            ConnectionStatus.DISCONNECTED -> "Offline"
        },
        description = "Uses the model, web search, and tools configured on your paired computer."
    )
}

internal fun responseModeTitle(mode: SmartMode): String = when (mode) {
    SmartMode.AUTO -> "Automatic"
    SmartMode.FAST_ON_DEVICE -> "On this phone"
    SmartMode.STRONG_HOST -> "Connected PC"
}

internal fun responseModeCompactLabel(mode: SmartMode): String = when (mode) {
    SmartMode.AUTO -> "Auto"
    SmartMode.FAST_ON_DEVICE -> "Phone"
    SmartMode.STRONG_HOST -> "PC"
}

internal fun responseModeTopStatus(mode: SmartMode, connection: ConnectionStatus): String = when (mode) {
    SmartMode.FAST_ON_DEVICE -> "Private · on this phone"
    SmartMode.STRONG_HOST -> when (connection) {
        ConnectionStatus.CONNECTED -> "Connected PC · ready"
        ConnectionStatus.CONNECTING -> "Connected PC · connecting"
        ConnectionStatus.ERROR -> "Connected PC · check connection"
        ConnectionStatus.DISCONNECTED -> "Connected PC · offline"
    }
    SmartMode.AUTO -> when (connection) {
        ConnectionStatus.CONNECTED -> "Automatic · using PC"
        ConnectionStatus.CONNECTING -> "Automatic · connecting"
        else -> "Automatic · PC offline"
    }
}
