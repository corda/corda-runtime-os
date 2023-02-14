package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.httprpc.v1.types.request.HostedIdentitySetupRequest
import net.corda.virtualnode.ShortHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.SignatureException

class NetworkRestResourceImplTest {
    private val coordinator = mock<LifecycleCoordinator>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val certificatesClient = mock<CertificatesClient>()

    private val networkOps = NetworkRestResourceImpl(
        lifecycleCoordinatorFactory,
        certificatesClient,
    )

    @Nested
    inner class LifecycleTests {
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
                "1234567890ab",
                HostedIdentitySetupRequest(
                    "alias",
                    true,
                    "session"
                )
            )

            verify(certificatesClient).setupLocallyHostedIdentity(
                ShortHash.of("1234567890ab"),
                "alias",
                true,
                "session",
                null
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
                    anyOrNull()
                )
            ).doThrow(CertificatesResourceNotFoundException("Mock failure"))

            assertThrows<ResourceNotFoundException> {
                networkOps.setupHostedIdentities(
                    "1234567890ab",
                    HostedIdentitySetupRequest(
                        "alias",
                        false,
                        "session"
                    )
                )
            }
        }

        @Test
        fun `it catches bad request exception`() {
            assertThrows<BadRequestException> {
                networkOps.setupHostedIdentities(
                    "id",
                    HostedIdentitySetupRequest(
                        "alias",
                        false,
                        "session"
                    )
                )
            }
        }

        @Test
        fun `it catches signature exception`() {
            whenever(
                certificatesClient.setupLocallyHostedIdentity(
                    any(),
                    any(),
                    any(),
                    any(),
                    anyOrNull()
                )
            ).doAnswer { throw SignatureException("Mock failure") }

            assertThrows<BadRequestException> {
                networkOps.setupHostedIdentities(
                    "id",
                    HostedIdentitySetupRequest(
                        "alias",
                        false,
                        "session"
                    )
                )
            }
        }

        @Test
        fun `it catches any other exception`() {
            whenever(
                certificatesClient.setupLocallyHostedIdentity(
                    any(),
                    any(),
                    any(),
                    any(),
                    anyOrNull()
                )
            ).doThrow(RuntimeException("Mock failure"))

            assertThrows<InternalServerException> {
                networkOps.setupHostedIdentities(
                    "79ED40726773",
                    HostedIdentitySetupRequest(
                        "alias",
                        true,
                        "session"
                    )
                )
            }
        }
    }
}
