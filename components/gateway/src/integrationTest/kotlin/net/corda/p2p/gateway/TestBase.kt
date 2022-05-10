package net.corda.p2p.gateway

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.read.impl.ConfigurationReadServiceImpl
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.publish.impl.ConfigPublisherImpl
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.p2p.GatewayTlsCertificates
import net.corda.p2p.NetworkType
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.KeyStoreWithPassword
import net.corda.p2p.gateway.messaging.http.SniCalculator
import net.corda.p2p.gateway.messaging.http.TrustStoresMap
import net.corda.p2p.test.KeyPairEntry
import net.corda.p2p.test.TenantKeys
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.TestSchema
import net.corda.test.util.eventually
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.jce.PrincipalUtil
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.StringWriter
import java.net.BindException
import java.net.ServerSocket
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

open class TestBase {
    companion object {
        private val lastUsedPort = AtomicInteger(3000)
    }

    private fun readKeyStore(fileName: String, password: String = keystorePass): KeyStoreWithPassword {
        val keyStore = KeyStore.getInstance("JKS").also { keyStore ->
            javaClass.classLoader.getResource("$fileName.jks")!!.openStream().use {
                keyStore.load(it, password.toCharArray())
            }
        }
        return KeyStoreWithPassword(keyStore, password)
    }
    protected val truststoreCertificatePem by lazy {
        javaClass.classLoader.getResource("truststore/certificate.pem").readText()
    }
    private val c4TruststoreCertificatePem by lazy {
        javaClass.classLoader.getResource("truststore_c4/cordarootca.pem").readText()
    }
    protected val truststoreKeyStore by lazy {
        TrustStoresMap.TrustedCertificates(listOf(truststoreCertificatePem)).trustStore
    }

    protected val c4TruststoreKeyStore by lazy {
        TrustStoresMap.TrustedCertificates(listOf(c4TruststoreCertificatePem)).trustStore
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
    protected val partyASNI = SniCalculator.calculateSni("O=PartyA, L=London, C=GB", NetworkType.CORDA_4, "")
    protected val aliceKeyStore = readKeyStore("sslkeystore_alice")
    protected val aliceSslConfig = SslConfiguration(
        revocationCheck = RevocationConfig(RevocationConfigMode.OFF)
    )
    protected val bobKeyStore = readKeyStore("sslkeystore_bob")
    protected val bobSslConfig = SslConfiguration(
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL)
    )
    protected val chipKeyStore = readKeyStore("sslkeystore_chip")
    protected val chipSslConfig = SslConfiguration(
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL)
    )
    protected val daleKeyStore = readKeyStore("sslkeystore_dale")
    protected val daleSslConfig = SslConfiguration(
        revocationCheck = RevocationConfig(RevocationConfigMode.SOFT_FAIL)

    )
    protected val c4sslKeyStore = readKeyStore("sslkeystore_c4", keystorePass_c4)
    protected val c4sslConfig = SslConfiguration(
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
        private val messagingConfig = SmartConfigImpl.empty()

        val readerService by lazy {
            ConfigurationReadServiceImpl(
                coordinatorFactory!!,
                InMemSubscriptionFactory(configurationTopicService, rpcTopicService, coordinatorFactory!!)
            ).also {
                it.start()
                val bootstrapper = ConfigFactory.empty()
                it.bootstrapConfig(smartConfigFactory.create(bootstrapper))
            }
        }

        fun publishConfig(configuration: GatewayConfiguration) {
            val publishConfig = ConfigFactory.empty()
                .withValue("hostAddress", ConfigValueFactory.fromAnyRef(configuration.hostAddress))
                .withValue("hostPort", ConfigValueFactory.fromAnyRef(configuration.hostPort))
                .withValue("sslConfig.revocationCheck.mode", ConfigValueFactory.fromAnyRef(configuration.sslConfig.revocationCheck.mode.toString()))
                .withValue("connectionConfig.connectionIdleTimeout", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.connectionIdleTimeout))
                .withValue("connectionConfig.maxClientConnections", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.maxClientConnections))
                .withValue("connectionConfig.acquireTimeout", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.acquireTimeout))
                .withValue("connectionConfig.responseTimeout", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.responseTimeout))
                .withValue("connectionConfig.retryDelay", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.retryDelay))
                .withValue("connectionConfig.initialReconnectionDelay", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.initialReconnectionDelay))
                .withValue("connectionConfig.maximalReconnectionDelay", ConfigValueFactory.fromAnyRef(configuration.connectionConfig.maximalReconnectionDelay))
            CordaPublisherFactory(configurationTopicService, rpcTopicService, lifecycleCoordinatorFactory)
                .createPublisher(PublisherConfig(configPublisherClientId, false), messagingConfig).use { publisher ->
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
                .createPublisher(PublisherConfig(configPublisherClientId, false), messagingConfig)
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

    protected fun publishKeyStoreCertificatesAndKeys(publisher: Publisher, keyStoreWithPassword: KeyStoreWithPassword) {
        val records = keyStoreWithPassword.keyStore.aliases().toList().flatMap { alias ->
            val tenantId = "tenantId"
            val certificateChain = keyStoreWithPassword.keyStore.getCertificateChain(alias)
            val pems = certificateChain.map { certificate ->
                StringWriter().use { str ->
                    JcaPEMWriter(str).use { writer ->
                        writer.writeObject(certificate)
                    }
                    str.toString()
                }
            }
            val name = PrincipalUtil.getSubjectX509Principal(certificateChain.first() as X509Certificate).name
            val certificateRecord = Record(
                Schemas.P2P.GATEWAY_TLS_CERTIFICATES,
                name,
                GatewayTlsCertificates(tenantId, pems)
            )
            val privateKey = keyStoreWithPassword
                .keyStore
                .getKey(alias,
                    keyStoreWithPassword.password.toCharArray())
                .toPem()

            val keyPair = KeyPairEntry(
                privateKey,
            )
            val keysRecord = Record(
                TestSchema.CRYPTO_KEYS_TOPIC,
                alias,
                TenantKeys(
                    tenantId,
                    keyPair
                )
            )
            listOf(certificateRecord, keysRecord)
        }

        publisher.publish(records).forEach {
            it.join()
        }
    }
}
