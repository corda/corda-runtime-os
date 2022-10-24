package net.corda.membership.certificates

import net.corda.data.certificates.CertificateType
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.membership.certificates.CertificateUsage.Companion.fromAvro
import net.corda.membership.certificates.CertificateUsage.Companion.publicName
import net.corda.virtualnode.ShortHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CertificateUsageTest {
    @Test
    fun `public name return the correct name`() {
        assertThat(CertificateType.CODE_SIGNER.publicName).isEqualTo("code-signer")
    }

    @Test
    fun `fromString return type if the string is one of the type public name`() {
        assertThat(CertificateUsage.fromString("code-signer"))
            .isEqualTo(CertificateUsage.Type(CertificateType.CODE_SIGNER))
    }

    @Test
    fun `fromString return type if the string is one of the type name`() {
        assertThat(CertificateUsage.fromString("CODE_SIGNER"))
            .isEqualTo(CertificateUsage.Type(CertificateType.CODE_SIGNER))
    }

    @Test
    fun `fromString return holding identity if the string is an holding identity`() {
        assertThat(CertificateUsage.fromString("001122334455"))
            .isEqualTo(CertificateUsage.HoldingIdentityId(ShortHash.of("001122334455")))
    }

    @Test
    fun `fromAvro return type if the usage is a type`() {
        val request = CertificateRpcRequest()
        request.usage = CertificateType.P2P

        assertThat(fromAvro(request))
            .isEqualTo(CertificateUsage.Type(CertificateType.P2P))
    }

    @Test
    fun `fromAvro return identity if the usage is a string`() {
        val request = CertificateRpcRequest()
        request.usage = "001122334455"

        assertThat(fromAvro(request))
            .isEqualTo(CertificateUsage.HoldingIdentityId(ShortHash.of("001122334455")))
    }

    @Test
    fun `fromAvro return null if the usage is anything else`() {
        val request = CertificateRpcRequest()

        assertThat(fromAvro(request))
            .isNull()
    }

    @Test
    fun `fromAvro build type`() {
        assertThat(CertificateType.P2P.fromAvro)
            .isEqualTo(CertificateUsage.Type(CertificateType.P2P))
    }

    @Test
    fun `asAvro return the short hash as string`() {
        assertThat(CertificateUsage.fromString("001122334455").asAvro)
            .isEqualTo("001122334455")
    }

    @Test
    fun `asAvro return the type as type`() {
        assertThat(CertificateType.RPC_API.fromAvro.asAvro)
            .isEqualTo(CertificateType.RPC_API)
    }
}
