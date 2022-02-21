package net.corda.cpi.upload.endpoints.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.CpiUploadManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CpiUploadRPCOpsServiceHandlerTest {
    private lateinit var cpiUploadRPCOpsServiceHandler: CpiUploadRPCOpsServiceHandler
    private lateinit var cpiUploadManagerFactory: CpiUploadManagerFactory
    private lateinit var configReadService: ConfigurationReadService
    private lateinit var publisherFactory: PublisherFactory

    private lateinit var coordinator: LifecycleCoordinator

    @BeforeEach
    fun setUp() {
        cpiUploadManagerFactory = mock()
        configReadService = mock()
        publisherFactory = mock()
        cpiUploadRPCOpsServiceHandler =
            CpiUploadRPCOpsServiceHandler(cpiUploadManagerFactory, configReadService, publisherFactory)

        coordinator = mock()
    }

    @Test
    fun `on Start event follows ConfigurationReadService for changes`() {
        val registrationHandle = mock<RegistrationHandle>()
        whenever(
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                )
            )
        ).thenReturn(registrationHandle)

        assertNull(cpiUploadRPCOpsServiceHandler.configReadServiceRegistrationHandle)
        cpiUploadRPCOpsServiceHandler.processEvent(StartEvent(), coordinator)
        assertNotNull(cpiUploadRPCOpsServiceHandler.configReadServiceRegistrationHandle)
    }

    @Test
    fun `on RegistrationStatusChangeEvent UP event registers to ConfigurationReadService for RPC config updates`() {
        val event = RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP)
        cpiUploadRPCOpsServiceHandler.processEvent(event, coordinator)
        verify(configReadService).registerComponentForUpdates(
            coordinator,
            setOf(
//                ConfigKeys.MESSAGING_CONFIG,
                ConfigKeys.BOOT_CONFIG,
                ConfigKeys.RPC_CONFIG
            )
        )
    }

    @Test
    fun `on RegistrationStatusChangeEvent ERROR event closes resources and updates coordinator to ERROR`() {
        val event = RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR)
        val configReadServiceRegistrationHandle = mock<RegistrationHandle>()
        val rpcSender = mock<RPCSender<Chunk, ChunkAck>>()
        val cpiUploadManager = mock<CpiUploadManager>()

        cpiUploadRPCOpsServiceHandler.configReadServiceRegistrationHandle = configReadServiceRegistrationHandle
        cpiUploadRPCOpsServiceHandler.rpcSender = rpcSender
        cpiUploadRPCOpsServiceHandler.cpiUploadManager = cpiUploadManager
        cpiUploadRPCOpsServiceHandler.processEvent(event, coordinator)
        verify(configReadServiceRegistrationHandle).close()
        verify(rpcSender).close()
        assertNull(cpiUploadRPCOpsServiceHandler.configReadServiceRegistrationHandle)
        assertNull(cpiUploadRPCOpsServiceHandler.rpcSender)
        assertNull(cpiUploadRPCOpsServiceHandler.cpiUploadManager)
        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `on ConfigChangedEvent creates new RPCSender and CpiUploadManager and updates coordinator to UP`() {
        val config = mock<Map<String, SmartConfig>>()
        // uncomment when we add ConfigKeys.MESSAGING_CONFIG back into the code.
//        whenever(config[ConfigKeys.MESSAGING_CONFIG]).thenReturn(mock())
        whenever(config[ConfigKeys.BOOT_CONFIG]).thenReturn(mock())
        whenever(config[ConfigKeys.RPC_CONFIG]).thenReturn(mock())
        // uncomment when we add ConfigKeys.MESSAGING_CONFIG back into the code.
//        whenever(config.toMessagingConfig()).thenReturn(mock())

        val rpcSender = mock<RPCSender<Any, Any>>()
        whenever(publisherFactory.createRPCSender<Any, Any>(any(), any())).thenReturn(rpcSender)
        val cpiUploadManager = mock<CpiUploadManager>()
        whenever(cpiUploadManagerFactory.create(any(), any())).thenReturn(cpiUploadManager)

        assertNull(cpiUploadRPCOpsServiceHandler.rpcSender)
        assertNull(cpiUploadRPCOpsServiceHandler.cpiUploadManager)
        cpiUploadRPCOpsServiceHandler.processEvent(ConfigChangedEvent(mock(), config), coordinator)
        assertNotNull(cpiUploadRPCOpsServiceHandler.rpcSender)
        assertNotNull(cpiUploadRPCOpsServiceHandler.cpiUploadManager)
        verify(rpcSender).start()
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `on StopEvent closes resources and updates coordinator to DOWN`() {
        val event = StopEvent()
        val configReadServiceRegistrationHandle = mock<RegistrationHandle>()
        val rpcSender = mock<RPCSender<Chunk, ChunkAck>>()
        val cpiUploadManager = mock<CpiUploadManager>()

        cpiUploadRPCOpsServiceHandler.configReadServiceRegistrationHandle = configReadServiceRegistrationHandle
        cpiUploadRPCOpsServiceHandler.rpcSender = rpcSender
        cpiUploadRPCOpsServiceHandler.cpiUploadManager = cpiUploadManager
        cpiUploadRPCOpsServiceHandler.processEvent(event, coordinator)
        verify(configReadServiceRegistrationHandle).close()
        verify(rpcSender).close()
        assertNull(cpiUploadRPCOpsServiceHandler.configReadServiceRegistrationHandle)
        assertNull(cpiUploadRPCOpsServiceHandler.rpcSender)
        assertNull(cpiUploadRPCOpsServiceHandler.cpiUploadManager)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

}
