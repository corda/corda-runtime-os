package net.corda.utils.security.provider

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DelegatedSignatureProviderTest {
    private val provider = DelegatedSignatureProvider()

    @Test
    fun `provider supports RSASSA-PSS signature`() {
        assertThat(provider.getService("Signature", "RSASSA-PSS")).isNotNull
    }

    @Test
    fun `provider supports EC signature`() {
        assertThat(
            provider.getService(
                "Signature",
                DelegatedSigningService.Hash.SHA384.ecName
            )
        ).isNotNull
    }

    @Test
    fun `provider AlgorithmParameters EC is correct`() {
        assertThat(
            provider.getProperty("AlgorithmParameters.EC")
        ).isEqualTo("sun.security.util.ECParameters")
    }

    @Test
    fun `provider service newInstance creates DelegatedSignature`() {
        val instance = provider.getService(
            "Signature",
            DelegatedSigningService.Hash.SHA384.ecName
        ).newInstance(null)

        assertThat(instance).isInstanceOf(DelegatedSignature::class.java)
    }
}
