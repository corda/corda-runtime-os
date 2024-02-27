package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.crypto.wire.ops.encryption.request.DecryptRpcCommand
import net.corda.data.crypto.wire.ops.encryption.request.EncryptRpcCommand
import net.corda.data.crypto.wire.ops.encryption.response.CryptoDecryptionResult
import net.corda.data.crypto.wire.ops.encryption.response.CryptoEncryptionResult
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsResponse
import net.corda.data.crypto.wire.ops.encryption.response.DecryptionOpsResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.publisher.HttpRpcClient
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.ConfigKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class SessionEncryptionOpsClientComponentTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val registrationHandle = mock<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator>() {
        on { status } doReturn LifecycleStatus.UP
        on { start() } doAnswer {
            handler.firstValue.processEvent(StartEvent(), mock)
        }
        on { followStatusChangesByName(any()) } doReturn registrationHandle
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val encryptionResponse = mock<EncryptionOpsResponse>()
    private val decryptionResponse = mock<DecryptionOpsResponse>()
    private val httpRpcClient = mock<HttpRpcClient> {
        on { send(any(), any<EncryptRpcCommand>(), eq(EncryptionOpsResponse::class.java)) } doReturn encryptionResponse
        on { send(any(), any<DecryptRpcCommand>(), eq(DecryptionOpsResponse::class.java)) } doReturn decryptionResponse
    }
    private val publisherFactory = mock<PublisherFactory> {
        on { createHttpRpcClient() } doReturn httpRpcClient
    }
    private val messagingConfig = mock<SmartConfig> {
        on { getString(BootConfig.CRYPTO_WORKER_REST_ENDPOINT) } doReturn "localhost:1231"
    }
    private val configurationReadService = mock<ConfigurationReadService> {
        on { registerComponentForUpdates(any(), any()) } doAnswer {
            val resource = mock<Resource>()
            handler.firstValue.processEvent(
                ConfigChangedEvent(
                    setOf(ConfigKeys.BOOT_CONFIG),
                    mapOf(ConfigKeys.BOOT_CONFIG to messagingConfig)
                ),
                coordinator,
            )
            resource
        }
    }
    private val platformInfoProvider = mock<PlatformInfoProvider>()
    private val component = SessionEncryptionOpsClientImpl(
        coordinatorFactory,
        publisherFactory,
        configurationReadService,
        platformInfoProvider,
    )

    private fun start() {
        component.start()
        handler.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle,
                LifecycleStatus.UP,
            ),
            coordinator,
        )
    }

    @Test
    fun `encryptSessionData send data to the client`() {
        start()
        val data = "data".toByteArray()
        val results = CryptoEncryptionResult(ByteBuffer.wrap(data))
        whenever(encryptionResponse.response).doReturn(results)

        val encrypted = component.encryptSessionData("hello".toByteArray())

        assertThat(encrypted).isEqualTo(data)
    }

    @Test
    fun `decryptSessionData send data to the client`() {
        start()
        val data = "data".toByteArray()
        val results = CryptoDecryptionResult(ByteBuffer.wrap(data))
        whenever(decryptionResponse.response).doReturn(results)

        val encrypted = component.decryptSessionData("hello".toByteArray())

        assertThat(encrypted).isEqualTo(data)
    }
}
