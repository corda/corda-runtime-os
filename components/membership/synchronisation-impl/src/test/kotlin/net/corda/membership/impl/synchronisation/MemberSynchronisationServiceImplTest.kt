package net.corda.membership.impl.synchronisation

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedMemberInfo
import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.SignedMemberships
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.id
import net.corda.membership.lib.MemberInfoFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
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
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith

class MemberSynchronisationServiceImplTest {
    private companion object {
        const val GROUP_NAME = "dummy_group"
        const val PUBLISHER_CLIENT_ID = "member-synchronisation-service"
        val MEMBER_CONTEXT_BYTES = "2222".toByteArray()
        val MGM_CONTEXT_BYTES = "3333".toByteArray()
    }
    private val mockPublisher = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }
    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
    }
    private val componentHandle: RegistrationHandle = mock()
    private val configHandle: AutoCloseable = mock()
    private val testConfig =
        SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString("instanceId=1"))
    private val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
    )

    private var coordinatorIsRunning = false
    private var coordinatorStatus: KArgumentCaptor<LifecycleStatus> = argumentCaptor()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(eq(dependentComponents)) } doReturn componentHandle
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer {
            coordinatorIsRunning = true
            lifecycleHandlerCaptor.firstValue.processEvent(StartEvent(), mock)
        }
        on { stop() } doAnswer {
            coordinatorIsRunning = false
            lifecycleHandlerCaptor.firstValue.processEvent(StopEvent(), mock)
        }
        doNothing().whenever(it).updateStatus(coordinatorStatus.capture(), any())
        on { status } doAnswer { coordinatorStatus.firstValue }
    }

    private val lifecycleHandlerCaptor: KArgumentCaptor<LifecycleEventHandler> = argumentCaptor()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), lifecycleHandlerCaptor.capture()) } doReturn coordinator
    }
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }
    private val memberProvidedContext: MemberContext = mock()
    private val mgmProvidedContext: MGMContext = mock()
    private val participantName = MemberX500Name("Bob", "London", "GB")
    private val participant: MemberInfo = mock {
        on { memberProvidedContext } doReturn memberProvidedContext
        on { mgmProvidedContext } doReturn mgmProvidedContext
        on { name } doReturn participantName
        on { groupId } doReturn GROUP_NAME
    }
    private val memberInfoFactory: MemberInfoFactory = mock {
        on { create(any()) } doReturn participant
    }
    private val memberName = MemberX500Name("Alice", "London", "GB")
    private val member = HoldingIdentity(memberName, GROUP_NAME)
    private val memberContextList = KeyValuePairList(listOf(KeyValuePair(PARTY_NAME, participantName.toString())))
    private val mgmContextList = KeyValuePairList(listOf())
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> = mock {
        on { deserialize(MEMBER_CONTEXT_BYTES) } doReturn memberContextList
        on { deserialize(MGM_CONTEXT_BYTES) } doReturn mgmContextList
    }
    private val serializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn keyValuePairListDeserializer
    }
    private val memberContext: ByteBuffer = mock {
        on { array() } doReturn MEMBER_CONTEXT_BYTES
    }
    private val mgmContext: ByteBuffer = mock {
        on { array() } doReturn MGM_CONTEXT_BYTES
    }
    private val signedMemberInfo: SignedMemberInfo = mock {
        on { memberContext } doReturn memberContext
        on { mgmContext } doReturn mgmContext
    }
    private val signedMemberships: SignedMemberships = mock {
        on { memberships } doReturn listOf(signedMemberInfo)
    }
    private val membershipPackage: MembershipPackage = mock {
        on { memberships } doReturn signedMemberships
    }
    private val updates: ProcessMembershipUpdates = mock {
        on { destination } doReturn member.toAvro()
        on { membershipPackage } doReturn membershipPackage
    }
    private val synchronisationService = MemberSynchronisationServiceImpl(
        publisherFactory,
        configurationReadService,
        lifecycleCoordinatorFactory,
        serializationFactory,
        memberInfoFactory
    )

    private fun postStartEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StartEvent(), coordinator)
    }

    private fun postStopEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StopEvent(), coordinator)
    }

    private fun postRegistrationStatusChangeEvent(
        status: LifecycleStatus,
        handle: RegistrationHandle = componentHandle
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

    @Test
    fun `starting the service succeeds`() {
        synchronisationService.start()
        assertThat(synchronisationService.isRunning).isTrue
        verify(coordinator).start()
    }

    @Test
    fun `stopping the service succeeds`() {
        synchronisationService.start()
        synchronisationService.stop()
        assertThat(synchronisationService.isRunning).isFalse
        verify(coordinator).stop()
    }

    @Test
    fun `member list is successfully published on receiving membership package from MGM`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
        synchronisationService.processMembershipUpdates(updates)
        verify(mockPublisher, times(1)).publish(capturedPublishedList.capture())
        val publishedMemberList = capturedPublishedList.firstValue
        SoftAssertions.assertSoftly {
            it.assertThat(publishedMemberList.size).isEqualTo(1)
            val publishedMember = publishedMemberList.first()
            it.assertThat(publishedMember.topic).isEqualTo(MEMBER_LIST_TOPIC)
            it.assertThat(publishedMember.key).isEqualTo("${member.shortHash}-${participant.id}")
            with(publishedMember.value as PersistentMemberInfo) {
                val name = memberContext.items.first { item -> item.key == PARTY_NAME }.value
                it.assertThat(name).isEqualTo(participantName.toString())
                it.assertThat(mgmContext.items).isEmpty()
            }
        }
    }

    @Test
    fun `processing of membership updates fails when coordinator is not running`() {
        val ex1 = assertFailsWith<IllegalStateException>{ synchronisationService.processMembershipUpdates(mock()) }
        assertThat(ex1.message).isEqualTo("MemberSynchronisationService is currently inactive.")
    }

    @Test
    fun `component handle created on start and closed on stop`() {
        postStartEvent()

        verify(componentHandle, never()).close()
        verify(coordinator).followStatusChangesByName(eq(dependentComponents))

        postStartEvent()

        verify(componentHandle).close()
        verify(coordinator, times(2)).followStatusChangesByName(eq(dependentComponents))

        postStopEvent()
        verify(componentHandle, times(2)).close()
    }

    @Test
    fun `status set to down after stop`() {
        postStopEvent()

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(componentHandle, never()).close()
        verify(configHandle, never()).close()
        verify(mockPublisher, never()).close()
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
            assertThat(clientId).isEqualTo(PUBLISHER_CLIENT_ID)
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
}
