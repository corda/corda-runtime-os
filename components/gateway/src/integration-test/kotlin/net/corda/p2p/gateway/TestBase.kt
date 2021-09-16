package net.corda.p2p.gateway

import net.corda.p2p.NetworkType
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.SniCalculator
import org.bouncycastle.asn1.x500.X500Name
import java.io.FileInputStream
import java.security.KeyStore

open class TestBase {
    private fun readKeyStore(fileName: String, password: String): KeyStore {
        return KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("$fileName.jks")!!.file), password.toCharArray())
        }
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
        keyStore = readKeyStore("sslkeystore_alice", keystorePass),
        trustStorePassword = truststorePass,
        trustStore = readKeyStore("truststore", truststorePass),
        revocationCheck = RevocationConfig(RevocationConfigMode.OFF)

    )
    protected val bobSslConfig = SslConfiguration(
        keyStorePassword = keystorePass,
        keyStore = readKeyStore("sslkeystore_bob", keystorePass),
        trustStorePassword = truststorePass,
        trustStore = readKeyStore("truststore", truststorePass),
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL)

    )
    protected val chipSslConfig = SslConfiguration(
        keyStorePassword = keystorePass,
        keyStore = readKeyStore("sslkeystore_chip", keystorePass),
        trustStorePassword = truststorePass,
        trustStore = readKeyStore("truststore", truststorePass),
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL)

    )
    protected val daleSslConfig = SslConfiguration(
        keyStorePassword = keystorePass,
        keyStore = readKeyStore("sslkeystore_dale", keystorePass),
        trustStorePassword = truststorePass,
        trustStore = readKeyStore("truststore", truststorePass),
        revocationCheck = RevocationConfig(RevocationConfigMode.SOFT_FAIL)

    )
    protected val c4sslConfig = SslConfiguration(
        keyStorePassword = keystorePass_c4,
        keyStore = readKeyStore("sslkeystore_c4", keystorePass_c4),
        trustStorePassword = truststorePass_c4,
        trustStore = readKeyStore("truststore_c4", truststorePass_c4),
        revocationCheck = RevocationConfig(RevocationConfigMode.OFF)

    )
}
