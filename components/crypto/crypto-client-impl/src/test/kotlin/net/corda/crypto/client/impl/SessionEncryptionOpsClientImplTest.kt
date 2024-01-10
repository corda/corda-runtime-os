package net.corda.crypto.client.impl

import net.corda.crypto.core.CryptoConsts.Categories.ENCRYPTION_SECRET
import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.wire.ops.encryption.request.DecryptRpcCommand
import net.corda.data.crypto.wire.ops.encryption.request.EncryptRpcCommand
import net.corda.data.crypto.wire.ops.encryption.response.CryptoDecryptionResult
import net.corda.data.crypto.wire.ops.encryption.response.CryptoEncryptionResult
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsError
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsResponse
import net.corda.data.crypto.wire.ops.encryption.response.DecryptionOpsResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.messaging.api.publisher.HttpRpcClient
import net.corda.schema.configuration.BootConfig
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.URI
import java.nio.ByteBuffer

class SessionEncryptionOpsClientImplTest {
    private val encryptionResponse = mock<EncryptionOpsResponse>()
    private val decryptionResponse = mock<DecryptionOpsResponse>()
    private val encryptionRequest = argumentCaptor<EncryptRpcCommand>()
    private val decryptionRequest = argumentCaptor<DecryptRpcCommand>()
    private val url = argumentCaptor<URI>()
    private val httpRpcClient = mock<HttpRpcClient> {
        on { send(url.capture(), encryptionRequest.capture(), eq(EncryptionOpsResponse::class.java)) } doReturn encryptionResponse
        on { send(url.capture(), decryptionRequest.capture(), eq(DecryptionOpsResponse::class.java)) } doReturn decryptionResponse
    }
    private val platformInfoProvider = mock<PlatformInfoProvider> {
        on { localWorkerSoftwareShortVersion } doReturn "5.x"
    }
    private val messagingConfig = mock<SmartConfig> {
        on { getString(BootConfig.CRYPTO_WORKER_REST_ENDPOINT) } doReturn "localhost:1231"
    }
    private val client = SessionEncryptionImpl(
        httpRpcClient,
        platformInfoProvider,
        messagingConfig,
    )

    @Nested
    inner class EncryptSessionDataTest {
        private val response = encryptionResponse

        @Test
        fun `it returns the correct data`() {
            val data = "data".toByteArray()
            val results = CryptoEncryptionResult(ByteBuffer.wrap(data))
            whenever(response.response).doReturn(results)

            val encrypted = client.encryptSessionData(byteArrayOf(1, 2), "alias")

            assertThat(encrypted).isEqualTo(data)
        }

        @Test
        fun `it sends the correct data`() {
            val data = byteArrayOf(1, 2)
            val results = CryptoEncryptionResult(ByteBuffer.wrap(byteArrayOf(3)))
            whenever(response.response).doReturn(results)

            client.encryptSessionData(data, "alias")

            assertThat(encryptionRequest.firstValue).isEqualTo(
                EncryptRpcCommand(
                    ENCRYPTION_SECRET,
                    "alias",
                    ByteBuffer.wrap(data),
                )
            )
        }

        @Test
        fun `it sends the correct URL`() {
            val data = byteArrayOf(1, 2)
            val results = CryptoEncryptionResult(ByteBuffer.wrap(byteArrayOf(3)))
            whenever(response.response).doReturn(results)

            client.encryptSessionData(data, "alias")

            assertThat(url.firstValue).isEqualTo(
                URI.create("http://localhost:1231/api/5.x/crypto-api/session/encrypt")
            )
        }

        @Test
        fun `it throws an exception if response is empty`() {
            whenever(response.response).doReturn(null)

            assertThrows<CordaRuntimeException> {
                client.encryptSessionData(byteArrayOf(1), "alias")
            }
        }

        @Test
        fun `it throws an exception if response is error`() {
            val error = ExceptionEnvelope("type", "messate")
            val results = EncryptionOpsError(error)
            whenever(response.response).doReturn(results)

            assertThrows<CordaRuntimeException> {
                client.encryptSessionData(byteArrayOf(1), "alias")
            }
        }

        @Test
        fun `it throws an exception if response is null`() {
            whenever(response.response).doReturn(12)

            assertThrows<CordaRuntimeException> {
                client.encryptSessionData(byteArrayOf(1), "alias")
            }
        }
    }
    @Nested
    inner class DecryptSessionDataTest {
        private val response = decryptionResponse
        @Test
        fun `it returns the correct data`() {
            val data = "data".toByteArray()
            val results = CryptoDecryptionResult(ByteBuffer.wrap(data))
            whenever(response.response).doReturn(results)

            val encrypted = client.decryptSessionData(byteArrayOf(1, 2), "alias")

            assertThat(encrypted).isEqualTo(data)
        }

        @Test
        fun `it sends the correct data`() {
            val data = byteArrayOf(1, 2)
            val results = CryptoDecryptionResult(ByteBuffer.wrap(byteArrayOf(3)))
            whenever(response.response).doReturn(results)

            client.decryptSessionData(data, "alias")

            assertThat(decryptionRequest.firstValue).isEqualTo(
                DecryptRpcCommand(
                    ENCRYPTION_SECRET,
                    "alias",
                    ByteBuffer.wrap(data),
                )
            )
        }

        @Test
        fun `it sends the correct URL`() {
            val data = byteArrayOf(1, 2)
            val results = CryptoDecryptionResult(ByteBuffer.wrap(byteArrayOf(3)))
            whenever(response.response).doReturn(results)

            client.decryptSessionData(data, "alias")

            assertThat(url.firstValue).isEqualTo(
                URI.create("http://localhost:1231/api/5.x/crypto-api/session/decrypt")
            )
        }

        @Test
        fun `it throws an exception if response is empty`() {
            whenever(response.response).doReturn(null)

            assertThrows<CordaRuntimeException> {
                client.decryptSessionData(byteArrayOf(1), "alias")
            }
        }

        @Test
        fun `it throws an exception if response is error`() {
            val error = ExceptionEnvelope("type", "messate")
            val results = EncryptionOpsError(error)
            whenever(response.response).doReturn(results)

            assertThrows<CordaRuntimeException> {
                client.decryptSessionData(byteArrayOf(1), "alias")
            }
        }

        @Test
        fun `it throws an exception if response is null`() {
            whenever(response.response).doReturn(12)

            assertThrows<CordaRuntimeException> {
                client.decryptSessionData(byteArrayOf(1), "alias")
            }
        }
    }
}
