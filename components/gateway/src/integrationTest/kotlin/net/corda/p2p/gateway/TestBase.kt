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
import net.corda.lifecycle.Resource
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorSchedulerFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messagebus.db.configuration.DbBusConfigMergerImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.EmulatorFactory
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.TlsType
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.http.KeyStoreWithPassword
import net.corda.p2p.gateway.messaging.http.SniCalculator
import net.corda.p2p.gateway.messaging.http.TrustStoresMap
import net.corda.schema.Schemas.Config.CONFIG_TOPIC
import net.corda.schema.configuration.BootConfig.BOOT_MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import net.corda.test.util.eventually
import net.corda.testing.p2p.certificates.Certificates
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.AfterEach
import java.net.BindException
import java.net.ServerSocket
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random.Default.nextInt

internal open class TestBase {
    private val toClose = ConcurrentLinkedQueue<Resource>()
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

    protected val truststoreWithRevocationCertificatePem by lazy {
        Certificates.truststoreCertificateWithRevocationPem.readText()
    }

    private val c4TruststoreCertificatePem by lazy {
        Certificates.c4TruststoreCertificatePem.readText()
    }
    internal val truststoreKeyStore by lazy {
        TrustStoresMap.TrustedCertificates(listOf(truststoreCertificatePem))
    }

    internal val truststoreKeyStoreWithRevocation by lazy {
        TrustStoresMap.TrustedCertificates(listOf(truststoreWithRevocationCertificatePem))
    }

    internal val c4TruststoreKeyStore by lazy {
        TrustStoresMap.TrustedCertificates(listOf(c4TruststoreCertificatePem))
    }

    protected fun getOpenPort(): Int {
        while (true) {
            try {
                ServerSocket(0).use {
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

    protected val keystorePassC4 = "cordacadevpass"
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
        revocationCheck = RevocationConfig(RevocationConfigMode.OFF),
        tlsType = TlsType.ONE_WAY,
    )
    protected val chipKeyStore = readKeyStore(Certificates.chipKeyStoreFile)
    protected val chipSslConfig = SslConfiguration(
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL),
        tlsType = TlsType.ONE_WAY,
    )
    protected val daleKeyStore = readKeyStore(Certificates.daleKeyStoreFile)
    protected val c4sslKeyStore = readKeyStore(Certificates.c4KeyStoreFile, keystorePassC4)
    protected val c4sslConfig = SslConfiguration(
        revocationCheck = RevocationConfig(RevocationConfigMode.OFF),
        tlsType = TlsType.ONE_WAY,
    )

    protected val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()

    protected val lifecycleCoordinatorFactory =
        LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl(), LifecycleCoordinatorSchedulerFactoryImpl())

    protected inner class ConfigPublisher(private var coordinatorFactory: LifecycleCoordinatorFactory? = null): Resource {
        init {
            coordinatorFactory = coordinatorFactory ?: lifecycleCoordinatorFactory
        }
        private val emulator = EmulatorFactory.create(coordinatorFactory!!)
        private val configPublisherClientId = "config.${UUID.randomUUID().toString().replace("-", "")}"
        private val messagingConfig = SmartConfigImpl.empty()
        private val configMerger = ConfigMergerImpl(DbBusConfigMergerImpl())

        val readerService by lazy {
            ConfigurationReadServiceImpl(
                coordinatorFactory!!,
                emulator.subscriptionFactory,
                configMerger,
                AvroSchemaRegistryImpl(),
                emulator.publisherFactory,
            ).also {
                it.start()
                val bootstrapper = ConfigFactory.empty()
                    .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(nextInt()))
                    .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
                    .withValue(BOOT_MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(10000000))
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
            val servers = ConfigValueFactory.fromIterable(
                configuration.serversConfiguration.map {
                    mapOf(
                        "hostAddress" to it.hostAddress,
                        "hostPort" to it.hostPort,
                        "urlPath" to it.urlPaths.first(),
                    )
                }
            )
            val publishConfig = ConfigFactory.empty()
                .withValue("serversConfiguration",
                    servers)
                .withValue("maxRequestSize",
                    ConfigValueFactory.fromAnyRef(configuration.maxRequestSize))
                .withValue("sslConfig.revocationCheck.mode",
                    ConfigValueFactory.fromAnyRef(configuration.sslConfig.revocationCheck.mode.toString()))
                .withValue("sslConfig.tlsType",
                    ConfigValueFactory.fromAnyRef(configuration.sslConfig.tlsType.toString()))
                .withValue("connectionConfig.connectionIdleTimeout",
                    ConfigValueFactory.fromAnyRef(configuration.connectionConfig.connectionIdleTimeout))
                .withValue("connectionConfig.maxClientConnections",
                    ConfigValueFactory.fromAnyRef(configuration.connectionConfig.maxClientConnections))
                .withValue("connectionConfig.acquireTimeout",
                    ConfigValueFactory.fromAnyRef(configuration.connectionConfig.acquireTimeout))
                .withValue("connectionConfig.responseTimeout",
                    ConfigValueFactory.fromAnyRef(configuration.connectionConfig.responseTimeout))
                .withValue("connectionConfig.retryDelay",
                    ConfigValueFactory.fromAnyRef(configuration.connectionConfig.retryDelay))
                .withValue("connectionConfig.initialReconnectionDelay",
                    ConfigValueFactory.fromAnyRef(configuration.connectionConfig.initialReconnectionDelay))
                .withValue("connectionConfig.maxReconnectionDelay",
                    ConfigValueFactory.fromAnyRef(configuration.connectionConfig.maxReconnectionDelay))
            emulator.publisherFactory
                .createPublisher(PublisherConfig(configPublisherClientId, false), messagingConfig)
                .use { publisher ->
                    publisher.publishGatewayConfig(publishConfig)
                }
        }
        fun publishBadConfig() {
            val publishConfig = ConfigFactory.empty()
                .withValue("hello", ConfigValueFactory.fromAnyRef("world"))
            emulator.publisherFactory
                .createPublisher(PublisherConfig(configPublisherClientId, false), messagingConfig)
                .use { publisher ->
                    publisher.publishGatewayConfig(publishConfig)
                }
        }

        override fun close() {
            emulator.close()
        }
    }

    protected fun createConfigurationServiceFor(
        configuration: GatewayConfiguration,
        coordinatorFactory: LifecycleCoordinatorFactory? = null) : ConfigurationReadService {
        val publisher = ConfigPublisher(coordinatorFactory)
        keep(publisher)
        publisher.publishConfig(configuration)
        return publisher.readerService
    }

    protected fun keep(resource: Resource) {
        toClose.add(resource)
    }

    @AfterEach
    fun cleanUp() {
        toClose.forEach {
            it.close()
        }
        toClose.clear()
    }

    fun Lifecycle.startAndWaitForStarted() {
        this.start()
        eventually(duration = 20.seconds) {
            assertThat(this.isRunning).isTrue
        }
    }

    internal fun HttpServer.startAndWaitForStarted() {
        this.start()
        eventually(duration = 20.seconds) {
            assertThat(this.isRunning).isTrue
        }
    }
}
