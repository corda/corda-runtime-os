package net.corda.processors.db.internal.reconcile.db

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.datamodel.getCurrentGroupParameters
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.orm.JpaEntitiesRegistry
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.reconciliation.VersionedRecord
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.debug
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Stream
import kotlin.concurrent.withLock
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters

/**
 * Reconciler for handling reconciliation between each vnode vault database on the cluster
 * and the compacted kafka topic for the group parameters.
 */
@Suppress("LongParameterList")
class GroupParametersReconciler(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val groupParametersFactory: GroupParametersFactory,
    private val reconcilerFactory: ReconcilerFactory,
    private val kafkaReconcilerWriter: ReconcilerWriter<HoldingIdentity, SignedGroupParameters>,
    private val kafkaReconcilerReader: ReconcilerReader<HoldingIdentity, SignedGroupParameters>,
) : ReconcilerWrapper {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val dependencies = setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()
        )
        const val FAILED_DESERIALIZATION = "Could not deserialize group parameters from the database entity."
    }

    private val lock = ReentrantLock()

    private val cordaAvroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer(
        { logger.warn(FAILED_DESERIALIZATION) },
        KeyValuePairList::class.java
    )

    private val entitiesSet
        get() = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
            ?: throw CordaRuntimeException(
                "persistenceUnitName '${CordaDb.Vault.persistenceUnitName}' is not registered."
            )

    @VisibleForTesting
    internal var dbReconcilerReader: DbReconcilerReader<HoldingIdentity, SignedGroupParameters>? = null

    @VisibleForTesting
    internal var reconciler: Reconciler? = null

    override fun stop() {
        lock.withLock {
            dbReconcilerReader?.stop()
            dbReconcilerReader = null
            reconciler?.stop()
            reconciler = null
        }
    }

    override fun updateInterval(intervalMillis: Long) {
        logger.debug { "Group parameters reconciliation interval set to $intervalMillis ms" }

        lock.withLock {
            if (dbReconcilerReader == null) {
                dbReconcilerReader = DbReconcilerReader(
                    coordinatorFactory,
                    HoldingIdentity::class.java,
                    SignedGroupParameters::class.java,
                    dependencies,
                    reconciliationContextFactory,
                    ::getAllGroupParametersDBVersionedRecords
                ).also {
                    it.start()
                }
            }

            if (reconciler == null) {
                reconciler = reconcilerFactory.create(
                    dbReader = dbReconcilerReader!!,
                    kafkaReader = kafkaReconcilerReader,
                    writer = kafkaReconcilerWriter,
                    keyClass = HoldingIdentity::class.java,
                    valueClass = SignedGroupParameters::class.java,
                    reconciliationIntervalMs = intervalMillis
                ).also { it.start() }
            } else {
                logger.info("Updating Group Parameters ${Reconciler::class.java.name}")
                reconciler!!.updateInterval(intervalMillis)
            }
        }
    }

    private val reconciliationContextFactory = {
        virtualNodeInfoReadService.getAll().stream().map {
            VirtualNodeReconciliationContext(dbConnectionManager, entitiesSet, it)
        }
    }

    private fun getAllGroupParametersDBVersionedRecords(context: ReconciliationContext):
            Stream<VersionedRecord<HoldingIdentity, SignedGroupParameters>> {
        require(context is VirtualNodeReconciliationContext) {
            "Reconciliation information must be virtual node level for group parameters reconciliation"
        }
        return context.getOrCreateEntityManager().getCurrentGroupParameters()?.let { entity ->
            val signature = CryptoSignatureWithKey(
                entity.signaturePublicKey.buffer,
                entity.signatureContent.buffer,
                cordaAvroDeserializer.deserialize(entity.signatureContext)
            )
            val signedGroupParameters = AvroGroupParameters(entity.parameters.buffer, signature)
            val params = groupParametersFactory.create(signedGroupParameters)

            Stream.of(
                object : VersionedRecord<HoldingIdentity, SignedGroupParameters> {
                    override val version = entity.epoch
                    override val isDeleted = false
                    override val key = context.virtualNodeInfo.holdingIdentity
                    override val value = params
                }
            )
        } ?: Stream.empty()
    }

    private val ByteArray.buffer get() = ByteBuffer.wrap(this)
}
