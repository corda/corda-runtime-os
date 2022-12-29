package net.corda.p2p.gateway

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.read.impl.ConfigurationReadServiceImpl
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.merger.impl.ConfigMergerImpl
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorSchedulerFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messagebus.db.configuration.DbBusConfigMergerImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.p2p.GatewayTlsCertificates
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.http.KeyStoreWithPassword
import net.corda.p2p.gateway.messaging.http.SniCalculator
import net.corda.p2p.gateway.messaging.http.TrustStoresMap
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.testing.p2p.certificates.Certificates
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.jce.PrincipalUtil
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.StringWriter
import java.net.BindException
import java.net.ServerSocket
import java.net.URL
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random.Default.nextInt
import net.corda.p2p.gateway.messaging.TlsType

open class TestBase {
    companion object {
        private val lastUsedPort = AtomicInteger(3000)
    }

    private fun readKeyStore(url: URL?, password: String = keystorePass): KeyStoreWithPassword {
        val keyStore = KeyStore.getInstance("JKS").also { keyStore ->
            url!!.openStream().use {
                keyStore.load(it, password.toCharArray())
            }
        }
        return KeyStoreWithPassword(keyStore, password)
    }
    protected val truststoreCertificatePem by lazy {
        Certificates.truststoreCertificatePem.readText()
    }

    private val c4TruststoreCertificatePem by lazy {
        Certificates.c4TruststoreCertificatePem.readText()
    }
    protected val truststoreKeyStore by lazy {
        TrustStoresMap.TrustedCertificates(listOf(truststoreCertificatePem)).trustStore!!
    }

    protected val c4TruststoreKeyStore by lazy {
        TrustStoresMap.TrustedCertificates(listOf(c4TruststoreCertificatePem)).trustStore!!
    }

    protected fun getOpenPort(): Int {
        while (true) {
            try {
                ServerSocket(lastUsedPort.incrementAndGet()).use {
                    return it.localPort
                }
            } catch (e: BindException) {
                // Go to next port...
            }
        }
    }
    protected val clientMessageContent = "PING"
    protected val serverResponseContent = "PONG"
    protected val keystorePass = "password"

    protected val keystorePass_c4 = "cordacadevpass"
    protected val aliceSNI = listOf("alice.net", "www.alice.net")
    protected val bobSNI = listOf("bob.net", "www.bob.net")
    protected val partyAx500Name = X500Name("O=PartyA, L=London, C=GB")
    protected val partyASNI = SniCalculator.calculateCorda4Sni("O=PartyA, L=London, C=GB")
    protected val aliceKeyStore = readKeyStore(Certificates.aliceKeyStoreFile)
    protected val ipKeyStore = readKeyStore(Certificates.ipKeyStore)
    protected val aliceSslConfig = SslConfiguration(
        revocationCheck = RevocationConfig(RevocationConfigMode.OFF),
        tlsType = TlsType.ONE_WAY,
    )
    protected val bobKeyStore = readKeyStore(Certificates.bobKeyStoreFile)
    protected val bobSslConfig = SslConfiguration(
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL),
        tlsType = TlsType.ONE_WAY,
    )
    protected val chipKeyStore = readKeyStore(Certificates.chipKeyStoreFile)
    protected val chipSslConfig = SslConfiguration(
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL),
        tlsType = TlsType.ONE_WAY,
    )
    protected val daleKeyStore = readKeyStore(Certificates.daleKeyStoreFile)
    protected val daleSslConfig = SslConfiguration(
        revocationCheck = RevocationConfig(RevocationConfigMode.SOFT_FAIL),
        tlsType = TlsType.ONE_WAY,
    )
    protected val c4sslKeyStore = readKeyStore(Certificates.c4KeyStoreFile, keystorePass_c4)
    protected val c4sslConfig = SslConfiguration(
        revocationCheck = RevocationConfig(RevocationConfigMode.OFF),
        tlsType = TlsType.ONE_WAY,
    )

    protected val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())

    protected val lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl(), LifecycleCoordinatorSchedulerFactoryImpl())

    protected inner class ConfigPublisher(private var coordinatorFactory: LifecycleCoordinatorFactory? = null) {
        init {
            coordinatorFactory = coordinatorFactory ?: lifecycleCoordinatorFactory
        }
        private val configurationTopicService = TopicServiceImpl()
        private val rpcTopicService = RPCTopicServiceImpl()
        private val configPublisherClientId = "config.${UUID.randomUUID().toString().replace("-", "")}"
        private val messagingConfig = SmartConfigImpl.empty()
        private val configMerger = ConfigMergerImpl(DbBusConfigMergerImpl())

        val readerService by lazy {
            ConfigurationReadServiceImpl(
                coordinatorFactory!!,
                InMemSubscriptionFactory(configurationTopicService, rpcTopicService, coordinatorFactory!!),
                configMerger
            ).also {
                it.start()
                val bootstrapper = ConfigFactory.empty()
                    .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(nextInt()))
                    .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
                it.bootstrapConfig(smartConfigFactory.create(bootstrapper))
            }
        }

        private fun Publisher.publishGatewayConfig(config: Config) {
            val configSource = config.root().render(ConfigRenderOptions.concise())
            this.publish(listOf(Record(
                CONFIG_TOPIC,
                ConfigKeys.P2P_GATEWAY_CONFIG,
                Configuration(configSource, configSource, 0, ConfigurationSchemaVersion(1, 0))
            ))).forEach { it.get() }
        }

        fun publishConfig(configuration: GatewayConfiguration) {
            val publishConfig = ConfigFactory.empty()
                .withValue("hostAddress", ConfigValueFactory.fromAnyRef(configuration.hostAddress))
                .withValue("hostPort", ConfigValueFactory.fromAnyRef(configuration.hostPort))
                .withValue("urlPath", ConfigValueFactory.fromAnyRef(configuration.urlPath))
                .withValue("maxRequestSize", ConfigValueFactory.fromAnyRef(configuration.maxRequestSize))
                .withValue("sslConfig.revocationCheck.mode", ConfigValueFactory.fromAnyRef(configuration.sslConfig.revocationCheck.mode.toString()))
                .withValue("sslConfig.tlsType", ConfigValueFactory.fromAnyRef(configuration.sslConfig.tlsType.toString()))
                .withValue("connectionConfig.connectionIdleTimeout", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.connectionIdleTimeout))
                .withValue("connectionConfig.maxClientConnections", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.maxClientConnections))
                .withValue("connectionConfig.acquireTimeout", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.acquireTimeout))
                .withValue("connectionConfig.responseTimeout", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.responseTimeout))
                .withValue("connectionConfig.retryDelay", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.retryDelay))
                .withValue("connectionConfig.initialReconnectionDelay", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.initialReconnectionDelay))
                .withValue("connectionConfig.maxReconnectionDelay", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.maxReconnectionDelay))
            CordaPublisherFactory(configurationTopicService, rpcTopicService, lifecycleCoordinatorFactory)
                .createPublisher(PublisherConfig(configPublisherClientId, false), messagingConfig).use { publisher ->
                    publisher.publishGatewayConfig(publishConfig)
                }
        }
        fun publishBadConfig() {
            val publishConfig = ConfigFactory.empty()
                .withValue("hello", ConfigValueFactory.fromAnyRef("world"))
            CordaPublisherFactory(configurationTopicService, rpcTopicService, lifecycleCoordinatorFactory)
                .createPublisher(PublisherConfig(configPublisherClientId, false), messagingConfig)
                .use { publisher ->
                    publisher.publishGatewayConfig(publishConfig)
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

    fun HttpServer.startAndWaitForStarted() {
        this.start()
        eventually(duration = 20.seconds) {
            assertThat(this.isRunning).isTrue
        }
    }
}
