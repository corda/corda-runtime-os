package net.corda.flow.pipeline.handlers.waiting.crypto

import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.state.crypto.CryptoState
import net.corda.data.flow.state.waiting.SignedBytes
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigitalSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@Suppress("MaxLineLength")
class SignedBytesWaitingForHandlerTest {

    private val checkpoint = mock<FlowCheckpoint>()
    private val config = mock<SmartConfig>()
    private val cryptoFlowOpsTransformer = mock<CryptoFlowOpsTransformer>()
    private val signedBytesWaitingForHandler = SignedBytesWaitingForHandler(cryptoFlowOpsTransformer)

    @Test
    fun `When a response with no error is received then a FlowContinuation#Run is returned`() {
        val response = FlowOpsResponse()
        val signature = DigitalSignature.WithKey(mock(), byteArrayOf(1), emptyMap())
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = response
        )

        whenever(checkpoint.cryptoState).thenReturn(createCryptoState(retries = 0, response))
        whenever(cryptoFlowOpsTransformer.transform(response)).thenReturn(signature)

        val continuation = signedBytesWaitingForHandler.runOrContinue(
            inputContext,
            SignedBytes(UUID.randomUUID().toString())
        )

        assertEquals(FlowContinuation.Run(signature), continuation)
    }

    @Test
    fun `When a response with a user error is received then a FlowContinuation#Error is returned`() {
        val response = FlowOpsResponse().apply {
            exception = ExceptionEnvelope("USER_ERROR", "user error")
        }
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = response
        )

        whenever(checkpoint.cryptoState).thenReturn(createCryptoState(retries = 0, response))

        val continuation = signedBytesWaitingForHandler.runOrContinue(
            inputContext,
            SignedBytes(UUID.randomUUID().toString())
        )

        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        assertInstanceOf(CordaRuntimeException::class.java, (continuation as FlowContinuation.Error).exception)
    }

    @Test
    fun `When a response with a retry error that has not exceeded the max retry count then a FlowContinuation#Continue is returned and the count incremented`() {
        val response = FlowOpsResponse().apply {
            exception = ExceptionEnvelope("RETRY", "try again")
        }
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = response,
            config = config
        )
        val cryptoState = createCryptoState(retries = 1, response)

        whenever(checkpoint.cryptoState).thenReturn(cryptoState)
        whenever(config.getLong(FlowConfig.CRYPTO_MAX_RETRIES)).thenReturn(2)

        val continuation = signedBytesWaitingForHandler.runOrContinue(
            inputContext,
            SignedBytes(UUID.randomUUID().toString())
        )

        assertEquals(FlowContinuation.Continue, continuation)
        assertEquals(2, cryptoState.retries)
    }

    @Test
    fun `When a response with a retry error that has exceeded the max retry count is received then a FlowContinuation#Error is returned`() {
        val response = FlowOpsResponse().apply {
            exception = ExceptionEnvelope("RETRY", "try again")
        }
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = response,
            config = config
        )

        whenever(checkpoint.cryptoState).thenReturn(createCryptoState(retries = 3, response))
        whenever(config.getLong(FlowConfig.CRYPTO_MAX_RETRIES)).thenReturn(2)

        val continuation = signedBytesWaitingForHandler.runOrContinue(
            inputContext,
            SignedBytes(UUID.randomUUID().toString())
        )

        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        assertInstanceOf(CordaRuntimeException::class.java, (continuation as FlowContinuation.Error).exception)
    }

    @Test
    fun `When a response with a platform error is received then a FlowContinuation#Error is returned`() {
        val response = FlowOpsResponse().apply {
            exception = ExceptionEnvelope("PLATFORM_ERROR", "platform error")
        }
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = response
        )

        whenever(checkpoint.cryptoState).thenReturn(createCryptoState(retries = 0, response))

        assertThrows<FlowFatalException> {
            signedBytesWaitingForHandler.runOrContinue(
                inputContext,
                SignedBytes(UUID.randomUUID().toString())
            )
        }
    }

    @Test
    fun `When a response with an unknown error type is received then a FlowContinuation#Error is returned`() {
        val response = FlowOpsResponse().apply {
            exception = ExceptionEnvelope("UNKNOWN", "??")
        }
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = response
        )

        whenever(checkpoint.cryptoState).thenReturn(createCryptoState(retries = 0, response))

        assertThrows<FlowFatalException> {
            signedBytesWaitingForHandler.runOrContinue(
                inputContext,
                SignedBytes(UUID.randomUUID().toString())
            )
        }

    }

    private fun createCryptoState(retries: Int, response: FlowOpsResponse): CryptoState {
        return CryptoState.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setSendTimestamp(Instant.now())
            .setRequest(FlowOpsRequest())
            .setRetries(retries)
            .setResponse(response)
            .build()
    }
}