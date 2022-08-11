package net.corda.membership.impl.registration.dynamic.member

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
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
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions
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
import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.CompletableFuture

class DynamicMemberRegistrationServiceTest {
    companion object {
        private const val SESSION_KEY = "1234"
        private const val SESSION_KEY_ID = "1"
        private const val LEDGER_KEY = "5678"
        private const val LEDGER_KEY_ID = "2"
        private const val PUBLISHER_CLIENT_ID = "dynamic-member-registration-service"
        private const val GROUP_NAME = "dummy_group"

        private val MEMBER_CONTEXT_BYTES = "2222".toByteArray()
        private val REQUEST_BYTES = "3333".toByteArray()
    }

    private val memberProvidedContext: MemberContext = mock()
    private val registrationResultId = UUID(3, 4)
    private val mgmProvidedContext: MGMContext = mock()
    private val mgmName = MemberX500Name("Corda MGM", "London", "GB")
    private val mgm = HoldingIdentity(mgmName, GROUP_NAME)
    private val mgmInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn memberProvidedContext
        on { mgmProvidedContext } doReturn mgmProvidedContext
        on { name } doReturn mgmName
        on { groupId } doReturn GROUP_NAME
        on { isMgm } doReturn true
    }
    private val memberName = MemberX500Name("Alice", "London", "GB")
    private val member = HoldingIdentity(memberName, GROUP_NAME)
    private val memberId = member.shortHash
    private val sessionKey: PublicKey = mock {
        on { encoded } doReturn SESSION_KEY.toByteArray()
    }
    private val sessionCryptoSigningKey: CryptoSigningKey = mock {
        on { publicKey } doReturn ByteBuffer.wrap(SESSION_KEY.toByteArray())
        on { id } doReturn "1"
    }
    private val ledgerKey: PublicKey = mock {
        on { encoded } doReturn LEDGER_KEY.toByteArray()
    }
    private val ledgerCryptoSigningKey: CryptoSigningKey = mock {
        on { publicKey } doReturn ByteBuffer.wrap(LEDGER_KEY.toByteArray())
        on { id } doReturn "2"
    }
    private val mockPublisher = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }

    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
    }
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(SESSION_KEY.toByteArray()) } doReturn sessionKey
        on { decodePublicKey(SESSION_KEY) } doReturn sessionKey
        on { decodePublicKey(LEDGER_KEY.toByteArray()) } doReturn ledgerKey

        on { encodeAsString(any()) } doReturn SESSION_KEY
        on { encodeAsString(ledgerKey) } doReturn LEDGER_KEY
    }
    private val mockSignature: DigitalSignature.WithKey = DigitalSignature.WithKey(mock(), byteArrayOf(1), emptyMap())
    private val cryptoOpsClient: CryptoOpsClient = mock {
        on { lookup(memberId.value, listOf(SESSION_KEY_ID)) } doReturn listOf(sessionCryptoSigningKey)
        on { lookup(memberId.value, listOf(LEDGER_KEY_ID)) } doReturn listOf(ledgerCryptoSigningKey)
        on {
            sign(
                any(),
                any(),
                any<SignatureSpec>(),
                any(),
                eq(CryptoOpsClient.EMPTY_CONTEXT)
            )
        }.doReturn(mockSignature)
    }

    private val componentHandle: RegistrationHandle = mock()
    private val configHandle: AutoCloseable = mock()
    private val testConfig =
        SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString("instanceId=1"))
    private val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
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
    private val keyValuePairListSerializer: CordaAvroSerializer<Any> = mock {
        on { serialize(any()) } doReturn MEMBER_CONTEXT_BYTES
    }
    private val registrationRequestSerializer: CordaAvroSerializer<Any> = mock {
        on { serialize(any()) } doReturn "3333".toByteArray()
    }
    private val serializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<Any>(any()) }.thenReturn(registrationRequestSerializer, keyValuePairListSerializer)
    }
    private val groupReader: MembershipGroupReader = mock {
        on { lookup() } doReturn listOf(mgmInfo)
    }
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }
    private val layeredPropertyMapFactory = mock<LayeredPropertyMapFactory> {
        on {createMap(any())} doAnswer {
            val map = it.getArgument<Map<String, String?>>(0)
            mock {
                on { entries } doReturn map.entries
            }
        }
    }
    private val registrationService = DynamicMemberRegistrationService(
        publisherFactory,
        configurationReadService,
        lifecycleCoordinatorFactory,
        cryptoOpsClient,
        keyEncodingService,
        serializationFactory,
        membershipGroupReaderProvider,
        mock(),
        layeredPropertyMapFactory,
    )

    private val context = mapOf(
        "corda.session.key.id" to SESSION_KEY_ID,
        "corda.session.key.signature.spec" to "CORDA.ECDSA.SECP256R1",
        "corda.endpoints.0.connectionURL" to "localhost:1080",
        "corda.endpoints.0.protocolVersion" to "1",
        "corda.ledger.keys.0.id" to LEDGER_KEY_ID,
        "corda.ledger.keys.0.signature.spec" to "CORDA.ECDSA.SECP256R1",
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
        registrationService.start()
        Assertions.assertThat(registrationService.isRunning).isTrue
        verify(coordinator).start()
    }

    @Test
    fun `stopping the service succeeds`() {
        registrationService.start()
        registrationService.stop()
        Assertions.assertThat(registrationService.isRunning).isFalse
        verify(coordinator).stop()
    }

    @Test
    fun `registration successfully builds unauthenticated message and publishes it`() {
        postConfigChangedEvent()
        registrationService.start()
        val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
        val result = registrationService.register(registrationResultId, member, context)
        verify(mockPublisher, times(1)).publish(capturedPublishedList.capture())
        val publishedMessageList = capturedPublishedList.firstValue
        SoftAssertions.assertSoftly {
            it.assertThat(result.outcome).isEqualTo(MembershipRequestRegistrationOutcome.SUBMITTED)
            it.assertThat(publishedMessageList.size).isEqualTo(1)
            val publishedMessage = publishedMessageList.first()
            it.assertThat(publishedMessage.topic).isEqualTo(Schemas.P2P.P2P_OUT_TOPIC)
            it.assertThat(publishedMessage.key).isEqualTo(memberId.value)
            val unauthenticatedMessagePublished =
                (publishedMessage.value as AppMessage).message as UnauthenticatedMessage
            it.assertThat(unauthenticatedMessagePublished.header.source).isEqualTo(member.toAvro())
            it.assertThat(unauthenticatedMessagePublished.header.destination).isEqualTo(mgm.toAvro())
            it.assertThat(unauthenticatedMessagePublished.payload).isEqualTo(ByteBuffer.wrap(REQUEST_BYTES))
        }
        registrationService.stop()
    }

    @Test
    fun `registration fails when coordinator is not running`() {
        val registrationResult = registrationService.register(registrationResultId, member, mock())
        Assertions.assertThat(registrationResult).isEqualTo(
            MembershipRequestRegistrationResult(
                MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                "Registration failed. Reason: DynamicMemberRegistrationService is not running."
            )
        )
    }

    @Test
    fun `registration fails when one or more context properties are missing`() {
        postConfigChangedEvent()
        val testProperties = mutableMapOf<String, String>()
        registrationService.start()
        context.entries.apply {
            for (index in indices) {
                val result = registrationService.register(registrationResultId, member, testProperties)
                SoftAssertions.assertSoftly {
                    it.assertThat(result.outcome).isEqualTo(MembershipRequestRegistrationOutcome.NOT_SUBMITTED)
                }
                elementAt(index).let { testProperties.put(it.key, it.value) }
            }
        }
        registrationService.stop()
    }

    @Test
    fun `registration fails when one or more context properties are numbered incorrectly`() {
        postConfigChangedEvent()
        val testProperties =
            context + mapOf(
                "corda.ledger.keys.100.id" to "9999"
            )
        registrationService.start()
        val result = registrationService.register(registrationResultId, member, testProperties)
        SoftAssertions.assertSoftly {
            it.assertThat(result.outcome).isEqualTo(MembershipRequestRegistrationOutcome.NOT_SUBMITTED)
            it.assertThat(result.message)
                .isEqualTo("Registration failed. Reason: Provided ledger key IDs are incorrectly numbered.")
        }
        registrationService.stop()
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
        Assertions.assertThat(configArgs.firstValue)
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
            Assertions.assertThat(clientId).isEqualTo(PUBLISHER_CLIENT_ID)
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
