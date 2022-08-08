package net.corda.membership.impl.registration

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigFactory
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
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.registration.RegistrationProxy
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig
import net.corda.test.util.eventually
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.publicKeyId
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.time.Duration
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
        lateinit var registrationProxy: RegistrationProxy

        lateinit var keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList>
        lateinit var requestDeserializer: CordaAvroDeserializer<MembershipRegistrationRequest>

        val logger = contextLogger()
        val bootConfig = SmartConfigFactory.create(ConfigFactory.empty())
            .create(
                ConfigFactory.parseString(
                    """
                ${BootConfig.INSTANCE_ID} = 1
                ${MessagingConfig.Bus.BUS_TYPE} = INMEMORY
                """
                )
            )
        const val messagingConf = """
            componentVersion="5.1"
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
        val schemaVersion = ConfigurationSchemaVersion(1, 0)
        val memberName = MemberX500Name("Alice", "London", "GB")
        val mgmName = MemberX500Name("Corda MGM", "London", "GB")

        const val groupId = "dummy_group"
        const val URL_KEY = "corda.endpoints.0.connectionURL"
        const val URL_VALUE = "localhost:1080"
        const val PROTOCOL_KEY = "corda.endpoints.0.protocolVersion"
        const val PROTOCOL_VALUE = "1"

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

            setupConfig()
            groupPolicyProvider.start()
            registrationProxy.start()
            cryptoOpsClient.start()
            membershipGroupReaderProvider.start()

            configurationReadService.bootstrapConfig(bootConfig)

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

        registrationProxy.use {
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
                .isEqualTo(member.shortHash)
            it.assertThat(result?.second)
                .isNotNull
                .isInstanceOf(AppMessage::class.java)
            with(result!!.second["message"] as UnauthenticatedMessage) {
                it.assertThat(this.header.destination.x500Name).isEqualTo(mgmName.toString())
                it.assertThat(this.header.destination.groupId).isEqualTo(groupId)
                it.assertThat(this.header.source.x500Name).isEqualTo(memberName.toString())
                it.assertThat(this.header.source.groupId).isEqualTo(groupId)
                val deserializedContext = requestDeserializer.deserialize(payload.array())!!.run {
                    keyValuePairListDeserializer.deserialize(memberContext.array())!!
                }
                with(deserializedContext.items) {
                    it.assertThat(first { pair -> pair.key == URL_KEY }.value).isEqualTo(URL_VALUE)
                    it.assertThat(first { pair -> pair.key == PROTOCOL_KEY }.value).isEqualTo(PROTOCOL_VALUE)
                    it.assertThat(first { pair -> pair.key == PARTY_NAME }.value).isEqualTo(memberName.toString())
                    it.assertThat(first { pair -> pair.key == GROUP_ID }.value).isEqualTo(groupId)
                    with (map { pair -> pair.key }) {
                        it.assertThat(contains(String.format(LEDGER_KEYS_KEY, 0))).isTrue
                        it.assertThat(contains(String.format(LEDGER_KEY_HASHES_KEY, 0))).isTrue
                        it.assertThat(contains(PARTY_SESSION_KEY)).isTrue
                        it.assertThat(contains(SESSION_KEY_HASH)).isTrue
                    }
                }
            }
        }
    }

    private fun buildTestContext(member: HoldingIdentity): Map<String, String> {
        val sessionKeyId =
            cryptoOpsClient.generateKeyPair(member.shortHash, "SESSION_INIT", member.shortHash + "session", "CORDA.ECDSA.SECP256R1")
                .publicKeyId()
        val ledgerKeyId =
            cryptoOpsClient.generateKeyPair(member.shortHash, "LEDGER", member.shortHash + "ledger", "CORDA.ECDSA.SECP256R1")
                .publicKeyId()
        return mapOf(
            "corda.session.key.id" to sessionKeyId,
            "corda.session.key.signature.spec" to "CORDA.ECDSA.SECP256R1",
            URL_KEY to URL_VALUE,
            PROTOCOL_KEY to PROTOCOL_VALUE,
            "corda.ledger.keys.0.id" to ledgerKeyId,
            "corda.ledger.keys.0.signature.spec" to "CORDA.ECDSA.SECP256R1",
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
