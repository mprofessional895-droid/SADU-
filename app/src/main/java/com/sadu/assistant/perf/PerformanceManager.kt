package com.sadu.assistant.perf

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import com.sadu.assistant.brain.CpuTopology

/** How much of the phone the model is allowed to use. */
enum class PerfMode(val tag: String, val label: String) {
    Performance("performance", "Performance"),
    Balanced("balanced", "Balanced"),
    Saver("saver", "Battery saver"),
    ;

    companion object {
        fun from(tag: String?): PerfMode = entries.firstOrNull { it.tag == tag } ?: Balanced
    }
}

/** What the platform will say about heat, and how worried to be about it. */
enum class Heat { None, Light, Moderate, Severe, Critical }

/** A thermometer-free view of the device: thermal band, free RAM, this process's size. */
data class DeviceState(
    val heat: Heat = Heat.None,
    val availableMb: Int = 0,
    val totalMb: Int = 0,
    val lowMemory: Boolean = false,
    val processMb: Int = 0,
) {
    /** Under a twelfth of RAM left, or Android itself says it is short. */
    val pressure: Boolean get() = lowMemory || (totalMb > 0 && availableMb < totalMb / 12)

    val overheated: Boolean get() = heat == Heat.Severe || heat == Heat.Critical
}

/**
 * The decisions, with no Android in them, so they can be tested.
 *
 * They are deliberately conservative. On a Snapdragon 7 Gen 3 the four big cores are the useful
 * ones: adding the little cores makes a reply slower, and adding threads onto a hot phone makes
 * it slower still once the governor throttles. Sustainable speed beats a fast first ten seconds.
 */
object PerfPlan {

    /** Threads for a mode, given the phone's big-core count and its state. */
    fun threads(mode: PerfMode, state: DeviceState, base: Int): Int {
        val wanted = when (mode) {
            PerfMode.Performance -> base
            PerfMode.Balanced -> base - 1
            PerfMode.Saver -> 2
        }
        val cooled = if (state.overheated) wanted - 1 else wanted
        val squeezed = if (state.pressure && !state.overheated) cooled - 1 else cooled
        return squeezed.coerceIn(1, base)
    }

    /** A hot or cramped phone gets a shorter answer rather than a killed process. */
    fun tokenBudget(mode: PerfMode, state: DeviceState, asked: Int): Int {
        val cap = when {
            state.overheated -> 256
            state.pressure -> 384
            mode == PerfMode.Saver -> 768
            else -> Int.MAX_VALUE
        }
        return minOf(asked, cap).coerceAtLeast(64)
    }

    /** The context window is memory too, so it shrinks when the phone is short. */
    fun contextTokens(mode: PerfMode, state: DeviceState, asked: Int): Int {
        val cap = when {
            state.pressure -> 2048
            state.overheated -> 3072
            mode == PerfMode.Saver -> 2048
            else -> Int.MAX_VALUE
        }
        return minOf(asked, cap).coerceAtLeast(512)
    }

    /** False when the file plus a working context cannot fit in what is left of memory. */
    fun canLoad(modelBytes: Long, state: DeviceState): Boolean {
        if (state.totalMb == 0) return true
        val needMb = (modelBytes / (1024 * 1024)).toInt() + 512
        return state.availableMb > needMb
    }
}

/** Reads what the phone will say, then asks [PerfPlan] what to do about it. */
class PerformanceManager(private val ctx: Context) {

    private val activity = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val power = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager

    fun threads(mode: PerfMode, state: DeviceState): Int =
        PerfPlan.threads(mode, state, CpuTopology.threads())

    fun tokenBudget(mode: PerfMode, state: DeviceState, asked: Int): Int =
        PerfPlan.tokenBudget(mode, state, asked)

    fun contextTokens(mode: PerfMode, state: DeviceState, asked: Int): Int =
        PerfPlan.contextTokens(mode, state, asked)

    fun canLoad(modelBytes: Long, state: DeviceState): Boolean =
        PerfPlan.canLoad(modelBytes, state)

    fun read(): DeviceState {
        val available = availableMb()
        val total = totalMb()
        return DeviceState(
            heat = heat(),
            availableMb = available,
            totalMb = total,
            lowMemory = total > 0 && available < total / 8,
            processMb = processMb(),
        )
    }

    private fun heat(): Heat {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Heat.None
        val status = runCatching { power?.currentThermalStatus }.getOrNull() ?: return Heat.None
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> Heat.None
            PowerManager.THERMAL_STATUS_LIGHT -> Heat.Light
            PowerManager.THERMAL_STATUS_MODERATE -> Heat.Moderate
            PowerManager.THERMAL_STATUS_SEVERE -> Heat.Severe
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN,
            -> Heat.Critical
            else -> Heat.None
        }
    }

    private fun availableMb(): Int = runCatching {
        val info = ActivityManager.MemoryInfo()
        activity?.getMemoryInfo(info)
        (info.availMem / (1024 * 1024)).toInt()
    }.getOrDefault(0)

    private fun totalMb(): Int = runCatching {
        val info = ActivityManager.MemoryInfo()
        activity?.getMemoryInfo(info)
        (info.totalMem / (1024 * 1024)).toInt()
    }.getOrDefault(0)

    /** This app's own use - not the whole phone's, which we cannot honestly know. */
    private fun processMb(): Int = runCatching {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        info.totalPss / 1024
    }.getOrDefault(0)
}
