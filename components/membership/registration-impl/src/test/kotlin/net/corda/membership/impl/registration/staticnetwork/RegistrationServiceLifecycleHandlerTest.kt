package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.configs
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.crypto.DigestService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class RegistrationServiceLifecycleHandlerTest {
    private val componentHandle: RegistrationHandle = mock()
    private val configHandle: AutoCloseable = mock()

    private val groupPolicyProvider: GroupPolicyProvider = mock()

    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(any(), any()) } doReturn configHandle
    }

    private val publisher: Publisher = mock()

    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn publisher
    }

    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn componentHandle
    }

    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }

    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = mock()

    private val digestService: DigestService = mock()

    private val staticMemberRegistrationService = StaticMemberRegistrationService(
        groupPolicyProvider,
        publisherFactory,
        mock(),
        mock(),
        configurationReadService,
        coordinatorFactory,
        layeredPropertyMapFactory,
        digestService
    )

    private val registrationServiceLifecycleHandler = RegistrationServiceLifecycleHandler(
        staticMemberRegistrationService
    )

    @Test
    fun `Start event starts following the statuses of the required dependencies`() {
        registrationServiceLifecycleHandler.processEvent(StartEvent(), coordinator)
        assertThrows<IllegalArgumentException> { registrationServiceLifecycleHandler.publisher }

        verify(coordinator).followStatusChangesByName(
            eq(
                setOf(
                    LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                )
            )
        )
    }

    @Test
    fun `Stop event sets the publisher to null`() {
        registrationServiceLifecycleHandler.processEvent(
            ConfigChangedEvent(setOf(BOOT_CONFIG, MESSAGING_CONFIG), configs),
            coordinator
        )
        registrationServiceLifecycleHandler.processEvent(StopEvent(), coordinator)
        assertThrows<IllegalArgumentException> { registrationServiceLifecycleHandler.publisher }

        verify(componentHandle, never()).close()
        verify(configHandle, never()).close()
    }

    @Test
    fun `Component handle is created after starting and closed when stopping`() {
        registrationServiceLifecycleHandler.processEvent(StartEvent(), coordinator)
        registrationServiceLifecycleHandler.processEvent(StopEvent(), coordinator)

        verify(componentHandle).close()
    }

    @Test
    fun `Config handle is created after registration status changes to UP and closed when stopping`() {
        registrationServiceLifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator
        )
        registrationServiceLifecycleHandler.processEvent(StopEvent(), coordinator)

        verify(configHandle).close()
    }

    @Test
    fun `Registration status UP registers for config updates`() {
        registrationServiceLifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator
        )

        verify(configurationReadService).registerComponentForUpdates(
            any(), any()
        )
        verify(coordinator, never()).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `Registration status DOWN sets component status to DOWN`() {
        registrationServiceLifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator
        )

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `Registration status ERROR sets component status to DOWN`() {
        registrationServiceLifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR), coordinator
        )

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `Registration status DOWN closes config handle if status was previously UP`() {
        registrationServiceLifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator
        )

        verify(configurationReadService).registerComponentForUpdates(
            any(), any()
        )

        registrationServiceLifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator
        )

        verify(configHandle).close()
    }

    @Test
    fun `After receiving the messaging configuration the publisher is initialized`() {
        registrationServiceLifecycleHandler.processEvent(
            ConfigChangedEvent(setOf(BOOT_CONFIG, MESSAGING_CONFIG), configs),
            coordinator
        )
        assertNotNull(registrationServiceLifecycleHandler.publisher)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }
}