package net.corda.components.rpc.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.security.read.RPCSecurityManagerFactory
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.httprpc.ssl.SslCertReadService
import net.corda.httprpc.ssl.SslCertReadServiceFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.service.PermissionServiceComponent
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class HttpRpcGatewayEventHandlerTest {

    private val permissionServiceComponent = mock<PermissionServiceComponent>()

    private val permissionServiceRegistration = mock<RegistrationHandle>()
    private val configRegistration = mock<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val sub = mock<AutoCloseable>()
    private val server = mock<HttpRpcServer>()
    private val securityManager = mock<RPCSecurityManager>()
    private val sslCertReadService = mock<SslCertReadService>()

    private val configurationReadService = mock<ConfigurationReadService>()
    private val httpRpcServerFactory = mock<HttpRpcServerFactory>()
    private val rpcSecurityManagerFactory = mock<RPCSecurityManagerFactory>()
    private val sslCertReadServiceFactory = mock<SslCertReadServiceFactory>()

    private interface MockEndpoint : PluggableRPCOps<MockEndpoint>, Lifecycle
    private val endpoint = mock<MockEndpoint>()
    private val rpcOps: List<PluggableRPCOps<out RpcOps>> = listOf(endpoint)

    private val handler = HttpRpcGatewayEventHandler(
        permissionServiceComponent,
        configurationReadService,
        httpRpcServerFactory,
        rpcSecurityManagerFactory,
        sslCertReadServiceFactory,
        rpcOps
    )

    @BeforeEach
    fun setUp() {
        whenever(coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionServiceComponent>()
            )
        )).thenReturn(permissionServiceRegistration)
        whenever(coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
            )
        )).thenReturn(configRegistration)
    }

    @Test
    fun `processing a start event follows permission service and configuration service and starts permission service`() {
        handler.processEvent(StartEvent(), coordinator)

        verify(coordinator).followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionServiceComponent>()
            )
        )
        verify(coordinator).followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
            )
        )
        verify(permissionServiceComponent).start()
    }

    @Test
    fun `processing an UP status change for configuration registers for config updates`() {
        handler.configServiceRegistration = configRegistration

        handler.processEvent(RegistrationStatusChangeEvent(configRegistration, LifecycleStatus.UP), coordinator)

        verify(configurationReadService).registerForUpdates(any())
    }

    @Test
    fun `processing a DOWN status change for configuration closes the subscription`() {
        handler.sub = sub
        handler.configServiceRegistration = configRegistration

        handler.processEvent(RegistrationStatusChangeEvent(configRegistration, LifecycleStatus.DOWN), coordinator)

        verify(sub).close()
    }

    @Test
    fun `processing an ERROR status change for configuration closes the subscription`() {
        handler.sub = sub
        handler.configServiceRegistration = configRegistration

        handler.processEvent(RegistrationStatusChangeEvent(configRegistration, LifecycleStatus.ERROR), coordinator)

        verify(sub).close()
    }

    @Test
    fun `processing an UP status change for permission service sets coordinator status to UP`() {
        handler.permissionServiceRegistration = permissionServiceRegistration

        handler.processEvent(RegistrationStatusChangeEvent(permissionServiceRegistration, LifecycleStatus.UP), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `processing a DOWN status change for permission service sets coordinator status to DOWN`() {
        handler.permissionServiceRegistration = permissionServiceRegistration

        handler.processEvent(RegistrationStatusChangeEvent(permissionServiceRegistration, LifecycleStatus.DOWN), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `processing a ERROR status change for permission service sets coordinator status to ERROR and stops the service`() {
        handler.permissionServiceRegistration = permissionServiceRegistration

        handler.processEvent(RegistrationStatusChangeEvent(permissionServiceRegistration, LifecycleStatus.ERROR), coordinator)

        verify(coordinator).stop()
        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `processing a STOP event stops the service's dependencies and sets service's status to DOWN`() {
        handler.configServiceRegistration = configRegistration
        handler.permissionServiceRegistration = permissionServiceRegistration
        handler.sub = sub
        handler.server = server
        handler.securityManager = securityManager
        handler.sslCertReadService = sslCertReadService

        handler.processEvent(StopEvent(), coordinator)

        verify(permissionServiceRegistration).close()
        verify(configRegistration).close()
        verify(sub).close()
        verify(server).close()
        verify(securityManager).stop()
        verify(sslCertReadService).stop()
        verify(endpoint).stop()
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)

        assertNull(handler.permissionServiceRegistration)
        assertNull(handler.configServiceRegistration)
        assertNull(handler.sub)
        assertNull(handler.server)
        assertNull(handler.securityManager)
        assertNull(handler.sslCertReadService)
    }
}