package net.corda.membership.impl.rest.v1

import net.corda.crypto.core.ShortHash
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.rest.v1.types.request.HostedIdentitySessionKeyAndCertificate
import net.corda.membership.rest.v1.types.request.HostedIdentitySetupRequest
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.ResourceNotFoundException
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

    private val networkRestResource = NetworkRestResourceImpl(
        lifecycleCoordinatorFactory,
        certificatesClient,
        mock(),
    )

    @Nested
    inner class LifecycleTests {
        @Test
        fun `isRunning returns the coordinator status`() {
            whenever(coordinator.status).doReturn(LifecycleStatus.UP)

            assertThat(networkRestResource.isRunning).isTrue
        }

        @Test
        fun `start starts the coordinator`() {
            networkRestResource.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop stops the coordinator`() {
            networkRestResource.stop()

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
            networkRestResource.setupHostedIdentities(
                "1234567890ab",
                HostedIdentitySetupRequest(
                    "alias",
                    true,
                    listOf(
                        HostedIdentitySessionKeyAndCertificate(
                            "1234567890ac",
                            preferred = true,
                        ),
                    )
                )
            )

            verify(certificatesClient).setupLocallyHostedIdentity(
                ShortHash.of("1234567890ab"),
                "alias",
                true,
                CertificatesClient.SessionKeyAndCertificate(ShortHash.of("1234567890ac"), null),
                emptyList(),
            )
        }

        @Test
        fun `it catches resource not found exception`() {
            whenever(
                certificatesClient.setupLocallyHostedIdentity(
                    any(),
                    any(),
                    any(),
                    anyOrNull(),
                    any()
                )
            ).doThrow(CertificatesResourceNotFoundException("Mock failure"))

            assertThrows<ResourceNotFoundException> {
                networkRestResource.setupHostedIdentities(
                    "1234567890ab",
                    HostedIdentitySetupRequest(
                        "alias",
                        false,
                        listOf(
                            HostedIdentitySessionKeyAndCertificate(
                                "1234567890ac",
                                null,
                                true,
                            )
                        )
                    )
                )
            }
        }

        @Test
        fun `it throws an exception if there are two preferred keys`() {
            assertThrows<BadRequestException> {
                networkRestResource.setupHostedIdentities(
                    "1234567890ab",
                    HostedIdentitySetupRequest(
                        "alias",
                        false,
                        listOf(
                            HostedIdentitySessionKeyAndCertificate(
                                "1234567890ac",
                                preferred = true,
                            ),
                            HostedIdentitySessionKeyAndCertificate(
                                "1234567890ac",
                                preferred = false,
                            ),
                            HostedIdentitySessionKeyAndCertificate(
                                "1234567890ac",
                                preferred = true,
                            ),
                        )
                    )
                )
            }
        }

        @Test
        fun `it throws an exception if there are no preferred keys`() {
            val exception = assertThrows<BadRequestException> {
                networkRestResource.setupHostedIdentities(
                    "1234567890ab",
                    HostedIdentitySetupRequest(
                        "alias",
                        false,
                        listOf(
                            HostedIdentitySessionKeyAndCertificate(
                                "1234567890aa",
                                preferred = false,
                            ),
                            HostedIdentitySessionKeyAndCertificate(
                                "1234567890ac",
                                preferred = false,
                            ),
                            HostedIdentitySessionKeyAndCertificate(
                                "1234567890ad",
                                preferred = false,
                            ),
                        )
                    )
                )
            }

            assertThat(exception).hasMessageContaining("No preferred session key was selected")
        }

        @Test
        fun `it throws an exception if there are no session keys`() {
            val exception = assertThrows<BadRequestException> {
                networkRestResource.setupHostedIdentities(
                    "1234567890ab",
                    HostedIdentitySetupRequest(
                        "alias",
                        false,
                        emptyList(),
                    )
                )
            }

            assertThat(exception).hasMessageContaining("No session keys where defined")
        }

        @Test
        fun `it uses the preferred key`() {
            val preferredKey = argumentCaptor<CertificatesClient.SessionKeyAndCertificate>()
            whenever(
                certificatesClient.setupLocallyHostedIdentity(
                    any(),
                    any(),
                    any(),
                    preferredKey.capture(),
                    any()
                )
            ).doAnswer { }

            networkRestResource.setupHostedIdentities(
                "1234567890ab",
                HostedIdentitySetupRequest(
                    "alias",
                    false,
                    listOf(
                        HostedIdentitySessionKeyAndCertificate(
                            "1234567890aa",
                            preferred = false,
                        ),
                        HostedIdentitySessionKeyAndCertificate(
                            "1234567890ac",
                            preferred = true,
                        ),
                        HostedIdentitySessionKeyAndCertificate(
                            "1234567890ad",
                            preferred = false,
                        ),
                    )
                )
            )

            assertThat(preferredKey.firstValue.sessionKeyId).isEqualTo(ShortHash.of("1234567890ac"))
        }

        @Test
        fun `it uses the alternative keys`() {
            val alternativeKeys = argumentCaptor<List<CertificatesClient.SessionKeyAndCertificate>>()
            whenever(
                certificatesClient.setupLocallyHostedIdentity(
                    any(),
                    any(),
                    any(),
                    any(),
                    alternativeKeys.capture(),
                )
            ).doAnswer { }

            networkRestResource.setupHostedIdentities(
                "1234567890ab",
                HostedIdentitySetupRequest(
                    "alias",
                    false,
                    listOf(
                        HostedIdentitySessionKeyAndCertificate(
                            "1234567890aa",
                            preferred = false,
                        ),
                        HostedIdentitySessionKeyAndCertificate(
                            "1234567890ac",
                            preferred = true,
                        ),
                        HostedIdentitySessionKeyAndCertificate(
                            "1234567890ad",
                            preferred = false,
                        ),
                    )
                )
            )

            assertThat(alternativeKeys.firstValue.map { it.sessionKeyId })
                .contains(ShortHash.of("1234567890ad"))
                .contains(ShortHash.of("1234567890aa"))
                .doesNotContain(ShortHash.of("1234567890ac"))
        }

        @Test
        fun `it catches bad request exception`() {
            assertThrows<BadRequestException> {
                networkRestResource.setupHostedIdentities(
                    "id",
                    HostedIdentitySetupRequest(
                        "alias",
                        false,
                        listOf(
                            HostedIdentitySessionKeyAndCertificate(
                                "1234567890ac",
                                preferred = true,
                            ),
                        )
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
                networkRestResource.setupHostedIdentities(
                    "id",
                    HostedIdentitySetupRequest(
                        "alias",
                        false,
                        listOf(
                            HostedIdentitySessionKeyAndCertificate(
                                "1234567890ac",
                                preferred = true,
                            ),
                        )
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
                    anyOrNull(),
                    any(),
                )
            ).doThrow(RuntimeException("Mock failure"))

            assertThrows<InternalServerException> {
                networkRestResource.setupHostedIdentities(
                    "79ED40726773",
                    HostedIdentitySetupRequest(
                        "alias",
                        true,
                        listOf(
                            HostedIdentitySessionKeyAndCertificate(
                                "79ED40726774",
                                null,
                                true,
                            )
                        )
                    )
                )
            }
        }
    }
}
