package net.corda.crypto.persistence

import net.corda.crypto.core.CryptoTenants
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TestCryptoRepositoryFactoryImpl {
    @Test
    fun `go go go`() {
        val cut = CryptoRepositoryFactoryImpl(mock(), mock(), mock(), mock())
        val repo = cut.create(CryptoTenants.CRYPTO)
        assertThat(repo::class.simpleName).isEqualTo("V1CryptoRepositoryImpl")
    }
}