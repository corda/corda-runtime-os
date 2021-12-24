package net.corda.membership.impl.read.lifecycle

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReaderComponent
import net.corda.crypto.service.CryptoFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigObject
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.config.MembershipConfig
import net.corda.membership.config.MembershipConfigConstants.CONFIG_KEY
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.impl.read.component.MembershipGroupReadServiceImpl
import net.corda.membership.impl.read.lifecycle.MembershipGroupReadLifecycleHandler.Impl.MembershipGroupConfigurationHandler
import net.corda.membership.impl.read.subscription.MembershipGroupReadSubscriptions
import net.corda.membership.lifecycle.MembershipConfigReceived
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MembershipGroupReadLifecycleHandlerTest {

    lateinit var handler: MembershipGroupReadLifecycleHandler

    val componentRegistrationHandle: RegistrationHandle = mock()
    val configRegistrationHandle: AutoCloseable = mock()

    val virtualNodeInfoReader: VirtualNodeInfoReaderComponent = mock()
    val cpiInfoReader: CpiInfoReaderComponent = mock()
    val configurationReadService: ConfigurationReadService = mock<ConfigurationReadService>().apply {
        doReturn(configRegistrationHandle).whenever(this).registerForUpdates(any())
    }
    val readServiceCoordinator: LifecycleCoordinator = mock()
    val coordinatorFactory: LifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
        doReturn(readServiceCoordinator).whenever(this).createCoordinator(any(), any())
    }
    private val membershipGroupReadService: MembershipGroupReadServiceImpl = MembershipGroupReadServiceImpl(
        virtualNodeInfoReader, cpiInfoReader, configurationReadService, mock(), coordinatorFactory, mock()
    )
    private val membershipGroupReadSubscriptions: MembershipGroupReadSubscriptions = mock()

    private val membershipGroupReadCache: MembershipGroupReadCache = mock()

    val coordinator: LifecycleCoordinator = mock<LifecycleCoordinator>().apply {
        doReturn(componentRegistrationHandle).whenever(this).followStatusChangesByName(any())
    }

    @BeforeEach
    fun setUp() {
        handler = MembershipGroupReadLifecycleHandler.Impl(
            membershipGroupReadService,
            membershipGroupReadSubscriptions,
            membershipGroupReadCache
        )
    }

    @Test
    fun `Start event`() {
        handler.processEvent(StartEvent(), coordinator)

        verify(configurationReadService).start()
        verify(cpiInfoReader).start()
        verify(virtualNodeInfoReader).start()
        verify(membershipGroupReadCache).start()

        verify(coordinator).followStatusChangesByName(
            eq(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<CpiInfoReaderComponent>(),
                    LifecycleCoordinatorName.forComponent<VirtualNodeInfoReaderComponent>(),
                )
            )
        )
    }

    @Test
    fun `Stop event`() {
        handler.processEvent(StopEvent(), coordinator)

        verify(configurationReadService).stop()
        verify(cpiInfoReader).stop()
        verify(virtualNodeInfoReader).stop()
        verify(membershipGroupReadSubscriptions).stop()
        verify(membershipGroupReadCache).stop()

        //these handles are only set if other lifecycle events happen first. In this case they are null when stopping.
        verify(componentRegistrationHandle, never()).close()
        verify(configRegistrationHandle, never()).close()
    }

    @Test
    fun `Component registration handle is created after starting and closed when stopping`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(StopEvent(), coordinator)

        verify(componentRegistrationHandle).close()
    }

    @Test
    fun `Config handle is created after registration status changes to UP and closed when stopping`() {
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        handler.processEvent(StopEvent(), coordinator)

        verify(configRegistrationHandle).close()
    }

    @Test
    fun `Registration status UP registers for config updates and sets component status to UP`() {
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        verify(configurationReadService).registerForUpdates(
            any<MembershipGroupConfigurationHandler>()
        )
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `Registration status DOWN sets component status to DOWN`() {
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `Registration status ERROR sets component status to DOWN`() {
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR), coordinator)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `Registration status DOWN closes config handle if status was previously UP`() {
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        verify(configRegistrationHandle).close()
    }

    private fun getConfigHandler(): MembershipGroupConfigurationHandler {
        // Get config handler after registration status changes to UP
        lateinit var configHandler: MembershipGroupConfigurationHandler
        doAnswer {
            configHandler =
                it.arguments[0] as MembershipGroupConfigurationHandler
            mock<AutoCloseable>()
        }.whenever(configurationReadService).registerForUpdates(any())

        // Change registration status to UP to catch handler
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        return configHandler
    }

    @Test
    fun `config handler does nothing if membership config has not changed`() {
        val configHandler = getConfigHandler()

        configHandler.onNewConfiguration(emptySet(), emptyMap())
        verify(coordinator, never()).postEvent(any<MembershipConfigReceived>())
    }

    @Test
    fun `config handler throws exception if membership config has changed but config is null`() {
        val configHandler = getConfigHandler()

        assertThrows<IllegalStateException> {
            configHandler.onNewConfiguration(setOf(CONFIG_KEY), emptyMap())
        }
    }

    @Test
    fun `config handler throws exception if membership config has changed but config is empty`() {
        val configHandler = getConfigHandler()
        val config: SmartConfig = mock<SmartConfig>().apply { doReturn(true).whenever(this).isEmpty }

        assertThrows<IllegalStateException> {
            configHandler.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config))
        }
    }

    @Test
    fun `config handler posts config received event when config is found`() {
        val configHandler = getConfigHandler()
        val configObject = mock<SmartConfigObject>().apply {
            doReturn(emptyMap<String, Any>()).whenever(this).unwrapped()
        }
        val config = mock<SmartConfig>().apply {
            doReturn(configObject).whenever(this).root()
        }

        configHandler.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config))

        verify(coordinator).postEvent(any<MembershipConfigReceived>())
    }

    @Test
    fun `Config received event while component is running`() {
        doReturn(true).whenever(readServiceCoordinator).isRunning
        val config: MembershipConfig = mock()
        handler.processEvent(MembershipConfigReceived(config), coordinator)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
        verify(membershipGroupReadCache).stop()
        verify(membershipGroupReadSubscriptions).stop()
        verify(membershipGroupReadCache).start()
        verify(membershipGroupReadSubscriptions, never()).start()
        verify(membershipGroupReadSubscriptions).start(any())

        membershipGroupReadCache.start()
        membershipGroupReadSubscriptions.start(eq(config))
    }

    @Test
    fun `Config received event while component is not running`() {
        doReturn(false).whenever(readServiceCoordinator).isRunning
        val config: MembershipConfig = mock()
        handler.processEvent(MembershipConfigReceived(config), coordinator)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
        verify(membershipGroupReadCache, never()).stop()
        verify(membershipGroupReadSubscriptions, never()).stop()
        verify(membershipGroupReadCache).start()
        verify(membershipGroupReadSubscriptions, never()).start()
        verify(membershipGroupReadSubscriptions).start(any())

        membershipGroupReadCache.start()
        membershipGroupReadSubscriptions.start(eq(config))
    }
}