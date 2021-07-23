package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.NetworkType
import net.corda.v5.application.identity.CordaX500Name
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.SNIHostName

class HostnameMatcherTest {
    @Test
    fun `C4 SNI match`() {
        val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("sslkeystore_c4.jks")!!.file), "cordacadevpass".toCharArray())
        }
        val cordaX500Name = CordaX500Name.parse("O=PartyA,L=London,C=GB")
        val calculatedSNI = SniCalculator.calculateSni(cordaX500Name.toString(), NetworkType.CORDA_4, "")
        val matcher = HostnameMatcher(keyStore)
        assertTrue(matcher.matches(SNIHostName(calculatedSNI)))
        assertFalse(matcher.matches(SNIHostName("PartyA.net")))
    }

    @Test
    fun `C5 SNI match`() {
        val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("sslkeystore_c5.jks")!!.file), "password".toCharArray())
        }
        val matcher = HostnameMatcher(keyStore)
        assertTrue(matcher.matches(SNIHostName("chip.net")))
        assertTrue(matcher.matches(SNIHostName("www.chip.net")))
        assertTrue(matcher.matches(SNIHostName("127.0.0.1")))
        assertFalse(matcher.matches(SNIHostName("alice.net")))
    }
}