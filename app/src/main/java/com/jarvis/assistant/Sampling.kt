package com.jarvis.assistant

import java.util.Locale

/**
 * How the on-device model picks its next word, per mode.
 *
 * A 1B model gets visibly worse when it is left at Gemma's lively default for conversation:
 * it drifts, repeats itself and pads. MediaPipe exposes no repetition penalty to damp that,
 * so there the temperature is the only lever - and it is a user setting for Chat mode, so it
 * can be tuned on the phone rather than argued about here. llama.cpp does have the penalty, so
 * the `.gguf` engine gets one too, at the setting chat apps generally ship with.
 */
data class Sampling(
    val temperature: Float,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    /** llama.cpp only. Above 1.0 the model is pushed away from what it just said. */
    val repeatPenalty: Float = DefaultRepeatPenalty,
    /** llama.cpp only. Cuts the tail of unlikely tokens top-p left behind. */
    val minP: Float = DefaultMinP,
) {
    companion object {

        const val MinTemperature = 0.5f
        const val MaxTemperature = 1.0f

        /** Steady enough to stay on the subject, warm enough not to be flat. */
        const val DefaultTemperature = 0.7f

        const val DefaultRepeatPenalty = 1.1f
        const val DefaultMinP = 0.05f

        /** How far back the repetition penalty looks. */
        const val RepeatLastN = 64

        /**
         * The most the session may ask for. The engine's own cap is 40, so a session that
         * asked for more than this would be silently trimmed.
         */
        const val MaxTopK = 64

        /** A tool call has to come back as valid JSON, so Control mode stays steadier. */
        val Command = Sampling(0.6f)

        fun chat(temperature: Float = DefaultTemperature): Sampling =
            Sampling(clampTemperature(temperature))

        fun clampTemperature(value: Float): Float =
            if (value.isNaN()) DefaultTemperature else value.coerceIn(MinTemperature, MaxTemperature)

        fun temperatureLabel(value: Float): String =
            String.format(Locale.US, "%.1f", clampTemperature(value))

        /** What the chosen temperature means, in one word. */
        fun temperatureHint(value: Float): String = when {
            value <= 0.6f -> "Careful"
            value >= 0.9f -> "Lively"
            else -> "Balanced"
        }
    }
}
