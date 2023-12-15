package net.corda.components.rest.internal

import net.corda.components.rbac.RBACSecurityManagerService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.management.PermissionManagementService
import net.corda.rest.PluggableRestResource
import net.corda.rest.RestResource
import net.corda.rest.server.RestServer
import net.corda.rest.server.factory.RestServerFactory
import net.corda.rest.ssl.KeyStoreInfo
import net.corda.rest.ssl.SslCertReadService
import net.corda.rest.ssl.SslCertReadServiceFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.PathProvider
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class RestGatewayEventHandlerTest {

    private val permissionManagementService = mock<PermissionManagementService>()

    private val registration = mock<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val server = mock<RestServer>()
    private val sslCertReadService = mock<SslCertReadService>()
    private val restGatewayConfig = mock<SmartConfig>().also {
        whenever(it.getString(ConfigKeys.REST_ADDRESS)).thenReturn("localhost:0")
        whenever(it.getString(ConfigKeys.REST_CONTEXT_DESCRIPTION)).thenReturn("REST_CONTEXT_DESCRIPTION")
        whenever(it.getString(ConfigKeys.REST_CONTEXT_TITLE)).thenReturn("REST_CONTEXT_TITLE")
    }
    private val tempPathProvider = mock<PathProvider>().also {
        whenever(it.getOrCreate(any(), anyVararg())).thenReturn(mock())
    }

    private val configurationReadService = mock<ConfigurationReadService>()
    private val restServerFactory = mock<RestServerFactory>().also {
        whenever(it.createRestServer(any(), any(), any(), any(), eq(false))).thenReturn(mock())
    }
    private val rbacSecurityManagerService = mock<RBACSecurityManagerService>()
    private val sslCertReadServiceFactory = mock<SslCertReadServiceFactory>().also {
        val keyStoreInfo = mock<KeyStoreInfo>()
        whenever(keyStoreInfo.path).thenReturn(mock())
        whenever(keyStoreInfo.password).thenReturn("testPassword")
        whenever(sslCertReadService.getOrCreateKeyStoreInfo(any())).thenReturn(keyStoreInfo)
        whenever(it.create()).thenReturn(sslCertReadService)
    }

    private interface MockEndpoint : PluggableRestResource<MockEndpoint>, Lifecycle
    private val endpoint = mock<MockEndpoint>()
    private val restResources: List<PluggableRestResource<out RestResource>> = listOf(endpoint)

    private val handler = RestGatewayEventHandler(
        permissionManagementService,
        configurationReadService,
        restServerFactory,
        rbacSecurityManagerService,
        sslCertReadServiceFactory,
        ::restResources,
        tempPathProvider
    )

    @BeforeEach
    fun setUp() {
        whenever(
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<PermissionManagementService>(),
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<RBACSecurityManagerService>(),
                )
            )
        ).thenReturn(registration)
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
        handler.restGatewayConfig = restGatewayConfig

        handler.processEvent(RegistrationStatusChangeEvent(registration, LifecycleStatus.UP), coordinator)

        verify(restServerFactory).createRestServer(any(), any(), any(), any(), eq(false))

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `processing a DOWN status change for configuration triggers a stop event`() {
        handler.registration = registration
        handler.sslCertReadService = sslCertReadService

        handler.processEvent(RegistrationStatusChangeEvent(registration, LifecycleStatus.DOWN), coordinator)

        verify(sslCertReadService).stop()
    }

    @Test
    fun `processing an ERROR status change for configuration triggers a stop event with error flag`() {
        handler.registration = registration

        handler.processEvent(RegistrationStatusChangeEvent(registration, LifecycleStatus.ERROR), coordinator)

        verify(coordinator).postEvent(StopEvent(true))
    }

    @Test
    fun `processing a STOP event stops the service's dependencies and sets service's status to DOWN`() {
        handler.registration = registration
        handler.server = server
        handler.sslCertReadService = sslCertReadService

        handler.processEvent(StopEvent(), coordinator)

        verify(registration).close()
        verify(permissionManagementService).stop()
        verify(rbacSecurityManagerService).stop()
        verify(server).close()
        verify(sslCertReadService).stop()
        verify(endpoint).stop()

        assertNull(handler.registration)
        assertNull(handler.server)
        assertNull(handler.sslCertReadService)
    }
}
