package net.corda.membership.groupparams.writer.service.impl

import com.typesafe.config.ConfigFactory
import jdk.jshell.spi.ExecutionControl.NotImplementedException
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentGroupParameters
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.layeredpropertymap.toAvro
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
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.impl.GroupParametersImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.GROUP_PARAMETERS_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.KArgumentCaptor
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
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture

class GroupParametersWriterServiceTest {
    private val viewOwner = HoldingIdentity(MemberX500Name("R3", "London", "GB"), "groupId")
    private val clock = TestClock(Instant.ofEpochSecond(100))
    private val testConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseString("instanceId=1"))

    private val dependencyHandle: RegistrationHandle = mock()
    private val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
    )
    private var coordinatorIsRunning = false
    private val lifecycleHandlerCaptor: KArgumentCaptor<LifecycleEventHandler> = argumentCaptor()
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

    private val mockPublisher = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }
    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
    }

    private val keyBytes = "key-bytes".toByteArray()
    private val keyEncodingService: KeyEncodingService = mock {
        on { encodeAsByteArray(any()) } doReturn keyBytes
    }
    private val serializedGroupParameters = "group-params".toByteArray()
    private val cordaAvroSerialiser: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(any()) } doReturn serializedGroupParameters
    }

    private val cordaAvroSerialisationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn cordaAvroSerialiser
    }

    private val writerService = GroupParametersWriterServiceImpl(
        coordinatorFactory,
        configurationReadService,
        publisherFactory,
        keyEncodingService,
        cordaAvroSerialisationFactory
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
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG, ConfigKeys.MEMBERSHIP_CONFIG),
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
            writerService.start()
            assertThat(writerService.isRunning).isTrue
            verify(coordinator).start()
        }

        @Test
        fun `stopping the service succeeds`() {
            writerService.start()
            writerService.stop()
            assertThat(writerService.isRunning).isFalse
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
        fun `registration status ERROR sets status to DOWN`() {
            postRegistrationStatusChangeEvent(LifecycleStatus.ERROR)

            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        }

        @Test
        fun `registration status UP creates config handle and closes it first if it exists`() {
            postStartEvent()
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
                assertThat(clientId).isEqualTo("group-parameters-writer-service")
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
            verify(mockPublisher, times(3)).close()
        }

        @Test
        fun `exception is thrown if functions are called before starting the service`() {
            val ex1 = assertThrows<IllegalStateException> { writerService.put(viewOwner, mock()) }
            assertThat(ex1.message).contains("inactive")
            val ex2 = assertThrows<IllegalStateException> { writerService.remove(viewOwner) }
            assertThat(ex2.message).contains("inactive")
        }


    }

    @Nested
    inner class WriterTests {
        @Test
        fun `put publishes records to kafka`() {
            postConfigChangedEvent()

            val capturedPublishedList = argumentCaptor<List<Record<String, PersistentGroupParameters>>>()
            whenever(mockPublisher.publish(capturedPublishedList.capture()))
                .doReturn(listOf(CompletableFuture.completedFuture(Unit)))

            val ownerId = viewOwner.shortHash.toString()
            val params = LayeredPropertyMapMocks.create<GroupParametersImpl>(
                sortedMapOf(
                    MPV_KEY to "1",
                    EPOCH_KEY to "2",
                    MODIFIED_TIME_KEY to clock.instant().toString()
                ),
                emptyList()
            )
            writerService.put(viewOwner, params)

            val result = capturedPublishedList.firstValue
            assertSoftly {
                it.assertThat(result.size).isEqualTo(1)
                val record = result.first()
                it.assertThat(record.topic).isEqualTo(GROUP_PARAMETERS_TOPIC)
                it.assertThat(record.key).isEqualTo(ownerId)
                it.assertThat(record.value).isInstanceOf(PersistentGroupParameters::class.java)
                val publishedParams = record.value as PersistentGroupParameters
                it.assertThat(publishedParams.viewOwner).isEqualTo(viewOwner.toAvro())

                publishedParams.groupParameters
                it.assertThat(publishedParams.groupParameters.groupParameters)
                    .isEqualTo(ByteBuffer.wrap(serializedGroupParameters))

                it.assertThat(publishedParams.groupParameters.mgmSignature).isNull()
            }
        }

        @Test
        fun `remove function throws exception`() {
            postConfigChangedEvent()

            val ex = assertThrows<NotImplementedException> { writerService.remove(viewOwner) }
            assertThat(ex.message).contains("not supported")
        }
    }
}