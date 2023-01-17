package net.corda.processors.db.internal.reconcile.db

import net.corda.data.p2p.mtls.AllowedCertificateSubject
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

@Suppress("LongParameterList")
internal class MgmAllowedCertificateSubjectsReconciler(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val reconcilerFactory: ReconcilerFactory,
    private val kafkaReconcilerReader: ReconcilerReader<AllowedCertificateSubject, AllowedCertificateSubject>,
    private val kafkaReconcilerWriter: ReconcilerWriter<AllowedCertificateSubject, AllowedCertificateSubject>,
) : ReconcilerWrapper{
    private companion object {
        val dependencies = setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
        )
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
    private var dbReconcilerReader: DbReconcilerReader<AllowedCertificateSubject, AllowedCertificateSubject>? = null
    private var reconciler: Reconciler? = null
    override fun updateInterval(intervalMillis: Long) {
        if (dbReconcilerReader == null) {
            dbReconcilerReader = DbReconcilerReader(
                coordinatorFactory,
                AllowedCertificateSubject::class.java,
                AllowedCertificateSubject::class.java,
                dependencies,
                ::reconciliationContextFactory,
                ::getAllAllowedSubjects
            ).also {
                it.start()
            }
        }
        reconciler = reconciler.let { reconciler ->
            if (reconciler == null) {
                reconcilerFactory.create(
                    dbReader = dbReconcilerReader!!,
                    kafkaReader = kafkaReconcilerReader,
                    writer = kafkaReconcilerWriter,
                    keyClass = AllowedCertificateSubject::class.java,
                    valueClass = AllowedCertificateSubject::class.java,
                    reconciliationIntervalMs = intervalMillis,
                ).also { it.start() }
            } else {
                reconciler.updateInterval(intervalMillis)
                reconciler
            }
        }
    }

    private fun getAllAllowedSubjects(reconciliationContext: ReconciliationContext):
            Stream<VersionedRecord<AllowedCertificateSubject, AllowedCertificateSubject>> {
        return reconciliationContext.getOrCreateEntityManager().let { em ->
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(MutualTlsAllowedClientCertificateEntity::class.java)
            val root = queryBuilder.from(MutualTlsAllowedClientCertificateEntity::class.java)
            val query = queryBuilder
                .select(root)
            em.createQuery(query)
                .resultStream

        }.map { entity ->
            val subject = AllowedCertificateSubject(entity.subject)
            object : VersionedRecord<AllowedCertificateSubject, AllowedCertificateSubject> {
                override val version = 1
                override val isDeleted = entity.isDeleted
                override val key = subject
                override val value = subject
            }
        }
    }

    override fun close() {
        dbReconcilerReader?.stop()
        dbReconcilerReader = null
        reconciler?.stop()
        reconciler = null

    }
}