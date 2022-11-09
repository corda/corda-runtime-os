package net.corda.membership.certificates

import net.corda.data.certificates.CertificateUsage
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CertificateUsageUtilsTest {
    @Test
    fun `public name return the correct name`() {
        assertThat(CertificateUsage.CODE_SIGNER.publicName).isEqualTo("code-signer")
    }
}
