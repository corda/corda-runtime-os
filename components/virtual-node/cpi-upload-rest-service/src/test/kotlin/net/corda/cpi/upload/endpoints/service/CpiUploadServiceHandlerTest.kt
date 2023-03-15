package net.corda.cpi.upload.endpoints.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpi.upload.endpoints.service.impl.CpiUploadServiceHandler
import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.CpiUploadManagerFactory
import net.corda.libs.cpiupload.impl.CpiUploadManagerImpl
import net.corda.libs.cpiupload.impl.UploadStatusProcessor
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CpiUploadServiceHandlerTest {
    private lateinit var cpiUploadServiceHandler: CpiUploadServiceHandler
    private lateinit var cpiUploadManagerFactory: CpiUploadManagerFactory
    private lateinit var configReadService: ConfigurationReadService
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var subscriptionFactory: SubscriptionFactory

    private lateinit var coordinator: LifecycleCoordinator

    @BeforeEach
    fun setUp() {
        cpiUploadManagerFactory = mock()
        configReadService = mock()
        publisherFactory = mock()
        subscriptionFactory = mock()
        cpiUploadServiceHandler =
            CpiUploadServiceHandler(
                cpiUploadManagerFactory,
                configReadService,
                publisherFactory,
                subscriptionFactory
            )

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

        assertNull(cpiUploadServiceHandler.configReadServiceRegistrationHandle)
        cpiUploadServiceHandler.processEvent(StartEvent(), coordinator)
        assertNotNull(cpiUploadServiceHandler.configReadServiceRegistrationHandle)
    }

    @Test
    fun `on RegistrationStatusChangeEvent UP event registers to ConfigurationReadService for config updates`() {
        val event = RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP)
        cpiUploadServiceHandler.processEvent(event, coordinator)
        verify(configReadService).registerComponentForUpdates(
            coordinator,
            setOf(
                ConfigKeys.MESSAGING_CONFIG,
                ConfigKeys.BOOT_CONFIG,
                ConfigKeys.REST_CONFIG
            )
        )
    }

    @Test
    fun `on RegistrationStatusChangeEvent ERROR event closes resources and updates coordinator to ERROR`() {
        val event = RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR)
        val configReadServiceRegistrationHandle = mock<RegistrationHandle>()
        val publisher = mock<Publisher>()
        val cpiUploadManager = mock<CpiUploadManager>()

        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)

        cpiUploadServiceHandler.configReadServiceRegistrationHandle = configReadServiceRegistrationHandle
        cpiUploadServiceHandler.cpiUploadManager = cpiUploadManager
        cpiUploadServiceHandler.processEvent(event, coordinator)
        verify(configReadServiceRegistrationHandle).close()
        assertNull(cpiUploadServiceHandler.configReadServiceRegistrationHandle)
        assertNull(cpiUploadServiceHandler.cpiUploadManager)
        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `on ConfigChangedEvent creates new RPCSender and CpiUploadManager and updates coordinator to UP`() {
        val msgConfigMock = mock<SmartConfig>() {
            on { getInt(MessagingConfig.MAX_ALLOWED_MSG_SIZE) }.doReturn(500)
        }
        val config = mock<Map<String, SmartConfig>>()
        whenever(config[ConfigKeys.MESSAGING_CONFIG]).thenReturn(msgConfigMock)
        whenever(config[ConfigKeys.BOOT_CONFIG]).thenReturn(mock())
        whenever(config[ConfigKeys.REST_CONFIG]).thenReturn(mock())

        val publisher = mock<Publisher>()
        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)

        val processor = UploadStatusProcessor()
        val subscription: Subscription<UploadStatusKey, UploadStatus> = mock()
        whenever(cpiUploadManagerFactory.create(any(), any(), any())).thenReturn(
            CpiUploadManagerImpl(
                Schemas.VirtualNode.CPI_UPLOAD_TOPIC,
                publisher,
                subscription,
                processor,
                500
            )
        )

        assertNull(cpiUploadServiceHandler.cpiUploadManager)
        cpiUploadServiceHandler.processEvent(ConfigChangedEvent(mock(), config), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `on StopEvent closes resources and updates coordinator to DOWN`() {
        val event = StopEvent()
        val configReadServiceRegistrationHandle = mock<RegistrationHandle>()
        val ackProcessor = UploadStatusProcessor()
        val publisher: Publisher = mock()
        val subscription: Subscription<UploadStatusKey, UploadStatus> = mock()
        val cpiUploadManager = CpiUploadManagerImpl("some topic", publisher, subscription, ackProcessor, 500)

        cpiUploadServiceHandler.configReadServiceRegistrationHandle = configReadServiceRegistrationHandle
        cpiUploadServiceHandler.cpiUploadManager = cpiUploadManager
        cpiUploadServiceHandler.processEvent(event, coordinator)
        verify(configReadServiceRegistrationHandle).close()
        verify(publisher).close()
        verify(subscription).close()
        assertNull(cpiUploadServiceHandler.configReadServiceRegistrationHandle)
        assertNull(cpiUploadServiceHandler.cpiUploadManager)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }


}
