package net.corda.crypto.persistence

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.core.CryptoTenants
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

fun makeConnectionCache(): Cache<String, EntityManagerFactory> = CacheFactoryImpl().build(
    "connection cache", Caffeine.newBuilder()
        .expireAfterAccess(24 * 60, TimeUnit.MINUTES)
        .maximumSize(100)
)

class TestCryptoRepositoryFactoryImpl {
    @Test
    fun `DML to Corda crypto DB for Crypto tenant and P2P`() {
        val dbConnectionManager = mock<DbConnectionManager> {
            on { getOrCreateEntityManagerFactory(any(), any()) } doReturn mock()
        }
        val cache = makeConnectionCache()
        val cut = CryptoRepositoryFactoryImpl(dbConnectionManager, mock(), mock(), cache)
        val repo = cut.create(CryptoTenants.CRYPTO)
        assertThat(repo::class.simpleName).isEqualTo("V1CryptoRepositoryImpl")
        verify(dbConnectionManager).getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        verifyNoMoreInteractions(dbConnectionManager)
        cut.create(CryptoTenants.P2P)
        verifyNoMoreInteractions(dbConnectionManager)
    }

    @Test
    fun `Fresh database connections for virtual node and P2P`() {
        val virtualNodeInfo = mock<VirtualNodeInfo> {
            on { cryptoDmlConnectionId } doReturn mock()
        }
        val dbConnectionManager = mock<DbConnectionManager> {
            on { getOrCreateEntityManagerFactory(any(), any()) } doReturn mock()
            on { createEntityManagerFactory(any(), any()) } doReturn mock()
        }
        val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
            on { getByHoldingIdentityShortHash(any()) } doReturn virtualNodeInfo
        }
        val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
            on { get(any()) } doReturn mock()
        }
        val cache = makeConnectionCache()
        val cut =
            CryptoRepositoryFactoryImpl(dbConnectionManager, jpaEntitiesRegistry, virtualNodeInfoReadService, cache)
        verifyNoMoreInteractions(dbConnectionManager)
        cut.create(CryptoTenants.P2P)
        verify(dbConnectionManager).getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        verifyNoMoreInteractions(dbConnectionManager)
        cut.create("123456789012") // try shorter, ShortHash bombs
        verify(dbConnectionManager).createEntityManagerFactory(any(), any())
        verifyNoMoreInteractions(dbConnectionManager)
    }
}