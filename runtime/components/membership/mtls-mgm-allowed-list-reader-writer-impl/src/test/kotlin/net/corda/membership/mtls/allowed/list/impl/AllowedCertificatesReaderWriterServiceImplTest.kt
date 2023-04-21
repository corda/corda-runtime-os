package net.corda.membership.mtls.allowed.list.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.p2p.mtls.MgmAllowedCertificateSubject
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
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import kotlin.streams.toList

class AllowedCertificatesReaderWriterServiceImplTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    val name = mock<LifecycleCoordinatorName>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { createManagedResource(any(), any<() -> Resource>()) } doAnswer {
            val function: () -> Resource = it.getArgument(1)
            function.invoke()
        }
        on { name } doReturn name
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val compactedSubscription = mock<CompactedSubscription<String, MgmAllowedCertificateSubject>>()
    private val processor = argumentCaptor<CompactedProcessor<String, MgmAllowedCertificateSubject>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createCompactedSubscription(
                any(),
                processor.capture(),
                any()
            )
        } doReturn compactedSubscription
    }
    private val messagingConfig = mock<SmartConfig>()
    private val configChangedEvent = mock<ConfigChangedEvent> {
        on { config } doReturn mapOf(ConfigKeys.MESSAGING_CONFIG to messagingConfig)
    }
    private val configurationReadService = mock<ConfigurationReadService>()
    private val publisher = mock<Publisher>()
    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), eq(messagingConfig)) } doReturn publisher
    }

    private val impl = AllowedCertificatesReaderWriterServiceImpl(
        coordinatorFactory,
        subscriptionFactory,
        configurationReadService,
        publisherFactory
    )

    @Nested
    inner class LifecycleTest {
        @Test
        fun `StartEvent will follow the configuration read service`() {
            handler.firstValue.processEvent(StartEvent(), coordinator)

            verify(coordinator).followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                )
            )
        }

        @Test
        fun `StopEvent will close managed resources`() {
            handler.firstValue.processEvent(StopEvent(), coordinator)

            verify(coordinator).closeManagedResources(
                argThat {
                    size == 4
                }
            )
        }

        @Test
        fun `StopEvent will set the status to down`() {
            handler.firstValue.processEvent(StopEvent(), coordinator)

            verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `child up will wait for config`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP
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
        fun `child down will set the status to down`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.DOWN
                ),
                coordinator
            )

            verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `child down will close the resource`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.DOWN
                ),
                coordinator
            )

            verify(coordinator).closeManagedResources(argThat { size == 1 })
        }

        @Test
        fun `config changed event will create the publisher`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            verify(publisherFactory).createPublisher(any(), eq(messagingConfig))
        }

        @Test
        fun `config changed event will start the publisher`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            verify(publisher).start()
        }

        @Test
        fun `config changed event will create the subscription`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            verify(subscriptionFactory).createCompactedSubscription(
                any(),
                eq(processor.firstValue),
                eq(messagingConfig),
            )
        }

        @Test
        fun `config changed event will start the subscription`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            verify(compactedSubscription).start()
        }

        @Test
        fun `start will start the coordinator`() {
            impl.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop will stop the coordinator`() {
            impl.stop()

            verify(coordinator).stop()
        }

        @Test
        fun `isRunning will return the coordinator status coordinator`() {
            whenever(coordinator.status).thenReturn(LifecycleStatus.UP)

            assertThat(impl.isRunning).isTrue
        }

        @Test
        fun `lifecycleCoordinatorName return the correct name`() {
            assertThat(impl.lifecycleCoordinatorName).isSameAs(name)
        }
    }

    @Nested
    inner class ReaderTests {
        @BeforeEach
        fun startMe() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)
        }

        @Test
        fun `onNext will add item to list`() {
            processor.firstValue.onNext(
                Record("topic", "key", MgmAllowedCertificateSubject("subject", "group")),
                null,
                emptyMap()
            )

            val records = impl.getAllVersionedRecords()?.toList()

            assertThat(records).hasSize(1)
                .anySatisfy {
                    assertThat(it.isDeleted).isFalse
                    assertThat(it.key.subject).isEqualTo("subject")
                    assertThat(it.value.subject).isEqualTo("subject")
                    assertThat(it.key.groupId).isEqualTo("group")
                    assertThat(it.value.groupId).isEqualTo("group")
                }
        }

        @Test
        fun `onNext will remove old data`() {
            processor.firstValue.onNext(
                Record("topic", "key", MgmAllowedCertificateSubject("subject", "group")),
                null,
                emptyMap()
            )
            processor.firstValue.onNext(
                Record("topic", "key", null),
                MgmAllowedCertificateSubject("subject", "group"),
                emptyMap()
            )

            val records = impl.getAllVersionedRecords()?.toList()

            assertThat(records).isEmpty()
        }

        @Test
        fun `onNext will not remove old data if null`() {
            processor.firstValue.onNext(
                Record("topic", "key", MgmAllowedCertificateSubject("subject", "group")),
                null,
                emptyMap()
            )
            processor.firstValue.onNext(
                Record("topic", "key", null),
                null,
                emptyMap()
            )

            val records = impl.getAllVersionedRecords()?.toList()

            assertThat(records).hasSize(1)
        }

        @Test
        fun `onSnapshot will add all the data`() {
            processor.firstValue.onSnapshot(
                mapOf(
                    "one" to MgmAllowedCertificateSubject("subject 1", "group 1"),
                    "two" to MgmAllowedCertificateSubject("subject 2", "group 2"),
                )
            )

            val records = impl.getAllVersionedRecords()?.toList()

            assertThat(records).hasSize(2)
                .anySatisfy {
                    assertThat(it.isDeleted).isFalse
                    assertThat(it.key.subject).isEqualTo("subject 1")
                    assertThat(it.value.subject).isEqualTo("subject 1")
                    assertThat(it.key.groupId).isEqualTo("group 1")
                    assertThat(it.value.groupId).isEqualTo("group 1")
                }
                .anySatisfy {
                    assertThat(it.isDeleted).isFalse
                    assertThat(it.key.subject).isEqualTo("subject 2")
                    assertThat(it.value.subject).isEqualTo("subject 2")
                    assertThat(it.key.groupId).isEqualTo("group 2")
                    assertThat(it.value.groupId).isEqualTo("group 2")
                }
        }

        @Test
        fun `onSnapshot will set the state to up`() {
            processor.firstValue.onSnapshot(
                mapOf(
                    "one" to MgmAllowedCertificateSubject("subject 1", "group"),
                    "two" to MgmAllowedCertificateSubject("subject 2", "group"),
                )
            )

            verify(coordinator).updateStatus(LifecycleStatus.UP)
        }
    }

    @Nested
    inner class WriterTests {
        @BeforeEach
        fun setUp() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
        }

        @Test
        fun `put will publish the record`() {
            impl.put(MgmAllowedCertificateSubject("subject", "group"), MgmAllowedCertificateSubject("subject", "group"))

            verify(publisher).publish(
                argThat {
                    size == 1 &&
                        first().key == "group;subject" &&
                        (first().value as? MgmAllowedCertificateSubject)?.subject == "subject" &&
                        (first().value as? MgmAllowedCertificateSubject)?.groupId == "group"
                }
            )
        }

        @Test
        fun `remove will publish the record`() {
            impl.remove(MgmAllowedCertificateSubject("subject", "group"))

            verify(publisher).publish(
                argThat {
                    size == 1 &&
                        first().value == null &&
                        first().key == "group;subject"
                }
            )
        }
    }
}
