package net.corda.crypto.delegated.signing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class DelegatedKeystoreProviderTest {
    @Test
    fun `newInstance return a DelegatedKeystore`() {
        val delegateService = mock<DelegatedSigningService>()
        val provider = DelegatedKeystoreProvider()
        provider.putService("service", delegateService)
        val service = provider.getService("KeyStore", "service")

        val delegate = service.newInstance(null)

        assertThat(delegate).isInstanceOf(DelegatedKeystore::class.java)
    }
}
