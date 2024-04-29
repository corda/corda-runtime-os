package net.corda.membership.locally.hosted.identities.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.CompletableFuture

class LocallyHostedIdentitiesWriterTest {
    private val lifecycleHandlerCaptor = argumentCaptor<LifecycleEventHandler>()
    private val capturedRecords = argumentCaptor<List<Record<String, HostedIdentityEntry>>>()
    private val mockPublisher = mock<Publisher> {
        on { publish(capturedRecords.capture()) } doReturn listOf(CompletableFuture.completedFuture(Unit))
    }
    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
    }
    private val coordinator = mock<LifecycleCoordinator> {
        on { status } doReturn LifecycleStatus.UP
        on { createManagedResource(any(), any<() -> Resource>()) } doAnswer {
            val generator: () -> Resource = it.getArgument(1)
            generator.invoke()
        }
        on { getManagedResource<Publisher>(any()) } doReturn mockPublisher
    }
    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), lifecycleHandlerCaptor.capture()) } doReturn coordinator
    }
    private val configHandle: Resource = mock()
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }
    private val testConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseString("instanceId=1"))

    private val writer = LocallyHostedIdentitiesWriterImpl(
        coordinatorFactory,
        configurationReadService,
        publisherFactory,
    )

    private fun postStartEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StartEvent(), coordinator)
    }

    private fun postStopEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StopEvent(), coordinator)
    }

    private fun postRegistrationStatusChangeEvent(
        status: LifecycleStatus,
    ) {
        lifecycleHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                mock(),
                status
            ),
            coordinator
        )
    }

    private fun postConfigChangedEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG),
                mapOf(
                    ConfigKeys.BOOT_CONFIG to testConfig,
                    ConfigKeys.MESSAGING_CONFIG to testConfig,
                )
            ),
            coordinator
        )
    }

    @Nested
    inner class LifecycleTests {
        @Test
        fun `starting the service starts coordinator`() {
            writer.start()

            verify(coordinator).start()
        }

        @Test
        fun `stopping the service stops coordinator`() {
            writer.start()

            writer.stop()

            verify(coordinator).stop()
        }

        @Test
        fun `start event follows changes`() {
            postStartEvent()

            verify(coordinator).followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                ),
            )
        }

        @Test
        fun `stop event closes resources and sets status to DOWN`() {
            postStopEvent()

            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            verify(coordinator).closeManagedResources(
                argThat {
                    size == 3
                },
            )
        }

        @Test
        fun `registration status DOWN closes resources and sets status to DOWN`() {
            postRegistrationStatusChangeEvent(LifecycleStatus.DOWN)

            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            verify(coordinator).closeManagedResources(argThat { size == 2 })
        }

        @Test
        fun `registration status UP will listen to configuration`() {
            postStartEvent()
            postRegistrationStatusChangeEvent(LifecycleStatus.UP)

            verify(configurationReadService).registerComponentForUpdates(
                coordinator,
                setOf(
                    ConfigKeys.BOOT_CONFIG,
                    ConfigKeys.MESSAGING_CONFIG,
                ),
            )
        }

        @Test
        fun `config changed event creates publisher`() {
            postConfigChangedEvent()

            verify(publisherFactory).createPublisher(any(), any())
            verify(mockPublisher).start()
        }
    }

    @Nested
    inner class WriterTests {

        private val identity = HoldingIdentity(
            MemberX500Name.parse("O=Alice, L=LONDON, C=GB"),
            "group",
        )
        private val identityEntry = HostedIdentityEntry(
            identity.toAvro(),
            "tlsTenantId",
            listOf("tlsCertificate"),
            HostedIdentitySessionKeyAndCert(
                "sessionPublicKey",
                listOf("sessionCertificate"),
            ),
            emptyList(),
            1
        )

        @Test
        fun `put will publish the record`() {
            postConfigChangedEvent()
            val key = identity.shortHash.value
            writer.put(key, identityEntry)

            val result = capturedRecords.firstValue
            assertSoftly {
                it.assertThat(result.size).isEqualTo(1)
                val record = result.single()
                it.assertThat(record.key).isEqualTo(key)
                it.assertThat(record.value).isEqualTo(identityEntry)
                it.assertThat(record.topic).isEqualTo(Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC)
            }
        }

        @Test
        fun `remove will publish a tombstone`() {
            postConfigChangedEvent()
            val key = identity.shortHash.value
            writer.remove(key)

            val result = capturedRecords.firstValue
            assertSoftly {
                it.assertThat(result.size).isEqualTo(1)
                val record = result.single()
                it.assertThat(record.key).isEqualTo(key)
                it.assertThat(record.value).isNull()
                it.assertThat(record.topic).isEqualTo(Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC)
            }
        }
    }
}
