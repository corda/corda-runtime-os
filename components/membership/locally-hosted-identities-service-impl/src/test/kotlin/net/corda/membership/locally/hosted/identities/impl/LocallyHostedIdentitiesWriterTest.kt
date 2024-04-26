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
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class LocallyHostedIdentitiesWriterTest {
    private val dependencyHandle: RegistrationHandle = mock()
    private val lifecycleHandlerCaptor = argumentCaptor<LifecycleEventHandler>()
    private val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
    )
    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(eq(dependentComponents)) } doReturn dependencyHandle
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer {
            coordinatorIsRunning = true
            lifecycleHandlerCaptor.firstValue.processEvent(StartEvent(), mock)
        }
        on { stop() } doAnswer {
            coordinatorIsRunning = false
            lifecycleHandlerCaptor.firstValue.processEvent(StopEvent(), mock)
        }
    }
    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), lifecycleHandlerCaptor.capture()) } doReturn coordinator
    }
    private val configHandle: Resource = mock()
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }
    private val capturedRecords = argumentCaptor<List<Record<String, HostedIdentityEntry>>>()
    private val mockPublisher = mock<Publisher>().apply {
        whenever(publish(capturedRecords.capture())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }
    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
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
        handle: RegistrationHandle = dependencyHandle
    ) {
        lifecycleHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                handle,
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
        fun `starting the service succeeds`() {
            writer.start()
            assertThat(writer.isRunning).isTrue
            verify(coordinator).start()
        }

        @Test
        fun `stopping the service succeeds`() {
            writer.start()
            writer.stop()
            assertThat(writer.isRunning).isFalse
            verify(coordinator).stop()
        }

        @Test
        fun `status set to down after stop`() {
            postStopEvent()

            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            verify(dependencyHandle, never()).close()
            verify(configHandle, never()).close()
            verify(mockPublisher, never()).close()
        }

        @Test
        fun `registration status DOWN sets status to DOWN`() {
            postRegistrationStatusChangeEvent(LifecycleStatus.DOWN)

            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        }

        @Test
        fun `registration status UP creates config handle and closes it first if it exists`() {
            postStartEvent()
            verify(coordinator).followStatusChangesByName(dependentComponents)

            postRegistrationStatusChangeEvent(LifecycleStatus.UP)

            val configArgs = argumentCaptor<Set<String>>()
            verify(configHandle, never()).close()
            verify(configurationReadService).registerComponentForUpdates(
                eq(coordinator),
                configArgs.capture()
            )
            assertThat(configArgs.firstValue)
                .isEqualTo(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG))

            postRegistrationStatusChangeEvent(LifecycleStatus.UP)
            verify(configHandle).close()
            verify(configurationReadService, times(2)).registerComponentForUpdates(eq(coordinator), any())

            postStopEvent()
            verify(configHandle, times(2)).close()
        }

        @Test
        fun `config changed event creates publisher`() {
            postConfigChangedEvent()

            val configCaptor = argumentCaptor<PublisherConfig>()
            verify(mockPublisher, never()).close()
            verify(publisherFactory).createPublisher(
                configCaptor.capture(),
                any()
            )
            verify(mockPublisher).start()
            verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())

            with(configCaptor.firstValue) {
                assertThat(clientId).startsWith("LOCALLY_HOSTED_IDENTITIES_WRITER")
            }

            postConfigChangedEvent()
            verify(mockPublisher).close()
            verify(publisherFactory, times(2)).createPublisher(
                configCaptor.capture(),
                any()
            )
            verify(mockPublisher, times(2)).start()
            verify(coordinator, times(2)).updateStatus(eq(LifecycleStatus.UP), any())

            postStopEvent()
            verify(mockPublisher, times(2)).close()
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
