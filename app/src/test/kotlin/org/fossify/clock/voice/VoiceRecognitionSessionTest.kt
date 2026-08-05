package org.fossify.clock.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecognitionSessionTest {
    @Test
    fun `duplicate final callbacks deliver one command`() {
        val engine = FakeEngine()
        val commands = mutableListOf<VoiceCommand>()
        val session = VoiceRecognitionSession(engine, 0.8f, commands::add)

        assertTrue(session.start().isSuccess)
        engine.emit(final("stop"))
        engine.emit(final("stop"))

        assertEquals(listOf(VoiceCommand.STOP), commands)
    }

    @Test
    fun `callbacks after stop are rejected`() {
        val engine = FakeEngine()
        val commands = mutableListOf<VoiceCommand>()
        val session = VoiceRecognitionSession(engine, 0.8f, commands::add)

        session.start()
        session.stop()
        engine.emit(final("snooze"))

        assertTrue(commands.isEmpty())
    }

    @Test
    fun `callback from replaced session is rejected`() {
        val engine = FakeEngine()
        val commands = mutableListOf<VoiceCommand>()
        val session = VoiceRecognitionSession(engine, 0.8f, commands::add)

        session.start()
        val oldCallback = engine.callback
        session.stop()
        session.start()
        oldCallback?.invoke(final("stop"))
        engine.emit(final("snooze"))

        assertEquals(listOf(VoiceCommand.SNOOZE), commands)
    }

    @Test
    fun `active engine failure is delivered once`() {
        val engine = FakeEngine()
        val errors = mutableListOf<Throwable>()
        val session = VoiceRecognitionSession(engine, 0.8f, {}, errors::add)

        session.start()
        val failure = IllegalStateException("decode failed")
        engine.fail(failure)
        engine.fail(IllegalStateException("duplicate"))

        assertEquals(listOf(failure), errors)
    }

    @Test
    fun `failure from replaced session is rejected`() {
        val engine = FakeEngine()
        val errors = mutableListOf<Throwable>()
        val session = VoiceRecognitionSession(engine, 0.8f, {}, errors::add)

        session.start()
        val oldErrorCallback = engine.errorCallback
        session.stop()
        session.start()
        oldErrorCallback?.invoke(IllegalStateException("stale"))

        assertTrue(errors.isEmpty())
    }

    private fun final(text: String) = RecognitionHypothesis(text, 1f, true)

    private class FakeEngine : VoiceRecognitionEngine {
        var callback: ((RecognitionHypothesis) -> Unit)? = null
        var errorCallback: ((Throwable) -> Unit)? = null

        override suspend fun prepare() = Result.success(Unit)
        override fun start(
            onHypothesis: (RecognitionHypothesis) -> Unit,
            onError: (Throwable) -> Unit,
        ): Result<Unit> {
            callback = onHypothesis
            errorCallback = onError
            return Result.success(Unit)
        }
        fun emit(hypothesis: RecognitionHypothesis) = callback?.invoke(hypothesis)
        fun fail(error: Throwable) = errorCallback?.invoke(error)
        override fun acceptPcm16(samples: ShortArray, sampleCount: Int) = Unit
        override fun stop() = Unit
        override fun close() = Unit
    }
}
