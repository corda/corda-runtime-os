package net.corda.membership.impl.p2p

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.StartRegistration
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.state.RegistrationState
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.impl.p2p.MembershipP2PProcessor.Companion.MEMBERSHIP_P2P_SUBSYSTEM
import net.corda.membership.p2p.MembershipP2PReadService
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.test.util.eventually
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture

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
        lateinit var membershipP2PReadService: MembershipP2PReadService

        @InjectService
        lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

        val logger = contextLogger()

        const val MEMBER_CONTEXT_KEY = "key"
        const val MEMBER_CONTEXT_VALUE = "value"

        val bootConfig = SmartConfigFactory.create(ConfigFactory.empty())
            .create(
                ConfigFactory.parseString(
                    """
                $INSTANCE_ID = 1
                $BUS_TYPE = INMEMORY
                """
                )
            )
        private const val messagingConf = """
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
        private val schemaVersion = ConfigurationSchemaVersion(1, 0)

        lateinit var p2pSender: Publisher
        lateinit var registrationRequestSerializer: CordaAvroSerializer<MembershipRegistrationRequest>
        lateinit var keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList>
        lateinit var keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList>

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val coordinator = lifecycleCoordinatorFactory.createCoordinator<MembershipP2PIntegrationTest> { e, c ->
                if (e is StartEvent) {
                    logger.info("Starting test coordinator")
                    c.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                            LifecycleCoordinatorName.forComponent<MembershipP2PReadService>()
                        )
                    )
                } else if (e is RegistrationStatusChangeEvent) {
                    logger.info("Test coordinator is ${e.status}")
                    c.updateStatus(e.status)
                }
            }.also { it.start() }

            registrationRequestSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
            keyValuePairListSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
            keyValuePairListDeserializer =
                cordaAvroSerializationFactory.createAvroDeserializer({}, KeyValuePairList::class.java)

            setupConfig()
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

        private fun setupConfig() {
            val publisher = publisherFactory.createPublisher(PublisherConfig("clientId"), bootConfig)
            publisher.publish(
                listOf(
                    Record(
                        Schemas.Config.CONFIG_TOPIC,
                        ConfigKeys.MESSAGING_CONFIG,
                        Configuration(messagingConf, 0, schemaVersion)
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
        val source = MemberX500Name.parse("O=Alice,C=GB,L=London").toString()
        val sourceHoldingIdentity = net.corda.virtualnode.HoldingIdentity(source, groupId)
        val destination = MemberX500Name.parse("O=MGM,C=GB,L=London").toString()
        val registrationId = UUID.randomUUID().toString()
        val fakeKey = "fakeKey"
        val fakeSig = "fakeSig"
        val completableResult = CompletableFuture<Pair<RegistrationState?, Record<String, RegistrationCommand>>>()

        // Set up subscription to gather results of processing p2p message
        val registrationRequestSubscription = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("membership_p2p_test_receiver", REGISTRATION_COMMAND_TOPIC),
            getTestProcessor { s, e ->
                completableResult.complete(Pair(s, e))
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
            HoldingIdentity(destination, groupId),
            HoldingIdentity(source, groupId),
            MEMBERSHIP_P2P_SUBSYSTEM
        )
        val message = MembershipRegistrationRequest(
            registrationId,
            ByteBuffer.wrap(keyValuePairListSerializer.serialize(memberContext)),
            fakeSigWithKey
        )

        // Publish P2P message requesting registration
        val sendFuture = p2pSender.publish(
            listOf(
                buildUnauthenticatedP2PRequest(
                    messageHeader,
                    ByteBuffer.wrap(registrationRequestSerializer.serialize(message))
                )
            )
        )

        // Wait for latch to countdown so we know when processing has completed and results have been collected
        val result = assertDoesNotThrow {
            completableResult.getOrThrow(Duration.ofSeconds(5))
        }
        registrationRequestSubscription.close()
        p2pSender.close()

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
            assertThat(key).isEqualTo(sourceHoldingIdentity.id)
            assertThat(value)
                .isNotNull
                .isInstanceOf(RegistrationCommand::class.java)
            assertThat(value!!.command)
                .isNotNull
                .isInstanceOf(StartRegistration::class.java)

            with(value!!.command as StartRegistration) {
                assertThat(this.destination.x500Name).isEqualTo(destination)
                assertThat(this.destination.groupId).isEqualTo(groupId)
                assertThat(this.source.x500Name).isEqualTo(source)
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

    private fun getTestProcessor(resultCollector: (RegistrationState?, Record<String, RegistrationCommand>) -> Unit): StateAndEventProcessor<String, RegistrationState, RegistrationCommand> {
        class TestProcessor : StateAndEventProcessor<String, RegistrationState, RegistrationCommand> {
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
        return TestProcessor()
    }
}