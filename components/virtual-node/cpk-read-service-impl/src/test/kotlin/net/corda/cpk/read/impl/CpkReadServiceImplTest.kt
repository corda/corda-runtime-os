package net.corda.cpk.read.impl

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.read.impl.CpkReadServiceImpl.Companion.CPK_CACHE_DIR
import net.corda.cpk.read.impl.CpkReadServiceImpl.Companion.CPK_PARTS_DIR
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.PathProvider
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CpkReadServiceImplTest {
    lateinit var cpkReadService: CpkReadServiceImpl
    lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    lateinit var configReadService: ConfigurationReadService
    lateinit var subscriptionFactory: SubscriptionFactory

    lateinit var coordinator: LifecycleCoordinator

    @BeforeEach
    fun setUp() {
        coordinatorFactory = mock()
        configReadService = mock()
        subscriptionFactory = mock()
        cpkReadService = CpkReadServiceImpl(coordinatorFactory, configReadService, subscriptionFactory)

        coordinator = mock()
    }

    @Test
    fun `on StartEvent follows configuration read service for updates`() {
        val registration = mock<RegistrationHandle>()
        whenever(coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())))
            .thenReturn(registration)

        cpkReadService.processEvent(StartEvent(), coordinator)
        assertNotNull(cpkReadService.configReadServiceRegistration)
    }

    @Test
    fun `on onRegistrationStatusChangeEvent registers to configuration read service for updates`() {
        whenever(configReadService.registerComponentForUpdates(any(), any())).thenReturn(mock())

        cpkReadService.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        assertNotNull(cpkReadService.configSubscription)
    }

    @Test
    fun `on onConfigChangedEvent fully sets the component`() {
        val fs = Jimfs.newFileSystem(Configuration.unix())
        val workspacePathProvider = mock<PathProvider>()
        whenever(workspacePathProvider.getOrCreate(any(), anyVararg())).thenReturn(fs.getPath("/workspace/$CPK_CACHE_DIR"))
        val tempPathProvider = mock<PathProvider>()
        whenever(tempPathProvider.getOrCreate(any(), any())).thenReturn(fs.getPath("/tmp/$CPK_PARTS_DIR"))
        whenever(subscriptionFactory.createCompactedSubscription<Any, Any>(any(), any(), any())).thenReturn(mock())

        val cpkReadService =
            CpkReadServiceImpl(coordinatorFactory, configReadService, subscriptionFactory, workspacePathProvider, tempPathProvider)

        val keys = mock<Set<String>>()
        whenever(keys.contains(ConfigKeys.BOOT_CONFIG)).thenReturn(true)
        val config = mock<Map<String, SmartConfig>>()
        whenever(config[ConfigKeys.BOOT_CONFIG]).thenReturn(mock())
        val messagingConfig = mock<SmartConfig>()
        whenever(config[ConfigKeys.MESSAGING_CONFIG]).thenReturn(messagingConfig)
        whenever(messagingConfig.withFallback(any())).thenReturn(messagingConfig)

        cpkReadService.processEvent(ConfigChangedEvent(keys, config), coordinator)

        assertNotNull(cpkReadService.cpkChunksKafkaReaderSubscription)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }
}