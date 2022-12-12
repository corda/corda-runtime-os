package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.orm.JpaEntitiesSet
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
        cpiIdentifier = CpiIdentifier("myCpi.cpi", "1.0", null),
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
        fun `Context initialisation creates the entity manager factory and the entity manager`() {
            verify(dbConnectionManager).getClusterEntityManagerFactory()
            verify(clusterEmf).createEntityManager()
        }

        @Test
        fun `Context exposes the entity manager`() {
            assertThat(context.entityManager).isEqualTo(clusterEm)
        }

        @Test
        fun `Closing the context closes the entity manager`() {
            verify(clusterEm, never()).close()
            context.close()
            verify(clusterEm).close()
        }

        @Test
        fun `Closing the context does not close the entity manager factory`() {
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
        fun `Context initialisation creates the entity manager factory and the entity manager`() {
            verify(dbConnectionManager).createEntityManagerFactory(
                eq(virtualNodeInfo.vaultDmlConnectionId),
                eq(jpaEntitiesSet)
            )
            verify(vnodeEmf).createEntityManager()
        }

        @Test
        fun `Context exposes the entity manager`() {
            assertThat(context.entityManager).isEqualTo(vnodeEm)
        }

        @Test
        fun `Closing the context closes the entity manager`() {
            verify(vnodeEm, never()).close()
            context.close()
            verify(vnodeEm).close()
        }

        @Test
        fun `Closing the context closes the entity manager factory`() {
            verify(vnodeEmf, never()).close()
            context.close()
            verify(vnodeEmf).close()
        }
    }
}