package net.corda.cpk.write.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.readwrite.CpkServiceConfigKeys
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.*
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CpkWriteServiceImplTest {
    private lateinit var cpkWriteServiceImpl: CpkWriteServiceImpl
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configReadService: ConfigurationReadService

    private lateinit var coordinator: LifecycleCoordinator

    @BeforeEach
    fun setUp() {
        coordinatorFactory = mock()
        configReadService = mock()
        cpkWriteServiceImpl = CpkWriteServiceImpl(coordinatorFactory, configReadService)

        coordinator = mock()
    }

    @Test
    fun `on StartEvent follows configuration read service for updates`() {
        val registration = mock<RegistrationHandle>()
        whenever(coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())))
            .thenReturn(registration)

        cpkWriteServiceImpl.processEvent(StartEvent(), coordinator)
        assertNotNull(cpkWriteServiceImpl.configReadServiceRegistration)
    }

    @Test
    fun `on onRegistrationStatusChangeEvent registers to configuration read service for updates`() {
        whenever(configReadService.registerComponentForUpdates(any(), any())).thenReturn(cpkWriteServiceImpl)

        cpkWriteServiceImpl.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        assertNotNull(cpkWriteServiceImpl.configSubscription)
    }

    @Test
    fun `on onConfigChangedEvent fully sets the component`() {
        val keys = mock<Set<String>>()
        whenever(keys.contains(ConfigKeys.BOOT_CONFIG)).thenReturn(true)
        val bootConfig = mock<SmartConfig>()
        whenever(bootConfig.hasPath(CpkServiceConfigKeys.CPK_CACHE_DIR)).thenReturn(true)
        whenever(bootConfig.getString(CpkServiceConfigKeys.CPK_CACHE_DIR)).thenReturn("")
        val config = mock<Map<String, SmartConfig>>()
        whenever(config[ConfigKeys.BOOT_CONFIG]).thenReturn(bootConfig)

        cpkWriteServiceImpl.processEvent(ConfigChangedEvent(keys, config), coordinator)
        assertNotNull(cpkWriteServiceImpl.writer)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }
}