package net.corda.p2p.gateway

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.read.impl.ConfigurationReadServiceImpl
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.publish.impl.ConfigPublisherImpl
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.p2p.NetworkType
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.SniCalculator
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.test.util.eventually
import net.corda.v5.base.util.seconds
import net.corda.v5.base.util.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import java.util.UUID

open class TestBase {
    private fun readKeyStore(fileName: String): ByteArray {
        return javaClass.classLoader.getResource("$fileName.jks").readBytes()
    }

    protected val clientMessageContent = "PING"
    protected val serverResponseContent = "PONG"
    private val keystorePass = "password"
    private val truststorePass = "password"
    private val keystorePass_c4 = "cordacadevpass"
    private val truststorePass_c4 = "trustpass"
    protected val aliceSNI = listOf("alice.net", "www.alice.net")
    protected val bobSNI = listOf("bob.net", "www.bob.net")
    protected val partyAx500Name = X500Name("O=PartyA, L=London, C=GB")
    protected val partyASNI = SniCalculator.calculateSni("O=PartyA, L=London, C=GB", NetworkType.CORDA_4, "")
    protected val aliceSslConfig = SslConfiguration(
        keyStorePassword = keystorePass,
        rawKeyStore = readKeyStore("sslkeystore_alice"),
        trustStorePassword = truststorePass,
        rawTrustStore = readKeyStore("truststore"),
        revocationCheck = RevocationConfig(RevocationConfigMode.OFF)
    )
    protected val bobSslConfig = SslConfiguration(
        keyStorePassword = keystorePass,
        rawKeyStore = readKeyStore("sslkeystore_bob"),
        trustStorePassword = truststorePass,
        rawTrustStore = readKeyStore("truststore"),
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL)

    )
    protected val chipSslConfig = SslConfiguration(
        keyStorePassword = keystorePass,
        rawKeyStore = readKeyStore("sslkeystore_chip"),
        trustStorePassword = truststorePass,
        rawTrustStore = readKeyStore("truststore"),
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL)

    )
    protected val daleSslConfig = SslConfiguration(
        keyStorePassword = keystorePass,
        rawKeyStore = readKeyStore("sslkeystore_dale"),
        trustStorePassword = truststorePass,
        rawTrustStore = readKeyStore("truststore"),
        revocationCheck = RevocationConfig(RevocationConfigMode.SOFT_FAIL)

    )
    protected val c4sslConfig = SslConfiguration(
        keyStorePassword = keystorePass_c4,
        rawKeyStore = readKeyStore("sslkeystore_c4"),
        trustStorePassword = truststorePass_c4,
        rawTrustStore = readKeyStore("truststore_c4"),
        revocationCheck = RevocationConfig(RevocationConfigMode.OFF)
    )

    protected val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())

    protected val lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())

    protected inner class ConfigPublisher(private var coordinatorFactory: LifecycleCoordinatorFactory? = null) {
        init {
            coordinatorFactory = coordinatorFactory ?: lifecycleCoordinatorFactory
        }
        private val configurationTopicService = TopicServiceImpl()
        private val rpcTopicService = RPCTopicServiceImpl()
        private val configPublisherClientId = "config.${UUID.randomUUID().toString().replace("-", "")}"

        val readerService by lazy {
            ConfigurationReadServiceImpl(
                coordinatorFactory!!,
                InMemSubscriptionFactory(configurationTopicService, rpcTopicService, coordinatorFactory!!)
            ).also {
                it.start()
                val bootstrapper = ConfigFactory.empty()
                it.bootstrapConfig(smartConfigFactory.create(bootstrapper))
                eventually {
                    assertThat(it.isRunning).isTrue
                }
            }
        }

        fun publishConfig(configuration: GatewayConfiguration) {
            val publishConfig = ConfigFactory.empty()
                .withValue("hostAddress", ConfigValueFactory.fromAnyRef(configuration.hostAddress))
                .withValue("hostPort", ConfigValueFactory.fromAnyRef(configuration.hostPort))
                .withValue("sslConfig.keyStorePassword", ConfigValueFactory.fromAnyRef(configuration.sslConfig.keyStorePassword))
                .withValue("sslConfig.keyStore", ConfigValueFactory.fromAnyRef(configuration.sslConfig.rawKeyStore.toBase64()))
                .withValue("sslConfig.trustStorePassword", ConfigValueFactory.fromAnyRef(configuration.sslConfig.trustStorePassword))
                .withValue("sslConfig.trustStore", ConfigValueFactory.fromAnyRef(configuration.sslConfig.rawTrustStore.toBase64()))
                .withValue("sslConfig.revocationCheck.mode", ConfigValueFactory.fromAnyRef(configuration.sslConfig.revocationCheck.mode.toString()))
                .withValue("connectionConfig.connectionIdleTimeout", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.connectionIdleTimeout))
                .withValue("connectionConfig.maxClientConnections", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.maxClientConnections))
                .withValue("connectionConfig.acquireTimeout", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.acquireTimeout))
                .withValue("connectionConfig.responseTimeout", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.responseTimeout))
                .withValue("connectionConfig.retryDelay", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.retryDelay))
            CordaPublisherFactory(configurationTopicService, rpcTopicService, lifecycleCoordinatorFactory).createPublisher(PublisherConfig((configPublisherClientId))).use { publisher ->
                val configurationPublisher = ConfigPublisherImpl(CONFIG_TOPIC, publisher)
                configurationPublisher.updateConfiguration(
                    CordaConfigurationKey(
                        "myKey",
                        CordaConfigurationVersion("p2p", 0, 1),
                        CordaConfigurationVersion("gateway", 0, 1)
                    ),
                    publishConfig
                )
            }
        }
        fun publishBadConfig() {
            val publishConfig = ConfigFactory.empty()
                .withValue("hello", ConfigValueFactory.fromAnyRef("world"))
            CordaPublisherFactory(configurationTopicService, rpcTopicService, lifecycleCoordinatorFactory)
                .createPublisher(PublisherConfig((configPublisherClientId)))
                .use { publisher ->
                    val configurationPublisher = ConfigPublisherImpl(CONFIG_TOPIC, publisher)
                    configurationPublisher.updateConfiguration(
                        CordaConfigurationKey(
                            "myKey",
                            CordaConfigurationVersion("p2p", 0, 1),
                            CordaConfigurationVersion("gateway", 0, 1)
                        ),
                        publishConfig
                    )
                }
        }
    }

    protected fun createConfigurationServiceFor(configuration: GatewayConfiguration, coordinatorFactory: LifecycleCoordinatorFactory? = null): ConfigurationReadService {
        val publisher = ConfigPublisher(coordinatorFactory)
        publisher.publishConfig(configuration)
        return publisher.readerService
    }

    fun Lifecycle.startAndWaitForStarted() {
        this.start()
        eventually(duration = 20.seconds) {
            assertThat(this.isRunning).isTrue
        }
    }
}
