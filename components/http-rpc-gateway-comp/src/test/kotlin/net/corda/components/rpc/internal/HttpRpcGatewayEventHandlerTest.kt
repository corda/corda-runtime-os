package net.corda.components.rpc.internal

import net.corda.components.rbac.RBACSecurityManagerService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.httprpc.server.security.local.HttpRpcLocalJwtSigner
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
import net.corda.permissions.management.PermissionManagementService
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class HttpRpcGatewayEventHandlerTest {

    private val permissionManagementService = mock<PermissionManagementService>()

    private val registration = mock<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val sub = mock<AutoCloseable>()
    private val server = mock<HttpRpcServer>()
    private val sslCertReadService = mock<SslCertReadService>()

    private val configurationReadService = mock<ConfigurationReadService>()
    private val httpRpcServerFactory = mock<HttpRpcServerFactory>()
    private val rbacSecurityManagerService = mock<RBACSecurityManagerService>()
    private val sslCertReadServiceFactory = mock<SslCertReadServiceFactory>()
    private val httpRpcLocalJwtSigner = mock<HttpRpcLocalJwtSigner>()

    private interface MockEndpoint : PluggableRPCOps<MockEndpoint>, Lifecycle
    private val endpoint = mock<MockEndpoint>()
    private val rpcOps: List<PluggableRPCOps<out RpcOps>> = listOf(endpoint)

    private val handler = HttpRpcGatewayEventHandler(
        permissionManagementService,
        configurationReadService,
        httpRpcServerFactory,
        rbacSecurityManagerService,
        sslCertReadServiceFactory,
        rpcOps,
        httpRpcLocalJwtSigner
    )

    @BeforeEach
    fun setUp() {
        whenever(coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionManagementService>(),
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<RBACSecurityManagerService>(),
            )
        )).thenReturn(registration)
    }

    @Test
    fun `processing a start event follows permission service and configuration service and starts permission service`() {
        handler.processEvent(StartEvent(), coordinator)

        verify(coordinator).followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionManagementService>(),
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<RBACSecurityManagerService>(),
            )
        )

        verify(permissionManagementService).start()
        verify(rbacSecurityManagerService).start()
    }

    @Test
    fun `processing an UP status change for configuration registers for config updates and sets status to UP`() {
        handler.registration = registration

        handler.processEvent(RegistrationStatusChangeEvent(registration, LifecycleStatus.UP), coordinator)

        verify(configurationReadService).registerForUpdates(any())
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `processing a DOWN status change for configuration triggers a stop event`() {
        handler.sub = sub
        handler.registration = registration

        handler.processEvent(RegistrationStatusChangeEvent(registration, LifecycleStatus.DOWN), coordinator)

        verify(coordinator).postEvent(StopEvent())
    }

    @Test
    fun `processing an ERROR status change for configuration triggers a stop event with error flag`() {
        handler.sub = sub
        handler.registration = registration

        handler.processEvent(RegistrationStatusChangeEvent(registration, LifecycleStatus.ERROR), coordinator)

        verify(coordinator).postEvent(StopEvent(true))
    }

    @Test
    fun `processing a STOP event stops the service's dependencies and sets service's status to DOWN`() {
        handler.registration = registration
        handler.sub = sub
        handler.server = server
        handler.sslCertReadService = sslCertReadService

        handler.processEvent(StopEvent(), coordinator)

        verify(registration).close()
        verify(sub).close()
        verify(permissionManagementService).stop()
        verify(rbacSecurityManagerService).stop()
        verify(server).close()
        verify(sslCertReadService).stop()
        verify(endpoint).stop()

        assertNull(handler.registration)
        assertNull(handler.sub)
        assertNull(handler.server)
        assertNull(handler.sslCertReadService)
    }
}