package net.corda.p2p.gateway.messaging.http

import net.corda.testing.p2p.certificates.Certificates
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.KeyStore
import javax.net.ssl.SNIHostName
import javax.security.auth.x500.X500Principal

class HostnameMatcherTest {
    @Test
    fun `C4 SNI match`() {
        val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(Certificates.c4KeyStoreFile.openStream(), "cordacadevpass".toCharArray())
        }
        val x500Name = X500Name.getInstance(X500Principal("O=PartyA,L=London,C=GB").encoded)
        val calculatedSNI = SniCalculator.calculateCorda4Sni(x500Name.toString())
        val matcher = HostnameMatcher(keyStore)
        assertTrue(matcher.matches(SNIHostName(calculatedSNI)))
        assertFalse(matcher.matches(SNIHostName("PartyA.net")))

        // Invalid C4 style SNI - incorrect suffix
        assertFalse(matcher.matches(SNIHostName("b597e8858a2fa87424f5e8c39dc4f93c.p2p.corda.com")))
        // Invalid C4 style SNI - incorrect length
        assertFalse(matcher.matches(SNIHostName("b597e8858a2fa87424f5e8c39d.p2p.corda.net")))
        // Invalid C4 style SNI - invalid hash (not hex)
        assertFalse(matcher.matches(SNIHostName("n597q8858z2fm87424f5e8c39dc4f93c.p2p.corda.net")))
    }

    @Test
    fun `C5 SNI match with ip address`() {
        val ipAddress = "127.0.0.1"
        val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(Certificates.ipKeyStore.openStream(), "password".toCharArray())
        }
        val matcher = HostnameMatcher(keyStore)
        assertThat(matcher.matches(SNIHostName(ipAddress + SniCalculator.IP_SNI_SUFFIX))).isTrue
    }

    @Test
    fun `C5 SNI match`() {
        // Because the tool used to create this certificate (tinycert.org) performs validations over subject alt names,
        // only happy paths can be tested using the certificate as input
        val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(Certificates.c5KeyStoreFile.openStream(), "password".toCharArray())
        }
        val matcher = HostnameMatcher(keyStore)
        assertFalse(matcher.matches(SNIHostName("alice.net")))
        assertTrue(matcher.matches(SNIHostName("www.TesT.com")))
        assertTrue(matcher.matches(SNIHostName("www.test.co.uk")))
        assertTrue(matcher.matches(SNIHostName("alice.test.net"))) // matches with *.test.net
        assertTrue(matcher.matches(SNIHostName("bob.test.net"))) // matches with *.test.net
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
