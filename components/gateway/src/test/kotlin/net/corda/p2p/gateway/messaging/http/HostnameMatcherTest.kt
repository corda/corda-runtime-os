@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package net.corda.p2p.gateway.messaging.http

import net.corda.nodeapi.internal.crypto.getX509Certificate
import net.corda.p2p.NetworkType
import net.corda.v5.application.identity.CordaX500Name
import org.apache.commons.validator.routines.DomainValidator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sun.security.util.HostnameChecker
import sun.security.util.HostnameChecker.TYPE_TLS
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
        assertTrue(matcher.matches(SNIHostName("2001:db8:3333:4444:cccc:dddd:eeee:ffff"))) // regular IPv6
        assertTrue(matcher.matches(SNIHostName("2001:0db8:3c4d:0015:0000:0000:1a2f:1a2b"))) // regular IPv6 which can be abbreviated
        assertTrue(matcher.matches(SNIHostName("2001:db8:3c4d:15::1a2f:1a2b"))) // abbreviated IPv6
        assertTrue(matcher.matches(SNIHostName("2001:db8:3c4d:15:0:0:1a2f:1a2b"))) // another abbreviation of the above IPv6 address

        // Certificate tool wouldn't allow using a DNS name with only part of a group wildcarded, even though the RFC allows it
        // We test this match using the direct HostnameMatcher method
        assertTrue(matcher.matchWithWildcard("alice.test.net", "al*.test.net"))

        // Use of wildcard
        assertTrue(matcher.illegalWildcard("*"))
        assertTrue(matcher.illegalWildcard("*."))
//        assertTrue(matcher.illegalWildcard("*.*.net")) // Should this be legal?
//        assertTrue(matcher.illegalWildcard("www.*.foo.bar.net")) // Should this be legal?
        assertTrue(matcher.illegalWildcard("www.*foo.bar.net"))
        assertTrue(matcher.illegalWildcard("www.f*o.bar.net"))
        assertTrue(matcher.illegalWildcard("www.foo.*"))
        assertTrue(matcher.illegalWildcard("www.foo.ne*"))
        assertTrue(matcher.illegalWildcard("www.foo.*et"))
    }
}