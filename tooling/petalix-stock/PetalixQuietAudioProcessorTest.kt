package com.nuvio.tv.petalix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.nuvio.tv.data.local.NightMode
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class PetalixQuietAudioProcessorTest {
    @Test fun `quiet audio stays at original level and loud peaks are reduced`() {
        assertEquals(0f, PetalixQuietAudioProcessor.liftDb(NightMode.MEDIUM, .03f), .01f)
        assertTrue(PetalixQuietAudioProcessor.liftDb(NightMode.MEDIUM, .8f) < -10f)
        assertEquals(0f, PetalixQuietAudioProcessor.liftDb(NightMode.OFF, .8f), .01f)
    }

    @Test fun `PCM output reduces sustained loud audio without changing stereo balance or timing`() {
        val processor = PetalixQuietAudioProcessor().apply {
            setMode(NightMode.MEDIUM)
            configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
            flush()
        }
        var left = 0f; var right = 0f; var frames = 0
        repeat(100) {
            val input = ByteBuffer.allocateDirect(480 * 4).order(ByteOrder.nativeOrder())
            repeat(480) { input.putShort(26214.toShort()); input.putShort((-13107).toShort()) }
            input.flip(); processor.queueInput(input)
            val output = processor.output.order(ByteOrder.nativeOrder())
            assertEquals(480 * 4, output.remaining())
            while (output.remaining() >= 4) {
                left = output.short / 32768f; right = output.short / 32768f; frames++
            }
        }
        assertEquals(48000, frames)
        assertTrue(abs(left) < .35f)
        assertEquals(-2f, left / right, .01f)
    }
}
