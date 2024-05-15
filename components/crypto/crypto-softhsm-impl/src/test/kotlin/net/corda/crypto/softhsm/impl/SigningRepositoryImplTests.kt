package net.corda.crypto.softhsm.impl

import net.corda.crypto.core.ClusterCryptoDb
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.persistence.getEntityManagerFactory
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID
import javax.persistence.EntityManagerFactory

class SigningRepositoryImplTests {

    @Suppress("MaxLineLength")
    @Test
    fun `Appropriate use of DB connection manager that sets up DML connection when creating signing repository for Crypto tenant and P2P`() {
        // Arguably this is really testing getEntityManagerFactory so should be moved to a new test class
        val entityManagerFactory = mock<EntityManagerFactory>()
        val dbConnectionManager = mock<DbConnectionManager> {
            on { getOrCreateEntityManagerFactory(any<CordaDb>(), any()) } doReturn entityManagerFactory
        }
        val tenant = ClusterCryptoDb.CRYPTO_SCHEMA
        makeMockSigningRepository(tenant, dbConnectionManager).use { repo ->
            assertThat(repo::class.simpleName).isEqualTo("SigningRepositoryImpl")
            verify(dbConnectionManager).getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
            verifyNoMoreInteractions(dbConnectionManager)
            makeMockSigningRepository(CryptoTenants.P2P, dbConnectionManager)
            verify(dbConnectionManager, times(2)).getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
            verifyNoMoreInteractions(dbConnectionManager)
        }
    }

    private fun makeMockSigningRepository(
        tenant: String,
        dbConnectionManager: DbConnectionManager,
    ): SigningRepositoryImpl {
        val repo = SigningRepositoryImpl(
            getEntityManagerFactory(tenant, dbConnectionManager, mock(), mock()),
            tenantId = tenant,
            keyEncodingService = mock(),
            digestService = mock(),
            layeredPropertyMapFactory = mock()
        )
        return repo
    }

    @Test
    fun `Fresh database connections for virtual node and P2P`() {
        val sharedEntityManagerFactory = mock<EntityManagerFactory>()
        val ownedEntityManagerFactory = mock<EntityManagerFactory>()
        val virtualNodeInfo = mock<VirtualNodeInfo> {
            on { cryptoDmlConnectionId } doReturn mock()
        }
        val dbConnectionManager = mock<DbConnectionManager> {
            on { getOrCreateEntityManagerFactory(any<CordaDb>(), any()) } doReturn mock()
            on { getOrCreateEntityManagerFactory(any<UUID>(), any(), any()) } doReturn ownedEntityManagerFactory
        }
        val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
            on { getByHoldingIdentityShortHash(any()) } doReturn virtualNodeInfo
        }
        val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
            on { get(any()) } doReturn mock()
        }
        verifyNoMoreInteractions(dbConnectionManager)
        SigningRepositoryImpl(
            entityManagerFactory = getEntityManagerFactory(
                CryptoTenants.P2P,
                dbConnectionManager,
                virtualNodeInfoReadService,
                jpaEntitiesRegistry
            ),
            tenantId = CryptoTenants.P2P,
            keyEncodingService = mock(),
            digestService = mock(),
            layeredPropertyMapFactory = mock(),
        ).use {
            verify(sharedEntityManagerFactory, times(0)).close()
        }
        verify(dbConnectionManager).getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        verifyNoMoreInteractions(dbConnectionManager)
        SigningRepositoryImpl(
            entityManagerFactory = getEntityManagerFactory(
                "123456789012",
                dbConnectionManager,
                virtualNodeInfoReadService,
                jpaEntitiesRegistry,
            ),
            tenantId = "123456789012",
            keyEncodingService = mock(),
            digestService = mock(),
            layeredPropertyMapFactory = mock(),
        ).use {
            verify(ownedEntityManagerFactory, times(0)).close()
        } // try shorter, ShortHash bombs
        verify(dbConnectionManager).getOrCreateEntityManagerFactory(any(), any(), eq(false))
        verifyNoMoreInteractions(dbConnectionManager)
        verify(sharedEntityManagerFactory, times(0)).close()
        verify(ownedEntityManagerFactory, times(1)).close()
    }
}