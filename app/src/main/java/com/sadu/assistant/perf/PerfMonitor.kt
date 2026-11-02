package com.sadu.assistant.perf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The numbers the dashboard shows.
 *
 * One small object, written by the inference path and read by a screen that is usually closed.
 * Nothing here samples on its own: every field is something the turn already knew, so showing
 * the panel costs nothing while a model is generating.
 */
object PerfMonitor {

    data class Snapshot(
        val mode: PerfMode = PerfMode.Balanced,
        val threads: Int = 0,
        val heat: Heat = Heat.None,
        val availableMb: Int = 0,
        val totalMb: Int = 0,
        val processMb: Int = 0,
        val modelMb: Long = 0L,
        val contextTokens: Int = 0,
        val promptTokS: Double = 0.0,
        val genTokS: Double = 0.0,
        val genTokens: Int = 0,
        val promptMs: Long = 0L,
        val genMs: Long = 0L,
        val lastTurnMs: Long = 0L,
        val running: Boolean = false,
        val background: Boolean = false,
    )

    var snapshot by mutableStateOf(Snapshot())
        private set

    fun beginTurn(mode: PerfMode, threads: Int, device: DeviceState, modelMb: Long, context: Int) {
        snapshot = snapshot.copy(
            mode = mode,
            threads = threads,
            heat = device.heat,
            availableMb = device.availableMb,
            totalMb = device.totalMb,
            processMb = device.processMb,
            modelMb = modelMb,
            contextTokens = context,
            running = true,
        )
    }

    fun endTurn(promptTokS: Double, genTokS: Double, genTokens: Int, promptMs: Long, genMs: Long, turnMs: Long, device: DeviceState) {
        snapshot = snapshot.copy(
            promptTokS = promptTokS,
            genTokS = genTokS,
            genTokens = genTokens,
            promptMs = promptMs,
            genMs = genMs,
            lastTurnMs = turnMs,
            heat = device.heat,
            availableMb = device.availableMb,
            processMb = device.processMb,
            running = false,
        )
    }

    fun setBackground(on: Boolean) {
        snapshot = snapshot.copy(background = on)
    }

    fun setMode(mode: PerfMode) {
        snapshot = snapshot.copy(mode = mode)
    }
}
