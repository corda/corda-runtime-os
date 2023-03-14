package net.corda.membership.impl.registration

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.parseSecureHash
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.UnauthenticatedRegistrationRequest
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.registration.dummy.TestCryptoOpsClient
import net.corda.membership.impl.registration.dummy.TestGroupPolicy
import net.corda.membership.impl.registration.dummy.TestGroupPolicyProvider
import net.corda.membership.impl.registration.dummy.TestGroupReaderProvider
import net.corda.membership.impl.registration.dummy.TestPlatformInfoProvider.Companion.TEST_ACTIVE_PLATFORM_VERSION
import net.corda.membership.impl.registration.dummy.TestPlatformInfoProvider.Companion.TEST_SOFTWARE_VERSION
import net.corda.membership.impl.registration.dummy.TestVirtualNodeInfoReadService
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.REGISTRATION_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.registration.RegistrationProxy
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.BOOT_MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig
import net.corda.test.util.eventually
import net.corda.test.util.lifecycle.usingLifecycle
import net.corda.utilities.concurrent.getOrThrow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

@ExtendWith(ServiceExtension::class, DBSetup::class)
class MemberRegistrationIntegrationTest {
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
        lateinit var serializationFactory: CordaAvroSerializationFactory

        @InjectService(timeout = 5000)
        lateinit var membershipGroupReaderProvider: TestGroupReaderProvider

        @InjectService(timeout = 5000)
        lateinit var groupPolicyProvider: TestGroupPolicyProvider

        @InjectService(timeout = 5000)
        lateinit var locallyHostedIdentitiesService: LocallyHostedIdentitiesService

        @InjectService(timeout = 5000)
        lateinit var registrationProxy: RegistrationProxy

        lateinit var keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList>
        lateinit var requestDeserializer: CordaAvroDeserializer<MembershipRegistrationRequest>
        lateinit var unauthRequestDeserializer: CordaAvroDeserializer<UnauthenticatedRegistrationRequest>

        @InjectService(timeout = 5000)
        lateinit var testVirtualNodeInfoReadService: TestVirtualNodeInfoReadService

        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val bootConfig = SmartConfigFactory.createWithoutSecurityServices()
            .create(
                ConfigFactory.parseString(
                    """
                ${BootConfig.INSTANCE_ID} = 1
                ${MessagingConfig.Bus.BUS_TYPE} = INMEMORY
                $BOOT_MAX_ALLOWED_MSG_SIZE = 1000000
                """
                )
            )
        const val messagingConf = """
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
        const val gatewayConfig = """
            sslConfig {
                tlsType: "ONE_WAY"
            }
        """
        val schemaVersion = ConfigurationSchemaVersion(1, 0)
        val memberName = MemberX500Name("Alice", "London", "GB")
        val mgmName = MemberX500Name("Corda MGM", "London", "GB")

        const val groupId = "dummy_group"
        const val URL_KEY = "corda.endpoints.0.connectionURL"
        const val URL_VALUE = "https://localhost:1080"
        const val PROTOCOL_KEY = "corda.endpoints.0.protocolVersion"
        const val PROTOCOL_VALUE = "1"
        const val CPI_VERSION = "1.1"
        const val CPI_SIGNER_HASH = "ALG:A1B2C3D4"
        const val CPI_NAME = "cpi-name"

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val coordinator = lifecycleCoordinatorFactory.createCoordinator<MemberRegistrationIntegrationTest> { e, c ->
                if (e is StartEvent) {
                    logger.info("Starting test coordinator")
                    c.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                            LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
                            LifecycleCoordinatorName.forComponent<RegistrationProxy>(),
                            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                        )
                    )
                } else if (e is RegistrationStatusChangeEvent) {
                    logger.info("Test coordinator is ${e.status}")
                    c.updateStatus(e.status)
                }
            }.also { it.start() }

            keyValuePairListDeserializer = serializationFactory.createAvroDeserializer({}, KeyValuePairList::class.java)
            requestDeserializer =
                serializationFactory.createAvroDeserializer({}, MembershipRegistrationRequest::class.java)
            unauthRequestDeserializer =
                serializationFactory.createAvroDeserializer({}, UnauthenticatedRegistrationRequest::class.java)

            setupConfig()
            groupPolicyProvider.start()
            registrationProxy.start()
            cryptoOpsClient.start()
            locallyHostedIdentitiesService.start()
            membershipGroupReaderProvider.start()

            configurationReadService.bootstrapConfig(bootConfig)


            testVirtualNodeInfoReadService.putTestVirtualNodeInfo(
                VirtualNodeInfo(
                    holdingIdentity = HoldingIdentity(memberName, groupId),
                    cpiIdentifier = CpiIdentifier(CPI_NAME, CPI_VERSION, parseSecureHash(CPI_SIGNER_HASH)),
                    cryptoDmlConnectionId = UUID.randomUUID(),
                    uniquenessDmlConnectionId = UUID.randomUUID(),
                    vaultDmlConnectionId = UUID.randomUUID(),
                    timestamp = Instant.ofEpochSecond(1)
                )
            )

            eventually {
                logger.info("Waiting for required services to start...")
                assertThat(coordinator.status).isEqualTo(LifecycleStatus.UP)
                logger.info("Required services started.")
            }
        }

        fun setupConfig() {
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
            publisher.publish(
                listOf(
                    Record(
                        Schemas.Config.CONFIG_TOPIC,
                        ConfigKeys.P2P_GATEWAY_CONFIG,
                        Configuration(gatewayConfig, gatewayConfig, 0, schemaVersion)
                    )
                )
            )
            configurationReadService.start()
            configurationReadService.bootstrapConfig(bootConfig)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            configurationReadService.stop()
        }
    }

    @Test
    fun `dynamic member registration service publishes unauthenticated message to be sent to the MGM`() {
        groupPolicyProvider.putGroupPolicy(TestGroupPolicy())

        val member = HoldingIdentity(memberName, groupId)
        val context = buildTestContext(member)
        val completableResult = CompletableFuture<Pair<String, AppMessage>>()
        // Set up subscription to gather results of processing p2p message
        val registrationRequestSubscription = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("membership_p2p_test_receiver", Schemas.P2P.P2P_OUT_TOPIC),
            getTestProcessor { s, e ->
                completableResult.complete(Pair(s, e))
            },
            messagingConfig = bootConfig
        ).also { it.start() }

        registrationProxy.usingLifecycle {
            it.register(UUID.randomUUID(), member, context)
        }

        // Wait for latch to countdown, so we know when processing has completed and results have been collected
        val result = assertDoesNotThrow {
            completableResult.getOrThrow(Duration.ofSeconds(5))
        }
        registrationRequestSubscription.close()

        // Assert results
        assertSoftly {
            it.assertThat(result).isNotNull
            it.assertThat(result?.first)
                .isNotNull
                .isEqualTo(member.shortHash.value)
            it.assertThat(result?.second)
                .isNotNull
                .isInstanceOf(AppMessage::class.java)

            with(result!!.second["message"] as UnauthenticatedMessage) {
                it.assertThat(this.header.destination.x500Name).isEqualTo(mgmName.toString())
                it.assertThat(this.header.destination.groupId).isEqualTo(groupId)
                it.assertThat(this.header.source.x500Name).isEqualTo(memberName.toString())
                it.assertThat(this.header.source.groupId).isEqualTo(groupId)

                val deserializedUnauthenticatedRegistrationRequest =
                    unauthRequestDeserializer.deserialize(payload.array())!!
                val deserializedContext =
                    requestDeserializer.deserialize(deserializedUnauthenticatedRegistrationRequest.payload.array())!!
                        .run { keyValuePairListDeserializer.deserialize(memberContext.array())!! }

                with(deserializedContext.items) {
                    fun getValue(key: String) = first { pair -> pair.key == key }.value

                    it.assertThat(getValue(URL_KEY)).isEqualTo(URL_VALUE)
                    it.assertThat(getValue(PROTOCOL_KEY)).isEqualTo(PROTOCOL_VALUE)
                    it.assertThat(getValue(PARTY_NAME)).isEqualTo(memberName.toString())
                    it.assertThat(getValue(GROUP_ID)).isEqualTo(groupId)
                    it.assertThat(getValue(MEMBER_CPI_NAME)).isEqualTo(CPI_NAME)
                    it.assertThat(getValue(MEMBER_CPI_VERSION)).isEqualTo(CPI_VERSION)
                    it.assertThat(getValue(MEMBER_CPI_SIGNER_HASH)).isEqualTo(CPI_SIGNER_HASH)
                    it.assertThat(getValue(PLATFORM_VERSION)).isEqualTo(TEST_ACTIVE_PLATFORM_VERSION.toString())
                    it.assertThat(getValue(SOFTWARE_VERSION)).isEqualTo(TEST_SOFTWARE_VERSION)

                    with(map { pair -> pair.key }) {
                        it.assertThat(contains(String.format(LEDGER_KEYS_KEY, 0))).isTrue
                        it.assertThat(contains(String.format(LEDGER_KEY_HASHES_KEY, 0))).isTrue
                        it.assertThat(contains(PARTY_SESSION_KEY)).isTrue
                        it.assertThat(contains(SESSION_KEY_HASH)).isTrue
                        it.assertThat(contains(REGISTRATION_ID)).isTrue
                    }

                    assertDoesNotThrow {
                        UUID.fromString(getValue(REGISTRATION_ID))
                    }
                }
            }
        }
    }

    private fun buildTestContext(member: HoldingIdentity): Map<String, String> {
        val sessionKeyId =
            cryptoOpsClient.generateKeyPair(
                member.shortHash.value,
                "SESSION_INIT",
                member.shortHash.value + "session",
                ECDSA_SECP256R1_CODE_NAME
            )
                .publicKeyId()
        val ledgerKeyId =
            cryptoOpsClient.generateKeyPair(
                member.shortHash.value,
                "LEDGER",
                member.shortHash.value + "ledger",
                ECDSA_SECP256R1_CODE_NAME
            )
                .publicKeyId()
        return mapOf(
            "corda.session.key.id" to sessionKeyId,
            "corda.session.key.signature.spec" to SignatureSpec.ECDSA_SHA512.signatureName,
            URL_KEY to URL_VALUE,
            PROTOCOL_KEY to PROTOCOL_VALUE,
            "corda.ledger.keys.0.id" to ledgerKeyId,
            "corda.ledger.keys.0.signature.spec" to SignatureSpec.ECDSA_SHA512.signatureName,
        )
    }

    private fun getTestProcessor(resultCollector: (String, AppMessage) -> Unit): PubSubProcessor<String, AppMessage> {
        class TestProcessor : PubSubProcessor<String, AppMessage> {
            override fun onNext(
                event: Record<String, AppMessage>
            ): CompletableFuture<Unit> {
                resultCollector(event.key, event.value!!)
                return CompletableFuture.completedFuture(Unit)
            }

            override val keyClass = String::class.java
            override val valueClass = AppMessage::class.java
        }
        return TestProcessor()
    }
}
