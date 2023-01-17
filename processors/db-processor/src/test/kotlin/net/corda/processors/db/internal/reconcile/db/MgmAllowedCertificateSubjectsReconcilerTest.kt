package net.corda.processors.db.internal.reconcile.db

import net.corda.data.p2p.mtls.AllowedCertificateSubject
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.datamodel.MutualTlsAllowedClientCertificateEntity
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root
import kotlin.streams.toList

class MgmAllowedCertificateSubjectsReconcilerTest {
    private val coordinator = mock<LifecycleCoordinator>()
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val entitySet = mock<JpaEntitiesSet>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }
    private val connectionId = UUID(0, 0)
    private val virtualNodeInfo = mock<VirtualNodeInfo> {
        on { vaultDmlConnectionId } doReturn connectionId
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getAll() } doReturn listOf(virtualNodeInfo)
    }
    private val dbReader = argumentCaptor<DbReconcilerReader<AllowedCertificateSubject, AllowedCertificateSubject>>()
    private val reconciler = mock<Reconciler>()
    private val kafkaReconcilerReader = mock<ReconcilerReader<AllowedCertificateSubject, AllowedCertificateSubject>>()
    private val kafkaReconcilerWriter = mock<ReconcilerWriter<AllowedCertificateSubject, AllowedCertificateSubject>>()
    private val transaction = mock<EntityTransaction>()
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn transaction
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { createEntityManagerFactory(connectionId, entitySet) } doReturn entityManagerFactory
    }
    private val reconcilerFactory = mock<ReconcilerFactory> {
        on { create(
            dbReader.capture(),
            eq(kafkaReconcilerReader),
            eq(kafkaReconcilerWriter),
            eq(AllowedCertificateSubject::class.java),
            eq(AllowedCertificateSubject::class.java),
            any(),
        ) } doReturn reconciler
    }

    private val mgmAllowedCertificateSubjectsReconciler = MgmAllowedCertificateSubjectsReconciler(
        coordinatorFactory,
        dbConnectionManager,
        virtualNodeInfoReadService,
        jpaEntitiesRegistry,
        reconcilerFactory,
        kafkaReconcilerReader,
        kafkaReconcilerWriter,
    )

    @Test
    fun `updateInterval will start the reconciler`() {
        mgmAllowedCertificateSubjectsReconciler.updateInterval(10)

        verify(reconciler).start()
    }

    @Test
    fun `second updateInterval will update the reconciler interval`() {
        mgmAllowedCertificateSubjectsReconciler.updateInterval(10)
        mgmAllowedCertificateSubjectsReconciler.updateInterval(20)

        verify(reconciler).updateInterval(20)
    }

    @Test
    fun `second updateInterval will not start the reconciler again`() {
        mgmAllowedCertificateSubjectsReconciler.updateInterval(10)
        mgmAllowedCertificateSubjectsReconciler.updateInterval(30)

        verify(reconciler, times(1)).start()
    }

    @Test
    fun `close will close the reconciler`() {
        mgmAllowedCertificateSubjectsReconciler.updateInterval(10)

        mgmAllowedCertificateSubjectsReconciler.close()

        verify(reconciler).stop()
    }

    @Test
    fun `close will not close the reconciler is not started`() {
        mgmAllowedCertificateSubjectsReconciler.close()

        verify(reconciler, never()).stop()
    }

    @Test
    fun `getAllAllowedSubjects will return all the subjects`() {
        val root = mock<Root<MutualTlsAllowedClientCertificateEntity>>()
        val queryBuilder = mock< CriteriaQuery<MutualTlsAllowedClientCertificateEntity>> {
            on { from(MutualTlsAllowedClientCertificateEntity::class.java) } doReturn root
            on { select(root) } doReturn mock
        }
        val criteriaBuilder = mock<CriteriaBuilder> {
            on { createQuery(MutualTlsAllowedClientCertificateEntity::class.java) } doReturn queryBuilder
        }
        val reply = mock<TypedQuery<MutualTlsAllowedClientCertificateEntity>>() {
            on { resultStream } doReturn listOf(
                MutualTlsAllowedClientCertificateEntity("subject 1", false),
                MutualTlsAllowedClientCertificateEntity("subject 2", true),
                MutualTlsAllowedClientCertificateEntity("subject 3", false),
            ).stream()
        }
        whenever(entityManager.criteriaBuilder).doReturn(criteriaBuilder)
        whenever(entityManager.createQuery(queryBuilder)).doReturn(reply)

        mgmAllowedCertificateSubjectsReconciler.updateInterval(10)

        val records = dbReader.firstValue.getAllVersionedRecords()?.toList()

        assertThat(records).hasSize(3)
            .anySatisfy {
                assertThat(it.value.subject).isEqualTo("subject 1")
                assertThat(it.key.subject).isEqualTo("subject 1")
                assertThat(it.isDeleted).isFalse
            }
            .anySatisfy {
                assertThat(it.value.subject).isEqualTo("subject 2")
                assertThat(it.key.subject).isEqualTo("subject 2")
                assertThat(it.isDeleted).isTrue
            }
            .anySatisfy {
                assertThat(it.value.subject).isEqualTo("subject 3")
                assertThat(it.key.subject).isEqualTo("subject 3")
                assertThat(it.isDeleted).isFalse
            }
    }
}