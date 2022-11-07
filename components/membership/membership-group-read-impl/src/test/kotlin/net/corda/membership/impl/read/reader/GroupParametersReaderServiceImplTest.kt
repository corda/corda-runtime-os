package net.corda.membership.impl.read.reader

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.GroupParameters as GroupParametersAvro
import net.corda.layeredpropertymap.impl.LayeredPropertyMapFactoryImpl
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
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.lib.impl.GroupParametersImpl
import net.corda.membership.lib.impl.GroupParametersImpl.Companion.EPOCH_KEY
import net.corda.membership.lib.impl.GroupParametersImpl.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.impl.GroupParametersImpl.Companion.MPV_KEY
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

class GroupParametersReaderServiceImplTest {
    private companion object {
        val clock = TestClock(Instant.ofEpochSecond(100))

        const val GROUP_ID = "groupId"
        val alice = HoldingIdentity(MemberX500Name.parse("O=Alice, L=London, C=GB"), GROUP_ID)
        val bob = HoldingIdentity(MemberX500Name.parse("O=Bob, L=London, C=GB"), GROUP_ID)
    }

    private val subscriptionHandle: RegistrationHandle = mock()
    private val dependencyHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()

    private val dependentComponents = setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())
    private val testConfig =
        SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString("instanceId=1"))

    private val subscriptionCoordinatorName = LifecycleCoordinatorName("SUB")
    private val groupParamsSubscription: CompactedSubscription<String, GroupParametersAvro> = mock {
        on { subscriptionName } doReturn subscriptionCoordinatorName
    }

    private val lifecycleHandlerCaptor: KArgumentCaptor<LifecycleEventHandler> = argumentCaptor()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(eq(dependentComponents)) } doReturn dependencyHandle
        on { followStatusChangesByName(eq(setOf(subscriptionCoordinatorName))) } doReturn subscriptionHandle
    }
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), lifecycleHandlerCaptor.capture()) } doReturn coordinator
    }

    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }
    private val subscriptionFactory: SubscriptionFactory = mock {
        on {
            createCompactedSubscription(any(), any<CompactedProcessor<String, GroupParametersAvro>>(), any())
        } doReturn groupParamsSubscription
    }
    private val layeredPropertyMapFactory = LayeredPropertyMapFactoryImpl(emptyList())

    private val testEntries1 = mapOf(
        EPOCH_KEY to "1",
        MPV_KEY to "1",
        MODIFIED_TIME_KEY to clock.instant().toString()
    )
    private val testEntries2 = mapOf(
        EPOCH_KEY to "2",
        MPV_KEY to "1",
        MODIFIED_TIME_KEY to clock.instant().toString()
    )
    private val groupParams = GroupParametersImpl(layeredPropertyMapFactory.createMap(testEntries1))
    private val groupParams2 = GroupParametersImpl(layeredPropertyMapFactory.createMap(testEntries2))
    private val map: ConcurrentHashMap<HoldingIdentity, GroupParameters> = ConcurrentHashMap(
        mapOf(alice to groupParams, bob to groupParams2)
    )
    private val groupParametersCache: MemberDataCache<GroupParameters> = mock {
        on { get(eq(alice)) } doReturn groupParams
        on { getAll() } doReturn map
    }
    private val groupParametersReaderService = GroupParametersReaderServiceImpl(
        lifecycleCoordinatorFactory,
        configurationReadService,
        subscriptionFactory,
        layeredPropertyMapFactory,
        groupParametersCache
    )

    @Nested
    inner class LifeCycleTests {
        @Test
        fun `start starts the coordinator`() {
            groupParametersReaderService.start()
            verify(coordinator).start()
        }

        @Test
        fun `stop stops the coordinator`() {
            groupParametersReaderService.stop()
            verify(coordinator).stop()
        }

        @Test
        fun `dependency handle created on start and closed on stop`() {
            postStartEvent()

            verify(dependencyHandle, never()).close()
            verify(coordinator).followStatusChangesByName(eq(dependentComponents))

            postStartEvent()

            verify(dependencyHandle).close()
            verify(coordinator, times(2)).followStatusChangesByName(eq(dependentComponents))

            postStopEvent()
            verify(dependencyHandle, times(2)).close()
        }

        @Test
        fun `status set to down after stop`() {
            postStopEvent()

            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            verify(dependencyHandle, never()).close()
            verify(configHandle, never()).close()
            verify(subscriptionHandle, never()).close()
            verify(groupParamsSubscription, never()).close()
            verify(groupParametersCache, never()).clear()
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
        fun `config changed event creates subscription for group parameters`() {
            postConfigChangedEvent()

            verify(groupParamsSubscription, never()).close()
            val configCaptor = argumentCaptor<SubscriptionConfig>()
            verify(subscriptionFactory).createCompactedSubscription(
                configCaptor.capture(),
                any<CompactedProcessor<String, GroupParameters>>(),
                any()
            )
            verify(groupParamsSubscription).start()
            verify(groupParamsSubscription).subscriptionName
            verify(coordinator).followStatusChangesByName(eq(setOf(groupParamsSubscription.subscriptionName)))

            assertThat(configCaptor.firstValue.eventTopic).isEqualTo(Schemas.Membership.GROUP_PARAMETERS_TOPIC)

            postConfigChangedEvent()
            verify(groupParamsSubscription).close()
            verify(subscriptionFactory, times(2)).createCompactedSubscription(
                configCaptor.capture(),
                any<CompactedProcessor<String, GroupParameters>>(),
                any()
            )
            verify(groupParamsSubscription, times(2)).start()
            verify(groupParamsSubscription, times(3)).subscriptionName
            verify(coordinator, times(2)).followStatusChangesByName(eq(setOf(groupParamsSubscription.subscriptionName)))

            postStopEvent()
            verify(groupParamsSubscription, times(2)).close()
        }

        @Test
        fun `service starts when subscription handle status is UP and cache gets cleared when its down`() {
            postConfigChangedEvent()
            postRegistrationStatusChangeEvent(LifecycleStatus.UP, subscriptionHandle)
            verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())

            postStopEvent()
            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            verify(groupParametersCache).clear()
        }
    }

    @Nested
    inner class ReaderServiceTests {
        @Test
        fun `calling get or get all results in exception if service is not running`() {
            val ex = assertThrows<IllegalStateException> { groupParametersReaderService.get(alice) }
            assertThat(ex.message).contains("inactive")

            val ex2 = assertThrows<IllegalStateException> { groupParametersReaderService.getAllVersionedRecords() }
            assertThat(ex2.message).contains("inactive")
        }

        @Test
        fun `calling get returns group params as expected`() {
            postConfigChangedEvent()
            postRegistrationStatusChangeEvent(LifecycleStatus.UP, subscriptionHandle)
            with(groupParametersReaderService.get(alice)) {
                assertThat(this).isEqualTo(groupParams)
            }
        }

        @Test
        fun `calling get all returns the versioned records as expected`() {
            postConfigChangedEvent()
            postRegistrationStatusChangeEvent(LifecycleStatus.UP, subscriptionHandle)
            val versionedRecords = groupParametersReaderService.getAllVersionedRecords()?.collect(Collectors.toList())
            assertThat(versionedRecords?.size).isEqualTo(2)

            val alika = versionedRecords?.first { it.key == alice }
            assertThat(alika?.version).isEqualTo(1)
            assertThat(alika?.isDeleted).isFalse()
            assertThat(alika?.value).isEqualTo(groupParams)

            val boboka = versionedRecords?.first { it.key == bob }
            assertThat(boboka?.version).isEqualTo(2)
            assertThat(boboka?.isDeleted).isFalse()
            assertThat(boboka?.value).isEqualTo(groupParams2)
        }
    }

    fun postStartEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StartEvent(), coordinator)
    }

    fun postStopEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StopEvent(), coordinator)
    }

    fun postRegistrationStatusChangeEvent(
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
                    ConfigKeys.MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )
    }
}