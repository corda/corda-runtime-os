package net.corda.membership.locally.hosted.identities.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.locally.hosted.identities.IdentityInfo
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class LocallyHostedIdentitiesServiceImplTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { status } doReturn LifecycleStatus.UP
        on { createManagedResource(any(), any<() -> Resource>()) } doAnswer {
            val generator : () -> Resource = it.getArgument(1)
            generator.invoke()
        }
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val processor = argumentCaptor<CompactedProcessor<String, HostedIdentityEntry>>()
    private val subscription = mock<CompactedSubscription<String, HostedIdentityEntry>>()
    private val messagingConfig = mock<SmartConfig>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createCompactedSubscription(
                any(),
                processor.capture(),
                same(messagingConfig),
            )
        } doReturn subscription
    }
    private val configurationReadService = mock<ConfigurationReadService>()
    private val certificates = listOf(mock<X509Certificate>())
    private val certificateFactory = mock<CertificateFactory> {
        on { generateCertificates(any()) } doReturn certificates
    }
    private val sleeper = mock<((Long) -> Unit)>()
    private val identity = HoldingIdentity(
        MemberX500Name.parse("O=Alice, L=LONDON, C=GB"),
        "group"
    )
    private val identityEntry = HostedIdentityEntry(
        identity.toAvro(),
        "tlsTenantId",
        "sessionKeyTenantId",
        listOf("tlsCertificate"),
        "sessionPublicKey",
        listOf("sessionCertificate"),
    )

    private val service = LocallyHostedIdentitiesServiceImpl(
        coordinatorFactory,
        subscriptionFactory,
        configurationReadService,
        certificateFactory,
        sleeper,
    )

    @Nested
    inner class HandleEventTest {
        @Test
        fun `start event will follow changes`() {
            handler.firstValue.processEvent(
                StartEvent(),
                coordinator
            )

            verify(coordinator).followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                )
            )
        }

        @Test
        fun `stop event will close all the resources`() {
            handler.firstValue.processEvent(
                StopEvent(),
                coordinator
            )

            verify(coordinator).closeManagedResources(
                argThat {
                    size == 3
                }
            )
        }

        @Test
        fun `stop event will set the status to down`() {
            handler.firstValue.processEvent(
                StopEvent(),
                coordinator
            )

            verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `RegistrationStatusChangeEvent to UP will listen to configuration`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP,
                ),
                coordinator
            )

            verify(configurationReadService).registerComponentForUpdates(
                coordinator,
                setOf(
                    ConfigKeys.BOOT_CONFIG,
                    ConfigKeys.MESSAGING_CONFIG,
                )
            )
        }

        @Test
        fun `RegistrationStatusChangeEvent to DOWN will stop listen to configuration`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.DOWN,
                ),
                coordinator
            )

            verify(coordinator).closeManagedResources(argThat { size == 1 })
        }

        @Test
        fun `RegistrationStatusChangeEvent to DOWN will set the state to down`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.DOWN,
                ),
                coordinator
            )

            verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `config changed will start the subscription`() {
            handler.firstValue.processEvent(
                ConfigChangedEvent(
                    emptySet(),
                    mapOf(
                        ConfigKeys.MESSAGING_CONFIG to messagingConfig
                    ),
                ),
                coordinator
            )

            verify(subscription).start()
        }
    }

    @Nested
    inner class LifecycleTest {
        @Test
        fun `start will start the coordinator`() {
            service.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop will stop the coordinator`() {
            service.stop()

            verify(coordinator).stop()
        }

        @Test
        fun `isRunning will return the coordinator status`() {
            assertThat(service.isRunning).isTrue
        }
    }
    @Nested
    inner class ProcessorTest {
        @BeforeEach
        fun setup() {
            handler.firstValue.processEvent(
                ConfigChangedEvent(
                    emptySet(),
                    mapOf(
                        ConfigKeys.MESSAGING_CONFIG to messagingConfig
                    ),
                ),
                coordinator
            )
        }

        @Test
        fun `onSnapshot will set the coordinator to started`() {
            processor.firstValue.onSnapshot(emptyMap())

            verify(coordinator).updateStatus(LifecycleStatus.UP)
        }

        @Test
        fun `onSnapshot will add the identities`() {
            processor.firstValue.onSnapshot(
                mapOf("id1" to identityEntry)
            )

            assertThat(service.getIdentityInfo(identity)).isEqualTo(
                IdentityInfo(
                    identity,
                    certificates,
                )
            )
        }

        @Test
        fun `onNext will add the identities`() {
            val newRecord = mock<Record<String, HostedIdentityEntry>> {
                on { value } doReturn identityEntry
            }

            processor.firstValue.onNext(
                newRecord,
                null,
                emptyMap()
            )

            assertThat(service.getIdentityInfo(identity)).isEqualTo(
                IdentityInfo(
                    identity,
                    certificates,
                )
            )
        }

        @Test
        fun `onNext will remove the old identities`() {
            processor.firstValue.onSnapshot(
                mapOf("id1" to identityEntry)
            )
            val newRecord = mock<Record<String, HostedIdentityEntry>> {
                on { value } doReturn null
            }

            processor.firstValue.onNext(
                newRecord,
                identityEntry,
                emptyMap()
            )

            assertThat(service.getIdentityInfo(identity)).isNull()
        }

        @Test
        fun `onNext will not throw anything if value is null and old value is null`() {
            val newRecord = mock<Record<String, HostedIdentityEntry>> {
                on { value } doReturn null
            }
            processor.firstValue.onNext(
                newRecord,
                null,
                emptyMap()
            )

            assertDoesNotThrow {
                service.getIdentityInfo(identity)
            }
        }
    }

    @Nested
    inner class GetIdentityInfoTest {
        @BeforeEach
        fun setup() {
            handler.firstValue.processEvent(
                ConfigChangedEvent(
                    emptySet(),
                    mapOf(
                        ConfigKeys.MESSAGING_CONFIG to messagingConfig
                    ),
                ),
                coordinator
            )
        }

        @Test
        fun `it throw exception when not ready`() {
            whenever(coordinator.status).thenReturn(LifecycleStatus.DOWN)

            assertThrows<CordaRuntimeException> {
                service.getIdentityInfo(identity)
            }
        }

        @Test
        fun `it return the identity if exists`() {
            processor.firstValue.onSnapshot(
                mapOf("id1" to identityEntry)
            )

            assertThat(service.getIdentityInfo(identity)).isNotNull
        }

        @Test
        fun `it will not sleep if the identity if exists`() {
            processor.firstValue.onSnapshot(
                mapOf("id1" to identityEntry)
            )

            service.getIdentityInfo(identity)

            verify(sleeper, never()).invoke(any())
        }

        @Test
        fun `it will sleep if the identity not exists`() {
            service.getIdentityInfo(identity)

            verify(sleeper, atLeastOnce()).invoke(any())
        }

        @Test
        fun `it will return the correct value after sleeping once`() {
            whenever(sleeper.invoke(any())).doAnswer {
                processor.firstValue.onSnapshot(
                    mapOf("id1" to identityEntry)
                )
            }

            service.getIdentityInfo(identity)

            assertThat(service.getIdentityInfo(identity)).isNotNull
        }
    }

}
