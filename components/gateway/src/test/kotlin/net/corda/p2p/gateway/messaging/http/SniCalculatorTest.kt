package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.NetworkType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SniCalculatorTest {

    companion object {
        val SOURCE = "PartyA"
        const val MAX_SNI_SIZE = 255 //Under RCF-1035 the total sni can be at most 255 bytes
        const val MAX_SNI_LABEL_SIZE = 63 //Under RCF-1035 each label can be at most 63 bytes
        const val LABEL_DELIMITER = "."
    }

    @Test
    fun `sni is correctly calculated from a base url for a corda5 identity`() {
        val hostName = "mydepartment.mycorp.com"
        val address = "http://$hostName/"
        Assertions.assertEquals(
            hostName,
            SniCalculator.calculateSni(
                SOURCE,
                NetworkType.CORDA_5,
                address
            )
        )
    }

    @Test
    fun `sni is future proof for a corda5 identity`() {
        val hostName = "nodea.r3.com"
        val address = "amqp://$hostName:443/somepath"
        Assertions.assertEquals(
            hostName,
            SniCalculator.calculateSni(
                SOURCE,
                NetworkType.CORDA_5,
                address
            )
        )
    }

    @Test
    fun `sni is correctly calculated from an address for a corda5 identity`() {
        val ip = "10.0.0.5"
        val address = "http://$ip/"
        Assertions.assertEquals(
            ip,
            SniCalculator.calculateSni(
                SOURCE,
                NetworkType.CORDA_5,
                address
            )
        )
    }

    @Test
    fun `sni conforms to rcf1035 for a corda4 identity`() {
        val address = ""
        val sni = SniCalculator.calculateSni(SOURCE,
            NetworkType.CORDA_4, address)
        assertTrue(sni.length < MAX_SNI_SIZE)
        for (label in sni.split(LABEL_DELIMITER)) {
            assertTrue(label.length <= MAX_SNI_LABEL_SIZE)
        }
    }
}