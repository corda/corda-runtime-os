package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus.PENDING_MEMBER_VERIFICATION
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
import net.corda.lifecycle.TimerEvent
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MembershipConfig.EXPIRATION_DATE_FOR_REGISTRATION_REQUESTS
import net.corda.schema.configuration.MembershipConfig.MAX_DURATION_BETWEEN_EXPIRED_REGISTRATION_REQUESTS_POLLS
import net.corda.test.util.time.TestClock
import net.corda.utilities.hours
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class ExpirationProcessorTest {
    private val groupId = UUID(100 ,30).toString()
    private val mgmOne = HoldingIdentity(
        MemberX500Name("Corda MGM One", "London", "GB"),
        groupId
    )
    private val mgmTwo = HoldingIdentity(
        MemberX500Name("Corda MGM Two", "London", "GB"),
        groupId
    )
    private val alice = HoldingIdentity(
        MemberX500Name("Alice", "London", "GB"),
        groupId
    )
    private val bob = HoldingIdentity(
        MemberX500Name("Bob", "London", "GB"),
        groupId
    )

    private val clock = TestClock(Instant.ofEpochMilli(100))

    private val messagingConfig = mock<SmartConfig>()
    private val membershipConfig = mock<SmartConfig> {
        on { getLong(MAX_DURATION_BETWEEN_EXPIRED_REGISTRATION_REQUESTS_POLLS) } doReturn 300
        on { getLong(EXPIRATION_DATE_FOR_REGISTRATION_REQUESTS) } doReturn 180
    }
    private val configChangedEvent = mock<ConfigChangedEvent> {
        on { config } doReturn mapOf(MESSAGING_CONFIG to messagingConfig, MEMBERSHIP_CONFIG to membershipConfig)
    }
    private val publisher = mock<Publisher>()
    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), eq(messagingConfig)) } doReturn publisher
    }

    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { createManagedResource(any(), any<() -> Resource>()) } doAnswer {
            val function: () -> Resource = it.getArgument(1)
            function.invoke()
        }
        on { getManagedResource<Publisher>(any()) } doReturn publisher
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    private val notExpiredRegistrationRequest: RegistrationRequestDetails = mock {
        on { holdingIdentityId } doReturn alice.shortHash.value
        on { registrationLastModified } doReturn clock.instant()
        on { registrationId } doReturn UUID(100 ,30).toString()
    }
    private val expiredRegistrationRequest: RegistrationRequestDetails = mock {
        on { holdingIdentityId } doReturn bob.shortHash.value
        on { registrationLastModified } doReturn clock.instant().minusMillis(6.hours.toMillis())
        on { registrationId } doReturn UUID(100 ,30).toString()
    }
    private val configurationReadService: ConfigurationReadService = mock()
    private val membershipQueryClient: MembershipQueryClient = mock {
        on {
            queryRegistrationRequests(eq(mgmOne), eq(null), eq(listOf(PENDING_MEMBER_VERIFICATION)), eq(null))
        } doReturn MembershipQueryResult.Success(
            listOf(notExpiredRegistrationRequest, expiredRegistrationRequest)
        )
    }

    private val mgmVirtualNodeInfo: VirtualNodeInfo = mock {
        on { holdingIdentity } doReturn mgmTwo
    }
    private val aliceVirtualNodeInfo: VirtualNodeInfo = mock {
        on { holdingIdentity } doReturn alice
    }
    private val bobVirtualNodeInfo: VirtualNodeInfo = mock {
        on { holdingIdentity } doReturn bob
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(bob.shortHash) } doReturn bobVirtualNodeInfo
        on { getAll() } doReturn listOf(mgmVirtualNodeInfo, aliceVirtualNodeInfo, bobVirtualNodeInfo)
    }
    private val memberContext: MemberContext = mock {
        on { parse(GROUP_ID, String::class.java) } doReturn groupId
    }
    private val mgmInfo: MemberInfo = mock {
        on { mgmProvidedContext } doReturn mock()
        on { memberProvidedContext } doReturn memberContext
        on { isMgm } doReturn true
        on { name } doReturn mgmTwo.x500Name
    }
    private val aliceInfo: MemberInfo = mock {
        on { mgmProvidedContext } doReturn mock()
        on { memberProvidedContext } doReturn mock()
    }
    private val bobInfo: MemberInfo = mock {
        on { mgmProvidedContext } doReturn mock()
        on { memberProvidedContext } doReturn mock()
    }
    private val membershipGroupReader: MembershipGroupReader = mock {
        on { lookup(eq(mgmTwo.x500Name), any()) } doReturn mgmInfo
        on { lookup(eq(alice.x500Name), any()) } doReturn aliceInfo
        on { lookup(eq(bob.x500Name), any()) } doReturn bobInfo
    }
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn membershipGroupReader
    }

    private val expirationProcessor = ExpirationProcessorImpl(
        publisherFactory,
        configurationReadService,
        coordinatorFactory,
        membershipQueryClient,
        virtualNodeInfoReadService,
        membershipGroupReaderProvider,
        clock
    )

    @Nested
    inner class LifecycleTests {
        @Test
        fun `StartEvent will start following the lifecycle of the dependencies`() {
            handler.firstValue.processEvent(StartEvent(), coordinator)

            verify(coordinator).followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                    LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                    LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                )
            )
        }

        @Test
        fun `StopEvent will close managed resources and update the status to down`() {
            handler.firstValue.processEvent(StopEvent(), coordinator)

            verify(coordinator).closeManagedResources(
                argThat {
                    size == 3
                }
            )

            verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `config change with UP will wait for component updates`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP
                ),
                coordinator
            )

            verify(configurationReadService).registerComponentForUpdates(
                coordinator,
                setOf(BOOT_CONFIG, MESSAGING_CONFIG, MEMBERSHIP_CONFIG,)
            )
        }

        @Test
        fun `config change with DOWN will set the status to DOWN and close the resources`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.DOWN
                ),
                coordinator
            )

            verify(coordinator).updateStatus(LifecycleStatus.DOWN)
            verify(coordinator).closeManagedResources(argThat { size == 2 })
        }

        @Test
        fun `config changed event will create and start the publisher`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            verify(publisherFactory).createPublisher(any(), eq(messagingConfig))
            verify(publisher).start()
        }

        @Test
        fun `use values from config to configure the processor`() {
            val capturedTimeframes = argumentCaptor<Long>()
            val capturedEvents = argumentCaptor<(String) -> TimerEvent>()
            val dummyTime = 100L
            whenever(membershipConfig.getLong(MAX_DURATION_BETWEEN_EXPIRED_REGISTRATION_REQUESTS_POLLS))
                .thenReturn(dummyTime)
            whenever(membershipConfig.getLong(EXPIRATION_DATE_FOR_REGISTRATION_REQUESTS))
                .thenReturn(dummyTime)

            handler.firstValue.processEvent(configChangedEvent, coordinator)

            verify(coordinator, times(1)).setTimer(any(), capturedTimeframes.capture(), capturedEvents.capture())

            SoftAssertions.assertSoftly {
                it.assertThat(capturedTimeframes.firstValue).isLessThanOrEqualTo(TimeUnit.MINUTES.toMillis(dummyTime))

                val capturedEvent = capturedEvents.firstValue.invoke("")
                it.assertThat(capturedEvent).isInstanceOf(ExpirationProcessorImpl.DeclineExpiredRegistrationRequests::class.java)
                val declineEventAfterUpdate = capturedEvent as ExpirationProcessorImpl.DeclineExpiredRegistrationRequests
                it.assertThat(declineEventAfterUpdate.expirationDate).isEqualTo(TimeUnit.MINUTES.toMillis(dummyTime))
            }
        }

        @Test
        fun `start will start the coordinator`() {
            expirationProcessor.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop will stop the coordinator`() {
            expirationProcessor.stop()

            verify(coordinator).stop()
        }

        @Test
        fun `isRunning will return the coordinator status coordinator`() {
            whenever(coordinator.status).thenReturn(LifecycleStatus.UP)

            assertThat(expirationProcessor.isRunning).isTrue
        }
    }

    @Nested
    inner class FunctionalityTests {
        @BeforeEach
        fun setUp() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)
        }

        @Test
        fun `DeclineRegistration command is issued for expired requests`() {
            triggerEvent()
            val publishedRecords = argumentCaptor<List<Record<String, RegistrationCommand>>>()
            verify(publisher).publish(publishedRecords.capture())
            with(publishedRecords.firstValue) {
                SoftAssertions.assertSoftly {
                    it.assertThat(this.size).isEqualTo(1)
                    val record = this.first()
                    it.assertThat(record.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
                    it.assertThat(record.key).isEqualTo("${bob.x500Name}-${bob.groupId}")
                    it.assertThat(record.value?.command).isInstanceOf(DeclineRegistration::class.java)
                }
            }
        }

        @Test
        fun `processing fails - when persistence failure happens`() {
            whenever(
                membershipQueryClient.queryRegistrationRequests(
                    eq(mgmOne), eq(null), eq(listOf(PENDING_MEMBER_VERIFICATION)), eq(null)
                )
            ).thenReturn(MembershipQueryResult.Failure("error"))
            triggerEvent()
            verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
            verify(publisher, never()).publish(any())
        }

        @Test
        fun `processing fails - when virtual node info cannot be found`() {
            whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(eq(bob.shortHash))).thenReturn(null)
            triggerEvent()
            verify(publisher, never()).publish(any())
        }

        @AfterEach
        fun tearDown() {
            handler.firstValue.processEvent(StopEvent(), coordinator)
        }

        private fun triggerEvent() {
            val eventCaptor = argumentCaptor<(String) -> TimerEvent>()
            doNothing().whenever(coordinator).setTimer(any(), any(), eventCaptor.capture())
            expirationProcessor.scheduleProcessingOfExpiredRequests(mgmOne)
            // trigger the event manually, because we don't want to wait for 3 hours
            handler.firstValue.processEvent(eventCaptor.firstValue.invoke(""), coordinator)
        }
    }

    @Nested
    inner class TimerTests {
        @BeforeEach
        fun setUp() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)
        }

        @Test
        fun `schedule the next clean-up for stuck registration requests`() {
            val timerDuration = argumentCaptor<Long>()
            doNothing().whenever(coordinator).setTimer(any(), timerDuration.capture(), any())
            expirationProcessor.scheduleProcessingOfExpiredRequests(mgmOne)

            assertThat(timerDuration.firstValue)
                .isLessThanOrEqualTo(3.hours.toMillis())
                .isGreaterThanOrEqualTo(2.hours.toMillis())
        }

        @Test
        fun `processor after deactivation will not execute any previously scheduled clean-up and cancels the timer`() {
            val eventCaptor = argumentCaptor<(String) -> TimerEvent>()
            doNothing().whenever(coordinator).setTimer(any(), any(), eventCaptor.capture())
            expirationProcessor.scheduleProcessingOfExpiredRequests(mgmOne)
            handler.firstValue.processEvent(StopEvent(), coordinator)
            handler.firstValue.processEvent(eventCaptor.firstValue.invoke(""), coordinator)

            // cancels for mgmOne and mgmTwo
            verify(coordinator, times(2)).cancelTimer(any())
            verify(membershipQueryClient, never()).queryRegistrationRequests(any(), anyOrNull(), any(), anyOrNull())
            verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
            verify(publisher, never()).publish(any())
        }

        @Test
        fun `processor after deactivation and activation sets the timer again`() {
            expirationProcessor.scheduleProcessingOfExpiredRequests(mgmOne)

            handler.firstValue.processEvent(StopEvent(), coordinator)
            // cancels for mgmOne and mgmTwo
            verify(coordinator, times(2)).cancelTimer(any())

            handler.firstValue.processEvent(configChangedEvent, coordinator)
            val keyCaptor = argumentCaptor<String>()
            verify(coordinator, times(4)).setTimer(keyCaptor.capture(), any(), any())
            keyCaptor.allValues.forEach {
                assertTrue(it.contains(mgmOne.shortHash.value) || it.contains(mgmTwo.shortHash.value))
            }
        }

        @Test
        fun `mgms are loaded during activation`() {
            val keyCaptor = argumentCaptor<String>()
            verify(coordinator, times(1)).setTimer(keyCaptor.capture(), any(), any())
            verify(virtualNodeInfoReadService).getAll()
            assertThat(keyCaptor.firstValue).contains(mgmTwo.shortHash.value)
        }

        @AfterEach
        fun tearDown() {
            handler.firstValue.processEvent(StopEvent(), coordinator)
        }
    }
}