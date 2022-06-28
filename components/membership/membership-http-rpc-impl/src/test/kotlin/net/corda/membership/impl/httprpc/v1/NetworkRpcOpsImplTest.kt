package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NetworkRpcOpsImplTest {
    private val coordinator = mock<LifecycleCoordinator>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val certificatesClient = mock<CertificatesClient>()

    private val networkOps = NetworkRpcOpsImpl(
        lifecycleCoordinatorFactory,
        certificatesClient,
    )

    @Nested
    inner class LifeCycleTests {
        @Test
        fun `isRunning returns the coordinator status`() {
            whenever(coordinator.status).doReturn(LifecycleStatus.UP)

            assertThat(networkOps.isRunning).isTrue
        }

        @Test
        fun `start starts the coordinator`() {
            networkOps.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop stops the coordinator`() {
            networkOps.stop()

            verify(coordinator).stop()
        }

        @Test
        fun `UP event will set the status to up`() {
            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            verify(coordinator).updateStatus(LifecycleStatus.UP, "Dependencies are UP")
        }

        @Test
        fun `DOWN event will set the status to down`() {
            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), mock())

            verify(coordinator).updateStatus(LifecycleStatus.DOWN, "Dependencies are DOWN")
        }
    }
    @Nested
    inner class PublishToLocallyHostedIdentitiesTests {
        @Test
        fun `it calls the client code`() {
            networkOps.setupHostedIdentities(
                "id",
                "alias",
                "tls",
                "session",
            )

            verify(certificatesClient).setupLocallyHostedIdentity(
                "id",
                "alias",
                "tls",
                "session",
            )
        }
        @Test
        fun `it catches resource not found exception`() {
            whenever(
                certificatesClient.setupLocallyHostedIdentity(
                    any(),
                    any(),
                    any(),
                    any(),
                )
            ).doThrow(CertificatesResourceNotFoundException("Nop"))

            assertThrows<ResourceNotFoundException> {
                networkOps.setupHostedIdentities(
                    "id",
                    "alias",
                    "tls",
                    "session",
                )
            }
        }
        @Test
        fun `it catches any other exception exception`() {
            whenever(
                certificatesClient.setupLocallyHostedIdentity(
                    any(),
                    any(),
                    any(),
                    any(),
                )
            ).doThrow(RuntimeException("Nop"))

            assertThrows<InternalServerException> {
                networkOps.setupHostedIdentities(
                    "id",
                    "alias",
                    "tls",
                    "session",
                )
            }
        }
    }
}
