package net.corda.crypto.persistence

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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import javax.persistence.EntityManagerFactory

class TestCryptoRepositoryFactoryImpl {
    @Test
    fun `DML to Corda crypto DB for Crypto tenant and P2P`() {
        val entityManagerFactory = mock<EntityManagerFactory>()
        val dbConnectionManager = mock<DbConnectionManager> {
            on { getOrCreateEntityManagerFactory(any(), any()) } doReturn entityManagerFactory
        }
        val cut = CryptoRepositoryFactoryImpl(dbConnectionManager, mock(), mock())
        val repo = cut.create(CryptoTenants.CRYPTO)
        assertThat(repo::class.simpleName).isEqualTo("V1CryptoRepositoryImpl")
        verify(dbConnectionManager).getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        verifyNoMoreInteractions(dbConnectionManager)
        cut.create(CryptoTenants.P2P)
        verify(dbConnectionManager, times(2)).getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        verifyNoMoreInteractions(dbConnectionManager)
        repo.close()
        verify(entityManagerFactory, times(0)).close()
    }

    @Test
    fun `Fresh database connections for virtual node and P2P`() {
        val sharedEntityManagerFactory = mock<EntityManagerFactory>()
        val ownedEntityManagerFactory = mock<EntityManagerFactory>()
        val virtualNodeInfo = mock<VirtualNodeInfo> {
            on { cryptoDmlConnectionId } doReturn mock()
        }
        val dbConnectionManager = mock<DbConnectionManager> {
            on { getOrCreateEntityManagerFactory(any(), any()) } doReturn mock()
            on { createEntityManagerFactory(any(), any()) } doReturn ownedEntityManagerFactory
        }
        val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
            on { getByHoldingIdentityShortHash(any()) } doReturn virtualNodeInfo
        }
        val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
            on { get(any()) } doReturn mock()
        }
        val cut = CryptoRepositoryFactoryImpl(dbConnectionManager, jpaEntitiesRegistry, virtualNodeInfoReadService)
        verifyNoMoreInteractions(dbConnectionManager)
        cut.create(CryptoTenants.P2P).use {
            verify(sharedEntityManagerFactory, times(0)).close()
        }
        verify(dbConnectionManager).getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        verifyNoMoreInteractions(dbConnectionManager)
        cut.create("123456789012").use {
            verify(ownedEntityManagerFactory, times(0)).close()
        } // try shorter, ShortHash bombs
        verify(dbConnectionManager).createEntityManagerFactory(any(), any())
        verifyNoMoreInteractions(dbConnectionManager)
        verify(sharedEntityManagerFactory, times(0)).close()
        verify(ownedEntityManagerFactory, times(1)).close()
    }
}