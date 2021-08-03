package net.corda.p2p.gateway

import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigImpl
import net.corda.p2p.gateway.messaging.SslConfiguration
import java.io.FileInputStream
import java.security.KeyStore

open class TestBase {
    protected val clientMessageContent = "PING"
    protected val serverResponseContent = "PONG"
    private val keystorePass = "password"
    private val truststorePass = "password"
    protected val aliceSNI = listOf("alice.net", "www.alice.net")
    protected val bobSNI = listOf("bob.net", "www.bob.net")
    protected val chipSNI = listOf("chip.net", "www.chip.net", "127.0.0.1")
    protected val daleSNI = listOf("dale.net", "www.dale.net", "127.0.0.1")
    protected val aliceSslConfig = object : SslConfiguration {
        override val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("sslkeystore_alice.jks")!!.file), keystorePass.toCharArray())
        }
        override val keyStorePassword: String = keystorePass
        override val trustStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("truststore.jks")!!.file), truststorePass.toCharArray())
        }
        override val trustStorePassword: String = truststorePass
        override val revocationCheck = RevocationConfigImpl(RevocationConfig.Mode.OFF)
    }
    protected val bobSslConfig = object : SslConfiguration {
        override val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("sslkeystore_bob.jks")!!.file), keystorePass.toCharArray())
        }
        override val keyStorePassword: String = keystorePass
        override val trustStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("truststore.jks")!!.file), truststorePass.toCharArray())
        }
        override val trustStorePassword: String = truststorePass
        override val revocationCheck = RevocationConfigImpl(RevocationConfig.Mode.HARD_FAIL)
    }
    protected val chipSslConfig = object : SslConfiguration {
        override val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("sslkeystore_chip.jks")!!.file), keystorePass.toCharArray())
        }
        override val keyStorePassword: String = keystorePass
        override val trustStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("truststore.jks")!!.file), truststorePass.toCharArray())
        }
        override val trustStorePassword: String = truststorePass
        override val revocationCheck = RevocationConfigImpl(RevocationConfig.Mode.HARD_FAIL)
    }
    protected val daleSslConfig = object : SslConfiguration {
        override val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("sslkeystore_dale.jks")!!.file), keystorePass.toCharArray())
        }
        override val keyStorePassword: String = keystorePass
        override val trustStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("truststore.jks")!!.file), truststorePass.toCharArray())
        }
        override val trustStorePassword: String = truststorePass
        override val revocationCheck = RevocationConfigImpl(RevocationConfig.Mode.SOFT_FAIL)
    }
}