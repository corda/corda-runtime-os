package net.corda.membership.impl.p2p

import com.typesafe.config.ConfigFactory
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.CryptoConsts.Categories.PRE_AUTH
import net.corda.crypto.hes.HybridEncryptionParams
import net.corda.crypto.hes.StableKeyPairDecryptor
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.crypto.SecureHash
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.command.synchronisation.SynchronisationCommand
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.membership.p2p.UnauthenticatedRegistrationRequest
import net.corda.data.membership.p2p.UnauthenticatedRegistrationRequestHeader
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import net.corda.data.sync.BloomFilter
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.impl.p2p.MembershipP2PProcessor.Companion.MEMBERSHIP_P2P_SUBSYSTEM
import net.corda.membership.impl.p2p.dummy.TestCryptoOpsClient
import net.corda.membership.impl.p2p.dummy.TestEphemeralKeyPairEncryptor
import net.corda.membership.impl.p2p.dummy.TestGroupReaderProvider
import net.corda.membership.impl.p2p.dummy.TestStableKeyPairDecryptor
import net.corda.membership.lib.MemberInfoExtension.Companion.ECDH_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.p2p.MembershipP2PReadService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.Schemas.Membership.SYNCHRONIZATION_TOPIC
import net.corda.schema.configuration.BootConfig.BOOT_MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.test.util.eventually
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.time.Clock
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.LoggerFactory

@ExtendWith(ServiceExtension::class, DBSetup::class)
class MembershipP2PIntegrationTest {

    private companion object {
        @InjectService(timeout = 5000)
        lateinit var publisherFactory: PublisherFactory

        @InjectService(timeout = 5000)
        lateinit var subscriptionFactory: SubscriptionFactory

        @InjectService(timeout = 5000)
        lateinit var configurationReadService: ConfigurationReadService

        @InjectService(timeout = 5000)
        lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

        @InjectService(timeout = 5000)
        lateinit var cryptoOpsClient: TestCryptoOpsClient

        @InjectService(timeout = 5000)
        lateinit var stableKeyPairDecryptor: TestStableKeyPairDecryptor

        @InjectService(timeout = 5000)
        lateinit var membershipGroupReaderProvider: TestGroupReaderProvider

        @InjectService(timeout = 5000)
        lateinit var membershipP2PReadService: MembershipP2PReadService

        @InjectService
        lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

        @InjectService(timeout = 5000)
        lateinit var memberInfoFactory: MemberInfoFactory

        @InjectService(timeout = 5000)
        lateinit var keyEncodingService: KeyEncodingService

        @InjectService(timeout = 5000)
        lateinit var ephemeralKeyPairEncryptor: TestEphemeralKeyPairEncryptor

        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val clock: Clock = TestClock(Instant.ofEpochSecond(100))

        const val MEMBER_CONTEXT_KEY = "key"
        const val MEMBER_CONTEXT_VALUE = "value"

        val bootConfig = SmartConfigFactory.createWithoutSecurityServices()
            .create(
                ConfigFactory.parseString(
                    """
                $INSTANCE_ID = 1
                $BUS_TYPE = INMEMORY
                $BOOT_MAX_ALLOWED_MSG_SIZE = 100000
                """
                )
            )
        private const val messagingConf = """
            componentVersion="5.1"
            maxAllowedMessageSize = 1000000
            subscription {
                consumer {
                    close.timeout = 6000
                    poll.timeout = 6000
                    thread.stop.timeout = 6000
                    processor.retries = 3
                    subscribe.retries = 3
                    commit.retries = 3
                }
                producer {
                    close.timeout = 6000
                }
            }
        """
        private val schemaVersion = ConfigurationSchemaVersion(1, 0)

        lateinit var p2pSender: Publisher
        lateinit var registrationRequestSerializer: CordaAvroSerializer<MembershipRegistrationRequest>
        lateinit var unauthRegistrationRequestSerializer: CordaAvroSerializer<UnauthenticatedRegistrationRequest>
        lateinit var headerSerializer: CordaAvroSerializer<UnauthenticatedRegistrationRequestHeader>
        lateinit var keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList>
        lateinit var keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList>
        lateinit var verificationRequestSerializer: CordaAvroSerializer<VerificationRequest>
        lateinit var verificationResponseSerializer: CordaAvroSerializer<VerificationResponse>
        lateinit var syncRequestSerializer: CordaAvroSerializer<MembershipSyncRequest>

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val coordinator = lifecycleCoordinatorFactory.createCoordinator<MembershipP2PIntegrationTest> { e, c ->
                if (e is StartEvent) {
                    logger.info("Starting test coordinator")
                    c.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                            LifecycleCoordinatorName.forComponent<MembershipP2PReadService>(),
                            LifecycleCoordinatorName.forComponent<StableKeyPairDecryptor>(),
                            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                        )
                    )
                } else if (e is RegistrationStatusChangeEvent) {
                    logger.info("Test coordinator is ${e.status}")
                    c.updateStatus(e.status)
                }
            }.also { it.start() }

            registrationRequestSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
            unauthRegistrationRequestSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
            headerSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
            keyValuePairListSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
            keyValuePairListDeserializer =
                cordaAvroSerializationFactory.createAvroDeserializer({}, KeyValuePairList::class.java)
            verificationRequestSerializer = cordaAvroSerializationFactory.createAvroSerializer {  }
            verificationResponseSerializer = cordaAvroSerializationFactory.createAvroSerializer {  }
            syncRequestSerializer = cordaAvroSerializationFactory.createAvroSerializer {  }

            setupConfig()
            cryptoOpsClient.start()
            stableKeyPairDecryptor.start()
            membershipGroupReaderProvider.start()
            membershipP2PReadService.start()
            configurationReadService.bootstrapConfig(bootConfig)

            p2pSender = publisherFactory.createPublisher(
                PublisherConfig("membership_p2p_test_sender"),
                messagingConfig = bootConfig
            ).also { it.start() }

            eventually {
                logger.info("Waiting for required services to start...")
                assertThat(coordinator.status).isEqualTo(LifecycleStatus.UP)
                logger.info("Required services started.")
            }
        }

        @AfterAll
        fun tearDown() {
            p2pSender.close()
        }

        private fun setupConfig() {
            val publisher = publisherFactory.createPublisher(PublisherConfig("clientId"), bootConfig)
            publisher.publish(
                listOf(
                    Record(
                        Schemas.Config.CONFIG_TOPIC,
                        ConfigKeys.MESSAGING_CONFIG,
                        Configuration(messagingConf, messagingConf, 0, schemaVersion)
                    )
                )
            )
            configurationReadService.start()
            configurationReadService.bootstrapConfig(bootConfig)
        }
    }
    @Test
    fun `membership p2p service reads registration requests from the p2p topic and puts them on a membership topic for further processing`() {
        val groupId = UUID.randomUUID().toString()
        val source = createTestHoldingIdentity("O=Alice,C=GB,L=London", groupId)
        val destination = createTestHoldingIdentity("O=MGM,C=GB,L=London", groupId)
        val registrationId = UUID.randomUUID().toString()
        val fakeKey = "fakeKey"
        val fakeSig = "fakeSig"

        val ecdhKey = cryptoOpsClient.generateKeyPair(
            destination.shortHash.value,
            PRE_AUTH,
            destination.shortHash.value + "ecdh",
            ECDSA_SECP256R1_CODE_NAME
        )

        val mgm = memberInfoFactory.create(
            sortedMapOf(
                GROUP_ID to groupId,
                PARTY_NAME to destination.x500Name.toString(),
                Pair(String.format(URL_KEY, "0"), "https://localhost:8080"),
                Pair(String.format(PROTOCOL_VERSION, "0"), "1"),
                PLATFORM_VERSION to "1",
                SOFTWARE_VERSION to "5.0.0",
                ECDH_KEY to keyEncodingService.encodeAsString(ecdhKey)
            ),
            sortedMapOf(
                STATUS to MEMBER_STATUS_ACTIVE,
                IS_MGM to "true"
            )
        )

        membershipGroupReaderProvider.loadMember(destination, mgm)

        val completableResult = CompletableFuture<Pair<RegistrationState?, Record<String, RegistrationCommand>>>()

        // Set up subscription to gather results of processing p2p message
        val registrationRequestSubscription = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("membership_p2p_test_receiver", REGISTRATION_COMMAND_TOPIC),
            getTestStateAndEventProcessor { s, e ->
                if(e.value?.command is StartRegistration) {
                    completableResult.complete(Pair(s, e))
                }
            },
            messagingConfig = bootConfig
        ).also { it.start() }

        val memberContext = KeyValuePairList(listOf(KeyValuePair(MEMBER_CONTEXT_KEY, MEMBER_CONTEXT_VALUE)))
        val fakeSigWithKey = CryptoSignatureWithKey(
            ByteBuffer.wrap(fakeKey.encodeToByteArray()),
            ByteBuffer.wrap(fakeSig.encodeToByteArray()),
            KeyValuePairList(emptyList())
        )
        val messageHeader = UnauthenticatedMessageHeader(
            destination.toAvro(),
            source.toAvro(),
            MEMBERSHIP_P2P_SUBSYSTEM,
            "messageId",
        )
        val message = MembershipRegistrationRequest(
            registrationId,
            ByteBuffer.wrap(keyValuePairListSerializer.serialize(memberContext)),
            fakeSigWithKey,
            true
        )

        var latestHeader: UnauthenticatedRegistrationRequestHeader? = null

        val encryptedMessage = ephemeralKeyPairEncryptor.encrypt(
            ecdhKey,
            registrationRequestSerializer.serialize(message)!!
        ) { ek, _ ->
            val header = UnauthenticatedRegistrationRequestHeader(
                ByteBuffer.wrap("salt".toByteArray()),
                ByteBuffer.wrap("aad".toByteArray()),
                keyEncodingService.encodeAsString(ek)
            )
            val serializedHeader = headerSerializer.serialize(header)
                ?: throw IllegalArgumentException("Serialized header cannot be null.")
            latestHeader = header
            HybridEncryptionParams( "salt".toByteArray(), serializedHeader )
        }

        val request = UnauthenticatedRegistrationRequest(
            latestHeader,
            ByteBuffer.wrap(encryptedMessage.cipherText)
        )

        // Publish P2P message requesting registration
        val sendFuture = p2pSender.publish(
            listOf(
                buildUnauthenticatedP2PRequest(
                    messageHeader,
                    ByteBuffer.wrap(unauthRegistrationRequestSerializer.serialize(request))
                )
            )
        )

        // Wait for latch to countdown so we know when processing has completed and results have been collected
        val result = assertDoesNotThrow {
            completableResult.getOrThrow(Duration.ofSeconds(10))
        }
        registrationRequestSubscription.close()

        // Assert Results
        assertThat(sendFuture).hasSize(1)
            .allSatisfy {
                assertThat(it).isCompletedWithValue(Unit)
            }
        assertThat(sendFuture.single().isDone).isTrue
        assertThat(result).isNotNull
        assertThat(result?.first).isNull()
        assertThat(result?.second).isNotNull
        with(result!!.second) {
            assertThat(topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
            assertThat(key).isEqualTo("$registrationId-${destination.shortHash}")
            assertThat(value)
                .isNotNull
                .isInstanceOf(RegistrationCommand::class.java)
            assertThat(value!!.command)
                .isNotNull
                .isInstanceOf(StartRegistration::class.java)

            with(value!!.command as StartRegistration) {
                assertThat(this.destination.x500Name).isEqualTo(destination.x500Name.toString())
                assertThat(this.destination.groupId).isEqualTo(groupId)
                assertThat(this.source.x500Name).isEqualTo(source.x500Name.toString())
                assertThat(this.source.groupId).isEqualTo(groupId)
                assertThat(memberRegistrationRequest).isNotNull
                with(memberRegistrationRequest) {
                    assertThat(this.registrationId).isEqualTo(registrationId)
                    val deserializedContext = keyValuePairListDeserializer.deserialize(this.memberContext.array())
                    assertThat(deserializedContext)
                        .isNotNull
                        .isEqualTo(memberContext)
                    assertThat(deserializedContext!!.items.size).isEqualTo(1)
                    assertThat(deserializedContext.items.single().key).isEqualTo(MEMBER_CONTEXT_KEY)
                    assertThat(deserializedContext.items.single().value).isEqualTo(MEMBER_CONTEXT_VALUE)
                    assertThat(memberSignature).isEqualTo(fakeSigWithKey)
                    assertThat(memberSignature.publicKey.array().decodeToString()).isEqualTo(fakeKey)
                    assertThat(memberSignature.bytes.array().decodeToString()).isEqualTo(fakeSig)
                }
            }
        }
    }

    @Test
    fun `membership p2p service reads verification requests from the p2p topic and puts them on a membership topic for further processing`() {
        val groupId = UUID.randomUUID().toString()
        val source = createTestHoldingIdentity("O=MGM,C=GB,L=London", groupId)
        val destination = createTestHoldingIdentity("O=Alice,C=GB,L=London", groupId)
        val registrationId = UUID.randomUUID().toString()
        val requestTimestamp = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        val requestBody = KeyValuePairList(listOf(KeyValuePair("KEY", "dummyKey")))
        val completableResult = CompletableFuture<Pair<RegistrationState?, Record<String, RegistrationCommand>>>()

        // Set up subscription to gather results of processing p2p message
        val verificationRequestSubscription = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("membership_p2p_test_receiver", REGISTRATION_COMMAND_TOPIC),
            getTestStateAndEventProcessor { s, e ->
                if(e.value?.command is ProcessMemberVerificationRequest) {
                    completableResult.complete(Pair(s, e))
                }
            },
            messagingConfig = bootConfig,
            null
        ).also { it.start() }

        val messageHeader = buildAuthenticatedMessageHeader(
            destination.toAvro(),
            source.toAvro(),
            requestTimestamp,
            registrationId,
            MembershipStatusFilter.PENDING
        )
        val verificationRequest = VerificationRequest(registrationId, requestBody)

        val sendFuture = p2pSender.publish(
            listOf(
                buildAuthenticatedP2PRequest(
                    messageHeader,
                    ByteBuffer.wrap(verificationRequestSerializer.serialize(verificationRequest))
                )
            )
        )

        val result = assertDoesNotThrow {
            completableResult.getOrThrow(Duration.ofSeconds(5))
        }
        verificationRequestSubscription.close()

        assertThat(sendFuture).hasSize(1)
            .allSatisfy {
                assertThat(it).isCompletedWithValue(Unit)
            }
        assertThat(sendFuture.single().isDone).isTrue

        assertThat(result).isNotNull
        with(result.second) {
            assertThat(this.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
            assertThat(this.value).isInstanceOf(RegistrationCommand::class.java)
            assertThat(this.value?.command).isInstanceOf(ProcessMemberVerificationRequest::class.java)
            with(this.value?.command as ProcessMemberVerificationRequest) {
                assertThat(this.source).isEqualTo(source.toAvro())
                assertThat(this.destination).isEqualTo(destination.toAvro())
                assertThat(this.verificationRequest).isEqualTo(verificationRequest)
            }
        }
    }

    @Test
    fun `membership p2p service reads verification responses from the p2p topic and puts them on a membership topic for further processing`() {
        val groupId = UUID.randomUUID().toString()
        val source = createTestHoldingIdentity("O=Alice,C=GB,L=London", groupId)
        val destination = createTestHoldingIdentity("O=MGM,C=GB,L=London", groupId)
        val registrationId = UUID.randomUUID().toString()
        val requestTimestamp = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        val responseBody = KeyValuePairList(listOf(KeyValuePair("KEY", "dummyKey")))
        val completableResult = CompletableFuture<Pair<RegistrationState?, Record<String, RegistrationCommand>>>()

        val verificationResponseSubscription = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("membership_p2p_test_receiver", REGISTRATION_COMMAND_TOPIC),
            getTestStateAndEventProcessor { s, e ->
                if(e.value?.command is ProcessMemberVerificationResponse) {
                    completableResult.complete(Pair(s, e))
                }
            },
            messagingConfig = bootConfig,
            null
        ).also { it.start() }

        val messageHeader = buildAuthenticatedMessageHeader(
            destination.toAvro(),
            source.toAvro(),
            requestTimestamp,
            registrationId
        )
        val verificationResponse = VerificationResponse(registrationId, responseBody)

        val sendFuture = p2pSender.publish(
            listOf(
                buildAuthenticatedP2PRequest(
                    messageHeader,
                    ByteBuffer.wrap(verificationResponseSerializer.serialize(verificationResponse))
                )
            )
        )

        val result = assertDoesNotThrow {
            completableResult.getOrThrow(Duration.ofSeconds(5))
        }
        verificationResponseSubscription.close()

        assertThat(sendFuture).hasSize(1)
            .allSatisfy {
                assertThat(it).isCompletedWithValue(Unit)
            }
        assertThat(sendFuture.single().isDone).isTrue

        assertThat(result).isNotNull
        with(result.second) {
            assertThat(this.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
            assertThat(this.value).isInstanceOf(RegistrationCommand::class.java)
            assertThat(this.value?.command).isInstanceOf(ProcessMemberVerificationResponse::class.java)
            with(this.value?.command as ProcessMemberVerificationResponse) {
                assertThat(this.verificationResponse).isEqualTo(verificationResponse)
            }
        }
    }

    @Test
    fun `membership p2p service reads sync requests from the p2p topic and puts them on a synchronisation topic for further processing`() {
        val groupId = UUID.randomUUID().toString()
        val source = createTestHoldingIdentity("O=Alice,C=GB,L=London", groupId)
        val destination = createTestHoldingIdentity("O=MGM,C=GB,L=London", groupId)
        val syncId = UUID.randomUUID().toString()
        val requestTimestamp = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        val byteBuffer = ByteBuffer.wrap("1234".toByteArray())
        val secureHash = SecureHash("algorithm", byteBuffer)
        val completableResult = CompletableFuture<SynchronisationCommand>()

        val syncRequestSubscription = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("membership_p2p_sync_test_receiver", SYNCHRONIZATION_TOPIC),
            getPubSubTestProcessor { v ->
                completableResult.complete(v as SynchronisationCommand)
            },
            messagingConfig = bootConfig
        ).also { it.start() }

        val messageHeader = buildAuthenticatedMessageHeader(
            destination.toAvro(),
            source.toAvro(),
            requestTimestamp,
            syncId
        )
        val syncRequest = MembershipSyncRequest(
            DistributionMetaData(
                syncId,
                requestTimestamp
            ),
            secureHash, BloomFilter(1, 1, 1, byteBuffer), secureHash, secureHash
        )

        val sendFuture = p2pSender.publish(
            listOf(
                buildAuthenticatedP2PRequest(
                    messageHeader,
                    ByteBuffer.wrap(syncRequestSerializer.serialize(syncRequest))
                )
            )
        )

        val result = assertDoesNotThrow {
            completableResult.getOrThrow(Duration.ofSeconds(5))
        }
        syncRequestSubscription.close()

        assertThat(sendFuture).hasSize(1)
            .allSatisfy {
                assertThat(it).isCompletedWithValue(Unit)
            }
        assertThat(sendFuture.single().isDone).isTrue

        assertThat(result).isNotNull
        with(result.command as ProcessSyncRequest) {
            assertThat(this.synchronisationMetaData.mgm).isEqualTo(destination.toAvro())
            assertThat(this.synchronisationMetaData.member).isEqualTo(source.toAvro())
            assertThat(this.syncRequest).isEqualTo(syncRequest)
        }
    }

    private fun buildAuthenticatedMessageHeader(
        destination: HoldingIdentity,
        source: HoldingIdentity,
        timestamp: Instant,
        id: String,
        filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE
    ) = AuthenticatedMessageHeader(
        destination,
        source,
        timestamp.plusMillis(300000L),
        id,
        null,
        MEMBERSHIP_P2P_SUBSYSTEM,
        filter
    )

    private fun buildUnauthenticatedP2PRequest(
        messageHeader: UnauthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, AppMessage> {
        return Record(
            Schemas.P2P.P2P_IN_TOPIC,
            UUID.randomUUID().toString(),
            AppMessage(
                UnauthenticatedMessage(
                    messageHeader,
                    payload
                )
            )
        )
    }

    private fun buildAuthenticatedP2PRequest(
        messageHeader: AuthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, AppMessage> {
        return Record(
            Schemas.P2P.P2P_IN_TOPIC,
            UUID.randomUUID().toString(),
            AppMessage(
                AuthenticatedMessage(
                    messageHeader,
                    payload
                )
            )
        )
    }

    private fun getTestStateAndEventProcessor(
        resultCollector: (RegistrationState?, Record<String, RegistrationCommand>) -> Unit
    ) = object : StateAndEventProcessor<String, RegistrationState, RegistrationCommand> {
        override fun onNext(
            state: RegistrationState?,
            event: Record<String, RegistrationCommand>
        ): StateAndEventProcessor.Response<RegistrationState> {
            resultCollector(state, event)
            return StateAndEventProcessor.Response(null, emptyList())
        }

        override val keyClass = String::class.java
        override val stateValueClass = RegistrationState::class.java
        override val eventValueClass = RegistrationCommand::class.java
    }

    private fun getPubSubTestProcessor(resultCollector: (Any) -> Unit) = object : PubSubProcessor<String, Any> {
        override fun onNext(
            event: Record<String, Any>
        ): CompletableFuture<Unit> {
            resultCollector(event.value!!)
            return CompletableFuture.completedFuture(Unit)
        }

        override val keyClass = String::class.java
        override val valueClass = Any::class.java
    }
}
