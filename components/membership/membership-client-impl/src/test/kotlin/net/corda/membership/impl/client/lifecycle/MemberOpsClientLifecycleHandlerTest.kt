package net.corda.membership.impl.client.lifecycle

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class MemberOpsClientLifecycleHandlerTest {
    private val componentHandle: RegistrationHandle = mock()
    private val configHandle: AutoCloseable = mock()

    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn componentHandle
    }

    private val rpcSender: RPCSender<MembershipRpcRequest, MembershipRpcResponse> = mock()

    private val publisherFactory: PublisherFactory = mock {
        on { createRPCSender(any<RPCConfig<MembershipRpcRequest, MembershipRpcResponse>>(), any()) } doReturn rpcSender
    }

    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(any(), any()) } doReturn configHandle
    }

    private val mockActivate: (RPCSender<MembershipRpcRequest, MembershipRpcResponse>, String) -> Unit = mock()
    private val mockDeactivate: (String) -> Unit = mock()

    private val lifecycleHandler = MemberOpsClientLifecycleHandler(
        publisherFactory,
        configurationReadService,
        mockActivate,
        mockDeactivate
    )

    private val bootConfig: SmartConfig = mock()
    private val messagingConfig: SmartConfig = mock {
        on(it.withFallback(any())).thenReturn(mock())
    }

    private val configs = mapOf(
        ConfigKeys.BOOT_CONFIG to bootConfig,
        ConfigKeys.MESSAGING_CONFIG to messagingConfig
    )

    @Test
    fun `start event starts following the statuses of the required dependencies`() {
        lifecycleHandler.processEvent(StartEvent(), coordinator)

        verify(coordinator).followStatusChangesByName(
            eq(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
        )
    }

    @Test
    fun `start event closes dependency handle if it exists`() {
        lifecycleHandler.processEvent(StartEvent(), coordinator)
        lifecycleHandler.processEvent(StartEvent(), coordinator)

        verify(componentHandle).close()
    }

    @Test
    fun `stop event calls the deactivate function`() {
        lifecycleHandler.processEvent(StopEvent(), coordinator)

        verify(componentHandle, never()).close()
        verify(configHandle, never()).close()
        verify(mockDeactivate).invoke(any())
    }

    @Test
    fun `component handle is created after starting and closed when stopping`() {
        lifecycleHandler.processEvent(StartEvent(), coordinator)
        lifecycleHandler.processEvent(StopEvent(), coordinator)

        verify(componentHandle).close()
    }

    @Test
    fun `config handle is created after registration status changes to UP and closed when stopping`() {
        lifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator
        )
        lifecycleHandler.processEvent(StopEvent(), coordinator)

        verify(configHandle).close()
    }

    @Test
    fun `registration status UP registers for config updates`() {
        lifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator
        )

        verify(configurationReadService).registerComponentForUpdates(
            any(), any()
        )
        verify(coordinator, never()).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `registration status DOWN calls the deactivate function`() {
        lifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator
        )

        verify(mockDeactivate).invoke(any())
    }

    @Test
    fun `registration status ERROR calls the deactivate function`() {
        lifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR), coordinator
        )

        verify(mockDeactivate).invoke(any())
    }

    @Test
    fun `registration status DOWN closes config handle if status was previously UP`() {
        lifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator
        )

        verify(configurationReadService).registerComponentForUpdates(
            any(), any()
        )

        lifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator
        )

        verify(configHandle).close()
    }

    @Test
    fun `registration status UP closes config handle if status was previously UP`() {
        lifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator
        )

        verify(configurationReadService).registerComponentForUpdates(
            any(), any()
        )

        lifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator
        )

        verify(configHandle).close()
    }

    @Test
    fun `after receiving the messaging configuration the rpc sender is initialized`() {
        lifecycleHandler.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG), configs),
            coordinator
        )
        verify(publisherFactory).createRPCSender(any<RPCConfig<MembershipRpcRequest, MembershipRpcResponse>>(), any())
        verify(rpcSender).start()
        verify(mockActivate).invoke(eq(rpcSender), any())
    }
}