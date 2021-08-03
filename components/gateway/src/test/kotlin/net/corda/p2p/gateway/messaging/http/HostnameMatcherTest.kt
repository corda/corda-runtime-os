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
        // Because the tool used to create this certificate (tinycert.org) performs validations over subject alt names,
        // only happy paths can be tested using the certificate as input
        val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("sslkeystore_c5.jks")!!.file), "password".toCharArray())
        }
        val matcher = HostnameMatcher(keyStore)
        assertFalse(matcher.matches(SNIHostName("alice.net")))
        assertTrue(matcher.matches(SNIHostName("www.TesT.com")))
        assertTrue(matcher.matches(SNIHostName("www.test.co.uk")))
        assertTrue(matcher.matches(SNIHostName("alice.test.net"))) // matches with *.test.net
        assertTrue(matcher.matches(SNIHostName("bob.test.net"))) // matches with *.test.net
        assertTrue(matcher.matches(SNIHostName("10.11.12.13")))
        assertTrue(matcher.matches(SNIHostName("Test2"))) // matches the CN in the subject name

        // Certificate tool wouldn't allow using a DNS name with only part of a group wildcarded, even though the RFC allows it
        // We test this match using the direct HostnameMatcher method
        assertTrue(matcher.matchWithWildcard("alice.test.net", "al*.test.net"))

        // Use of wildcard
        assertTrue(matcher.illegalWildcard("*"))
        assertTrue(matcher.illegalWildcard("*."))
        assertTrue(matcher.illegalWildcard("*.*.net"))
        assertTrue(matcher.illegalWildcard("www.*.foo.bar.net"))
        assertTrue(matcher.illegalWildcard("www.*foo.bar.net"))
        assertTrue(matcher.illegalWildcard("www.f*o.bar.net"))
        assertTrue(matcher.illegalWildcard("www.foo.*"))
        assertTrue(matcher.illegalWildcard("www.foo.ne*"))
        assertTrue(matcher.illegalWildcard("www.foo.*et"))

        assertFalse(matcher.illegalWildcard("*.r3.com"))
        assertFalse(matcher.illegalWildcard("*.corda.r3.com"))
    }
}