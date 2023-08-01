package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.TestRandom
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class ReconciliationContextTest {

    private val jpaEntitiesSet: JpaEntitiesSet = mock()
    private val virtualNodeInfo = VirtualNodeInfo(
        holdingIdentity = HoldingIdentity(
            MemberX500Name.parse("O=Alice, C=GB, L=London"),
            UUID(0, 1).toString()
        ),
        cpiIdentifier = CpiIdentifier("myCpi.cpi", "1.0", TestRandom.secureHash()),
        vaultDmlConnectionId = UUID(1, 2),
        cryptoDmlConnectionId = UUID(2, 3),
        uniquenessDmlConnectionId = UUID(3, 4),
        timestamp = Instant.ofEpochSecond(100),
    )
    private val clusterEm: EntityManager = mock()
    private val vnodeEm: EntityManager = mock()
    private val clusterEmf: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn clusterEm
    }
    private val vnodeEmf: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn vnodeEm
    }
    private val dbConnectionManager: DbConnectionManager = mock {
        on { getClusterEntityManagerFactory() } doReturn clusterEmf
        on {
            createEntityManagerFactory(eq(virtualNodeInfo.vaultDmlConnectionId), eq(jpaEntitiesSet))
        } doReturn vnodeEmf
    }

    @Nested
    inner class ClusterReconciliationContextTest {
        private val context = ClusterReconciliationContext(dbConnectionManager)

        @Test
        fun `Context initialisation does not get the entity manager factory and create the entity manager`() {
            verify(dbConnectionManager, never()).getClusterEntityManagerFactory()
            verify(clusterEmf, never()).createEntityManager()
        }

        @Test
        fun `Context entity manager factory and entity manager are retrieved or created when needed`() {
            context.getOrCreateEntityManager()
            verify(dbConnectionManager).getClusterEntityManagerFactory()
            verify(clusterEmf).createEntityManager()
        }

        @Test
        fun `Context entity manager factory and entity manager are retrieved or created when needed only if not previously closed`() {
            context.getOrCreateEntityManager()
            context.getOrCreateEntityManager()
            verify(dbConnectionManager).getClusterEntityManagerFactory()
            verify(clusterEmf).createEntityManager()
        }

        @Test
        fun `Context entity manager factory and entity manager are retrieved or created when needed if previously closed`() {
            context.getOrCreateEntityManager()
            context.close()
            context.getOrCreateEntityManager()
            verify(dbConnectionManager, times(2)).getClusterEntityManagerFactory()
            verify(clusterEmf, times(2)).createEntityManager()
        }

        @Test
        fun `Context exposes the entity manager`() {
            assertThat(context.getOrCreateEntityManager()).isEqualTo(clusterEm)
        }

        @Test
        fun `Closing the context closes the entity manager`() {
            context.getOrCreateEntityManager()
            verify(clusterEm, never()).close()
            context.close()
            verify(clusterEm).close()
        }

        @Test
        fun `Closing the context does not close the entity manager factory`() {
            context.getOrCreateEntityManager()
            context.close()
            verify(clusterEmf, never()).close()
        }
    }

    @Nested
    inner class VirtualNodeReconciliationContextTest {
        val context = VirtualNodeReconciliationContext(
            dbConnectionManager,
            jpaEntitiesSet,
            virtualNodeInfo
        )

        @Test
        fun `Context initialisation does not create the entity manager factory and the entity manager`() {
            verify(dbConnectionManager, never()).createEntityManagerFactory(
                eq(virtualNodeInfo.vaultDmlConnectionId),
                eq(jpaEntitiesSet)
            )
            verify(vnodeEmf, never()).createEntityManager()
        }

        @Test
        fun `Context entity manager factory and entity manager are created when called`() {
            context.getOrCreateEntityManager()
            verify(dbConnectionManager).createEntityManagerFactory(
                eq(virtualNodeInfo.vaultDmlConnectionId),
                eq(jpaEntitiesSet)
            )
            verify(vnodeEmf).createEntityManager()
        }

        @Test
        fun `Context entity manager factory and entity manager are not recreated if they haven't been closed`() {
            context.getOrCreateEntityManager()
            context.getOrCreateEntityManager()
            verify(dbConnectionManager).createEntityManagerFactory(
                eq(virtualNodeInfo.vaultDmlConnectionId),
                eq(jpaEntitiesSet)
            )
            verify(vnodeEmf).createEntityManager()
        }

        @Test
        fun `Context entity manager factory and entity manager are recreated if they have been closed`() {
            context.getOrCreateEntityManager()
            context.close()
            context.getOrCreateEntityManager()
            verify(dbConnectionManager, times(2)).createEntityManagerFactory(
                eq(virtualNodeInfo.vaultDmlConnectionId),
                eq(jpaEntitiesSet)
            )
            verify(vnodeEmf, times(2)).createEntityManager()
        }

        @Test
        fun `Context exposes the entity manager`() {
            assertThat(context.getOrCreateEntityManager()).isEqualTo(vnodeEm)
        }

        @Test
        fun `Closing the context closes the entity manager`() {
            context.getOrCreateEntityManager()
            verify(vnodeEm, never()).close()
            context.close()
            verify(vnodeEm).close()
        }

        @Test
        fun `Closing the context closes the entity manager factory`() {
            context.getOrCreateEntityManager()
            verify(vnodeEmf, never()).close()
            context.close()
            verify(vnodeEmf).close()
        }
    }
}