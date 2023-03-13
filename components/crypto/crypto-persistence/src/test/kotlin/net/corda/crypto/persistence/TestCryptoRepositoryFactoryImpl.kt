package net.corda.crypto.persistence

import net.corda.crypto.core.CryptoTenants
import net.corda.db.connection.manager.DbConnectionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TestCryptoRepositoryFactoryImpl {
    @Test
    fun `different virtual nodes should have different crypto repositories`() {
        val dbConnectionManager = mock<DbConnectionManager>()
        val cut = CryptoRepositoryFactoryImpl(dbConnectionManager, mock(), mock(), mock())
        val repo = cut.create(CryptoTenants.CRYPTO)
        assertThat(repo::class.simpleName).isEqualTo("V1CryptoRepositoryImpl")
    }
}