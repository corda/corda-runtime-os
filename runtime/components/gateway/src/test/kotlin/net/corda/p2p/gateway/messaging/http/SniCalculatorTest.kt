package net.corda.p2p.gateway.messaging.http

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import java.net.URI

class SniCalculatorTest {

    private companion object {
        const val SOURCE = "O=PartyA, L=London, C=GB"
        const val MAX_SNI_SIZE = 255 //Under RCF-1035 the total sni can be at most 255 bytes
        const val MAX_SNI_LABEL_SIZE = 63 //Under RCF-1035 each label can be at most 63 bytes
        const val LABEL_DELIMITER = "."
    }

    @Test
    fun `sni is correctly calculated from a base url for a corda5 identity`() {
        val hostName = "mydepartment.mycorp.com"
        val address = URI("https://$hostName:9955/")

        assertThat(SniCalculator.calculateCorda5Sni(address)).isEqualTo(hostName)
    }

    @Test
    fun `sni is correctly calculated if address contains IP`() {
        val ip = "10.0.0.5"
        val address = URI("https://$ip:4055/")

        assertThat(SniCalculator.calculateCorda5Sni(address)).isEqualTo(ip + SniCalculator.IP_SNI_SUFFIX)
    }


    @Test
    fun `sni conforms to rcf1035 for a corda4 identity`() {
        val sni = SniCalculator.calculateCorda4Sni(SOURCE)

        assertSoftly {
            assertThat(sni).hasSizeLessThanOrEqualTo(MAX_SNI_SIZE)
            assertThat(sni.split(LABEL_DELIMITER)).allSatisfy {
                assertThat(it).hasSizeLessThanOrEqualTo(MAX_SNI_LABEL_SIZE)
            }
        }
    }

    @Test
    fun `c4 style sni calculation gives same result regardless of order of subject name RDNs`() {
        val names = listOf(
            "O=PartyA, L=London, C=GB, CN=Alice",
            "CN=Alice, O=PartyA, C=GB, L=London",
            "C=GB , O=PartyA  , CN =Alice, L = London",
        )
        val snis = names.map {
            SniCalculator.calculateCorda4Sni(it)
        }.toSet()

        assertThat(snis).hasSize(1)
    }
}