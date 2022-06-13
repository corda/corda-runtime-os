package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.layeredpropertymap.impl.LayeredPropertyMapFactoryImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.impl.MGMContextImpl
import net.corda.membership.impl.MemberContextImpl
import net.corda.membership.impl.converter.EndpointInfoConverter
import net.corda.membership.impl.converter.PublicKeyConverter
import net.corda.membership.impl.converter.PublicKeyHashConverter
import net.corda.membership.impl.registration.staticnetwork.TestUtils
import net.corda.membership.impl.toMemberInfo
import net.corda.membership.impl.toSortedMap
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MGMRegistrationServiceTest {
    companion object {
        private const val SESSION_KEY = "1234"
        private const val SESSION_KEY_ID = "1"
        private const val ECDH_KEY = "5678"
        private const val ECDH_KEY_ID = "2"
    }

    private val mgmName = MemberX500Name("Corda MGM", "London", "GB")
    private val mgm = HoldingIdentity(mgmName.toString(), "dummy_group")
    private val mgmId = mgm.id
    private val sessionKey: PublicKey = mock {
        on { encoded } doReturn SESSION_KEY.toByteArray()
    }
    private val sessionCryptoSigningKey: CryptoSigningKey = mock {
        on { publicKey } doReturn ByteBuffer.wrap(SESSION_KEY.toByteArray())
    }
    private val ecdhKey: PublicKey = mock {
        on { encoded } doReturn ECDH_KEY.toByteArray()
    }
    private val ecdhCryptoSigningKey: CryptoSigningKey = mock {
        on { publicKey } doReturn ByteBuffer.wrap(ECDH_KEY.toByteArray())
    }
    private val mockPublisher = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }

    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
    }
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(SESSION_KEY.toByteArray()) } doReturn sessionKey
        on { decodePublicKey(ECDH_KEY.toByteArray()) } doReturn ecdhKey

        on { encodeAsString(any()) } doReturn SESSION_KEY
        on { encodeAsString(ecdhKey) } doReturn ECDH_KEY
    }
    private val cryptoOpsClient: CryptoOpsClient = mock {
        on { lookup(mgmId, listOf(SESSION_KEY_ID)) } doReturn listOf(sessionCryptoSigningKey)
        on { lookup(mgmId, listOf(ECDH_KEY_ID)) } doReturn listOf(ecdhCryptoSigningKey)
    }
    private val configurationReadService: ConfigurationReadService = mock()
    private var coordinatorIsRunning = false
    private var coordinatorStatus = LifecycleStatus.DOWN
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
        on { updateStatus(any(), any()) } doAnswer {
            coordinatorStatus = it.arguments[0] as LifecycleStatus
        }
        on { status } doAnswer { coordinatorStatus }
    }

    private var registrationServiceLifecycleHandler: MGMRegistrationServiceLifecycleHandler? = null
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
        on { createCoordinator(any(), any()) } doAnswer {
            registrationServiceLifecycleHandler = it.arguments[1] as MGMRegistrationServiceLifecycleHandler
            coordinator
        }
    }
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = LayeredPropertyMapFactoryImpl(
        listOf(EndpointInfoConverter(), PublicKeyConverter(keyEncodingService), PublicKeyHashConverter())
    )
    private val registrationService = MGMRegistrationService(
        publisherFactory,
        configurationReadService,
        lifecycleCoordinatorFactory,
        layeredPropertyMapFactory,
        cryptoOpsClient,
        keyEncodingService
    )

    private val properties = mapOf(
        "corda.party.name" to mgmName.toString(),
        "corda.endpoints.0.connectionURL" to "localhost:1080",
        "corda.endpoints.0.protocolVersion" to "1",
        "corda.party.session.key.id" to SESSION_KEY_ID,
        "corda.ecdh.key.id" to ECDH_KEY_ID,
        "corda.group.protocol.registration" to "net.corda.membership.impl.registration.dynamic.MemberRegistrationService",
        "corda.group.protocol.synchronisation" to "net.corda.membership.impl.sync.dynamic.MemberSyncService",
        "corda.group.protocol.p2p.mode" to "AUTHENTICATED_ENCRYPTION",
        "corda.group.key.session.policy" to "Combined",
        "corda.group.pki.session" to "Standard",
        "corda.group.pki.tls" to "C5",
        "corda.group.truststore.session.0" to "-----BEGIN CERTIFICATE-----Base64–encoded certificate-----END CERTIFICATE-----",
        "corda.group.truststore.tls.0" to "-----BEGIN CERTIFICATE-----Base64–encoded certificate-----END CERTIFICATE-----",
    )

    @Suppress("UNCHECKED_CAST")
    private fun setUpPublisher() {
        // kicks off the MessagingConfigurationReceived event to be able to mock the Publisher
        registrationServiceLifecycleHandler?.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG), TestUtils.configs),
            coordinator
        )
    }

    @Test
    fun `starting and stopping the service succeeds`() {
        registrationService.start()
        assertTrue(registrationService.isRunning)
        registrationService.stop()
        assertFalse(registrationService.isRunning)
    }

    @Test
    fun `registration successfully builds MGM info and publishes it`() {
        setUpPublisher()
        registrationService.start()
        val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
        val result = registrationService.register(mgm, properties)
        verify(mockPublisher, times(1)).publish(capturedPublishedList.capture())
        val publishedMgmInfoList = capturedPublishedList.firstValue
        assertSoftly {
            it.assertThat(result.outcome).isEqualTo(MembershipRequestRegistrationOutcome.SUBMITTED)
            it.assertThat(publishedMgmInfoList.size).isEqualTo(1)
            val publishedMgmInfo = publishedMgmInfoList.first()
            it.assertThat(publishedMgmInfo.topic).isEqualTo(Schemas.Membership.MEMBER_LIST_TOPIC)
            val expectedRecordKey = "${mgmId}-${mgmId}"
            it.assertThat(publishedMgmInfo.key).isEqualTo(expectedRecordKey)
            val persistentMemberPublished = publishedMgmInfo.value as PersistentMemberInfo
            val mgmPublished = toMemberInfo(
                layeredPropertyMapFactory.create<MemberContextImpl>(
                    KeyValuePairList.fromByteBuffer(persistentMemberPublished.memberContext).toSortedMap()
                ),
                layeredPropertyMapFactory.create<MGMContextImpl>(
                    KeyValuePairList.fromByteBuffer(persistentMemberPublished.mgmContext).toSortedMap()
                )
            )
            it.assertThat(mgmPublished.name.toString()).isEqualTo(mgmName.toString())
        }
        registrationService.stop()
    }

    @Test
    fun `registration fails when coordinator is not running`() {
        setUpPublisher()
        val registrationResult = registrationService.register(mgm, mock())
        assertEquals(
            MembershipRequestRegistrationResult(
                MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                "Registration failed. Reason: MGMRegistrationService is not running/down."
            ),
            registrationResult
        )
    }

    @Test
    fun `registration fails when one or more properties are missing`() {
        setUpPublisher()
        val testProperties = mutableMapOf<String, String>()
        registrationService.start()
        properties.entries.apply {
            for (index in indices) {
                val result = registrationService.register(mgm, testProperties)
                assertSoftly {
                    it.assertThat(result.outcome).isEqualTo(MembershipRequestRegistrationOutcome.NOT_SUBMITTED)
                }
                elementAt(index).let { testProperties.put(it.key, it.value) }
            }
        }
        registrationService.stop()
    }
}
