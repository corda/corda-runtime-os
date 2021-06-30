package net.corda.p2p.linkmanager.messaging

import net.corda.p2p.HoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SniCalculatorTest {

    companion object {
        val FIRST_SOURCE = HoldingIdentity("PartyA", "Group")
        val CLASSIC_SOURCE = HoldingIdentity("PartyA", null)
        const val MAX_SNI_SIZE = 255 //Under RCF-1035 the total sni can be at most 255 bytes
        const val MAX_SNI_LABEL_SIZE = 63 //Under RCF-1035 each label can be at most 63 bytes
        const val LABEL_DELIMITER = "."
    }

    @Test
    fun `sni is correctly calculated from a hostname for a corda5 identity`() {
        val hostName = "mydepartment.mycorp.com"
        val address = "http://$hostName/myendpoint"
        assertEquals(hostName, SniCalculator.calculateSni(FIRST_SOURCE, address))
    }

    @Test
    fun `sni is correctly calculated from a hostname and port for a corda5 identity`() {
        val hostName = "mydepartment.mycorp.com"
        val address = "http://$hostName:8080/myendpoint"
        assertEquals(hostName, SniCalculator.calculateSni(FIRST_SOURCE, address))
    }

    @Test
    fun `sni conforms to rcf1035 for a classic corda identity`() {
        val address = ""
        val sni = SniCalculator.calculateSni(CLASSIC_SOURCE, address)
        assertTrue(sni.length < MAX_SNI_SIZE)
        for (label in sni.split(LABEL_DELIMITER)) {
            assertTrue(label.length < MAX_SNI_LABEL_SIZE)
        }

    }
}