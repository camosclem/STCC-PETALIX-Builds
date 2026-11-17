package com.nuvio.tv.petalix

import com.nuvio.tv.ui.screens.player.PeakLimiter

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.nuvio.tv.data.local.NightMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Live-TV downward compression, adapted from the upstream PCM night-mode processor. */
internal class PetalixQuietAudioProcessor : BaseAudioProcessor() {

    private class Preset(val makeupDb: Float, val ratio: Float) {
        // Gain is back to unity at 0 dBFS: makeup + (1/ratio - 1) * (0 - threshold) = 0.
        val thresholdDb = -18f
    }

    @Volatile
    private var mode: NightMode = NightMode.OFF

    private var configuredSampleRate = 0
    private var envelopeAttack = 0f
    private var envelopeRelease = 0f
    private var gainAttack = 0f
    private var gainRelease = 0f

    private var envelope = 0f
    private var gainDb = 0f
    private var gainLinear = 1f
    private var gainStep = 0f
    private var blockPosition = 0
    private val limiter = PeakLimiter()
    private var frameScratch = FloatArray(0)

    fun setMode(mode: NightMode) {
        this.mode = mode
    }

    fun currentMode(): NightMode = mode

    fun isNightModeEnabled(): Boolean = mode != NightMode.OFF

    override fun isActive(): Boolean {
        return super.isActive() && isNightModeEnabled()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        val preset = presetFor(mode)
        val channelCount = inputAudioFormat.channelCount
        if (preset == null || channelCount <= 0) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }
        configureFor(inputAudioFormat.sampleRate)
        if (frameScratch.size != channelCount) {
            frameScratch = FloatArray(channelCount)
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val frameBytes = channelCount * if (isFloat) 4 else 2
        val frame = frameScratch

        while (inputBuffer.remaining() >= frameBytes) {
            var peak = 0f
            for (channel in 0 until channelCount) {
                var sample = if (isFloat) inputBuffer.float else inputBuffer.short / PCM16_SCALE
                if (!sample.isFinite()) sample = 0f
                frame[channel] = sample
                peak = max(peak, abs(sample))
            }

            val envelopeCoefficient = if (peak > envelope) envelopeAttack else envelopeRelease
            envelope = peak + envelopeCoefficient * (envelope - peak)

            // The gain curve is evaluated once per block and ramped linearly in between.
            if (blockPosition == 0) {
                val targetDb = liftFor(envelope, preset)
                val gainCoefficient = if (targetDb < gainDb) gainAttack else gainRelease
                gainDb = targetDb + gainCoefficient * (gainDb - targetDb)
                gainStep = (dbToLinear(gainDb) - gainLinear) / BLOCK_FRAMES
            }
            blockPosition = (blockPosition + 1) % BLOCK_FRAMES
            gainLinear += gainStep

            val gain = gainLinear * limiter.gainFor(peak * gainLinear)
            for (channel in 0 until channelCount) {
                val processed = (frame[channel] * gain).coerceIn(-1f, 1f)
                if (isFloat) {
                    outputBuffer.putFloat(processed)
                } else {
                    outputBuffer.putShort(
                        (processed * PCM16_SCALE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                    )
                }
            }
        }

        if (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer)
        }
        outputBuffer.flip()
    }

    override fun onFlush() {
        resetDynamics()
    }

    override fun onReset() {
        resetDynamics()
        configuredSampleRate = 0
    }

    private fun resetDynamics() {
        envelope = 0f
        gainDb = 0f
        gainLinear = 1f
        gainStep = 0f
        blockPosition = 0
        limiter.reset()
    }

    private fun configureFor(sampleRate: Int) {
        if (sampleRate == configuredSampleRate) return
        val rate = if (sampleRate > 0) sampleRate.toFloat() else DEFAULT_SAMPLE_RATE
        envelopeAttack = timeCoefficient(ENVELOPE_ATTACK_MS, rate)
        envelopeRelease = timeCoefficient(ENVELOPE_RELEASE_MS, rate)
        gainAttack = timeCoefficient(GAIN_ATTACK_MS, rate / BLOCK_FRAMES)
        gainRelease = timeCoefficient(GAIN_RELEASE_MS, rate / BLOCK_FRAMES)
        limiter.configure(sampleRate)
        configuredSampleRate = sampleRate
    }

    private fun timeCoefficient(timeMs: Float, rate: Float): Float {
        return exp(-1f / (timeMs * rate / 1000f))
    }

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    internal companion object {
        private const val PCM16_SCALE = 32768f
        private const val DEFAULT_SAMPLE_RATE = 48000f
        private const val LEVEL_FLOOR = 1e-6f
        private const val BLOCK_FRAMES = 16
        private const val KNEE_DB = 6f
        private const val ENVELOPE_ATTACK_MS = 1f
        private const val ENVELOPE_RELEASE_MS = 200f
        private const val GAIN_ATTACK_MS = 5f
        private const val GAIN_RELEASE_MS = 300f
        // Full lift above LIFT_FADE_START_DB, none below LIFT_FADE_END_DB (peak level).
        private const val LIFT_FADE_START_DB = -50f
        private const val LIFT_FADE_END_DB = -65f

        private val LIGHT = Preset(makeupDb = 0f, ratio = 2f)
        private val MEDIUM = Preset(makeupDb = 0f, ratio = 4f)
        private val STRONG = Preset(makeupDb = 0f, ratio = 6f)

        private fun presetFor(mode: NightMode): Preset? = when (mode) {
            NightMode.OFF -> null
            NightMode.LIGHT -> LIGHT
            NightMode.MEDIUM -> MEDIUM
            NightMode.STRONG -> STRONG
        }

        /** Static gain in dB that [mode] applies to a steady peak [level] (linear, 1 = full scale). */
        internal fun liftDb(mode: NightMode, level: Float): Float {
            return presetFor(mode)?.let { liftFor(level, it) } ?: 0f
        }

        /** Soft-knee downward compression; returns gain in dB (zero or negative) for a peak level. */
        private fun liftFor(level: Float, preset: Preset): Float {
            val levelDb = 20f * log10(max(level, LEVEL_FLOOR))
            val overDb = levelDb - preset.thresholdDb
            val slope = 1f / preset.ratio - 1f
            val halfKnee = KNEE_DB / 2f
            val reduction = when {
                overDb <= -halfKnee -> 0f
                abs(overDb) < halfKnee -> slope * (overDb + halfKnee).pow(2) / (2f * KNEE_DB)
                else -> slope * overDb
            }
            val makeupWeight = ((levelDb - LIFT_FADE_END_DB) / (LIFT_FADE_START_DB - LIFT_FADE_END_DB))
                .coerceIn(0f, 1f)
            return reduction.coerceAtMost(0f)
        }
    }
}
