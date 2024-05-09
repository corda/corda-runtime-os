package net.corda.membership.certificates

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CertificateUtilsTest {
    private val chainCertificatesPems = (0..3).map { index ->
        this::class.java.getResource("/certificates/chain/certificate.$index.pem")!!.readText()
            .replace("\r", "")
            .replace("\n", System.lineSeparator())
    }

    @Test
    fun `toPemCertificateChain returns correct certificate chain`() {
        val certificateString = chainCertificatesPems.joinToString(separator = System.lineSeparator())

        val result = certificateString.toPemCertificateChain()

        assertThat(result).isEqualTo(chainCertificatesPems)
    }
}
