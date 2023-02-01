package net.corda.processors.db.internal.reconcile.db

import net.corda.data.p2p.mtls.MgmAllowedCertificateSubject
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.datamodel.MutualTlsAllowedClientCertificateEntity
import net.corda.orm.JpaEntitiesRegistry
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.stream.Stream
import javax.persistence.EntityManager

@Suppress("LongParameterList")
internal class MgmAllowedCertificateSubjectsReconciler(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val reconcilerFactory: ReconcilerFactory,
    private val kafkaReconcilerReader: ReconcilerReader<MgmAllowedCertificateSubject, MgmAllowedCertificateSubject>,
    private val kafkaReconcilerWriter: ReconcilerWriter<MgmAllowedCertificateSubject, MgmAllowedCertificateSubject>,
) : ReconcilerWrapper {
    companion object {
        private val dependencies = setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
        )

        internal fun getAllAllowedSubjects(em: EntityManager): Stream<MutualTlsAllowedClientCertificateEntity> {
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(MutualTlsAllowedClientCertificateEntity::class.java)
            val root = queryBuilder.from(MutualTlsAllowedClientCertificateEntity::class.java)
            val query = queryBuilder
                .select(root)
            return em.createQuery(query)
                .resultStream
        }
    }

    private val entitiesSet by lazy {
        jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
            ?: throw CordaRuntimeException(
                "persistenceUnitName '${CordaDb.Vault.persistenceUnitName}' is not registered."
            )
    }
    private fun reconciliationContextFactory() =
        virtualNodeInfoReadService.getAll().stream().map {
            VirtualNodeReconciliationContext(dbConnectionManager, entitiesSet, it)
        }
    private var dbReconcilerReader = DbReconcilerReader(
        coordinatorFactory,
        MgmAllowedCertificateSubject::class.java,
        MgmAllowedCertificateSubject::class.java,
        dependencies,
        ::reconciliationContextFactory,
        ::getAllAllowedSubjects
    )

    private var reconciler: Reconciler? = null
    override fun updateInterval(intervalMillis: Long) {
        dbReconcilerReader.start()

        reconciler = reconciler.let { reconciler ->
            if (reconciler == null) {
                reconcilerFactory.create(
                    dbReader = dbReconcilerReader,
                    kafkaReader = kafkaReconcilerReader,
                    writer = kafkaReconcilerWriter,
                    keyClass = MgmAllowedCertificateSubject::class.java,
                    valueClass = MgmAllowedCertificateSubject::class.java,
                    reconciliationIntervalMs = intervalMillis,
                ).also { it.start() }
            } else {
                reconciler.updateInterval(intervalMillis)
                reconciler
            }
        }
    }

    private fun getAllAllowedSubjects(reconciliationContext: ReconciliationContext):
        Stream<VersionedRecord<MgmAllowedCertificateSubject, MgmAllowedCertificateSubject>> {
        val context = reconciliationContext as? VirtualNodeReconciliationContext ?: return Stream.empty()
        return getAllAllowedSubjects(context.getOrCreateEntityManager())
            .map { entity ->
                val subject = MgmAllowedCertificateSubject(entity.subject, context.virtualNodeInfo.holdingIdentity.groupId)
                object : VersionedRecord<MgmAllowedCertificateSubject, MgmAllowedCertificateSubject> {
                    override val version = 1
                    override val isDeleted = entity.isDeleted
                    override val key = subject
                    override val value = subject
                }
            }
    }

    override fun close() {
        reconciler?.stop()
        reconciler = null
    }
}
