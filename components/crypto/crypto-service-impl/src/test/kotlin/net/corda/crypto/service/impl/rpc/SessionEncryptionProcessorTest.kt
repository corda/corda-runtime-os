package net.corda.crypto.service.impl.rpc

import net.corda.crypto.config.impl.RetryingConfig
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.wire.ops.encryption.request.EncryptRpcCommand
import net.corda.data.crypto.wire.ops.encryption.response.CryptoEncryptionResult
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsError
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsResponse
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class SessionEncryptionProcessorTest {
    private val service = mock<CryptoService> {
        on {
            encrypt(
                any(),
                any(),
                any(),
            )
        } doReturn byteArrayOf(5, 2)
    }
    private val config = mock<RetryingConfig> {
        on { maxAttempts } doReturn 1
        on { waitBetweenMills } doReturn listOf(1)
    }
    private val request = EncryptRpcCommand(
        "category",
        "alias",
        ByteBuffer.wrap(byteArrayOf(4)),
    )
    private val processor = SessionEncryptionProcessor(
        service,
        config,
    )

    @Test
    fun `process returns the correct data`() {
        val response = processor.process(request)

        assertThat(response).isEqualTo(
            EncryptionOpsResponse(
                CryptoEncryptionResult(
                    ByteBuffer.wrap(byteArrayOf(5, 2))
                )
            )
        )
    }

    @Test
    fun `process sends the correct tenant`() {
        val tenant = argumentCaptor<String>()
        whenever(
            service.encrypt(
                tenant.capture(),
                any(),
                any(),
            )
        ).doReturn(
            byteArrayOf(),
        )

        processor.process(request)

        assertThat(tenant.firstValue)
            .isEqualTo(CryptoTenants.P2P)
    }

    @Test
    fun `process sends the correct data`() {
        val data = argumentCaptor<ByteArray>()
        whenever(
            service.encrypt(
                any(),
                data.capture(),
                any(),
            )
        ).doReturn(
            byteArrayOf(),
        )

        processor.process(request)

        assertThat(data.firstValue)
            .isEqualTo(byteArrayOf(4))
    }

    @Test
    fun `process sends the correct alias`() {
        val alias = argumentCaptor<String>()
        whenever(
            service.encrypt(
                any(),
                any(),
                alias.capture(),
            )
        ).doReturn(
            byteArrayOf(),
        )

        processor.process(request)

        assertThat(alias.firstValue)
            .isEqualTo("alias")
    }

    @Test
    fun `process return error in case service failed`() {
        whenever(
            service.encrypt(
                any(),
                any(),
                any(),
            )
        ).doThrow(
            CordaRuntimeException("test"),
        )

        val reply = processor.process(request)

        assertThat(reply.response as? EncryptionOpsError)
            .isEqualTo(
                EncryptionOpsError(
                    ExceptionEnvelope(
                        CordaRuntimeException::class.java.name,
                        "test",
                    ),
                )
            )
    }
}