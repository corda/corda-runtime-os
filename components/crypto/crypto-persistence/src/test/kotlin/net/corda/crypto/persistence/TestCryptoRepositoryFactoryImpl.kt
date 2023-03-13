package net.corda.crypto.persistence

import net.corda.crypto.core.CryptoTenants
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class TestCryptoRepositoryFactoryImpl {
    @Test
    fun `different DML for Crypto tenant`() {
        val dbConnectionManager = mock<DbConnectionManager>()
        val cut = CryptoRepositoryFactoryImpl(dbConnectionManager, mock(), mock(), mock())
        val repo = cut.create(CryptoTenants.CRYPTO)
        assertThat(repo::class.simpleName).isEqualTo("V1CryptoRepositoryImpl")
        verify(dbConnectionManager).getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        verifyNoMoreInteractions(dbConnectionManager)
    }
}