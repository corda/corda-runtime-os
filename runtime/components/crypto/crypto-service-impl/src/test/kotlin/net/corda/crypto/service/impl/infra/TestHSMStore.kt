package net.corda.crypto.service.impl.infra

import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.persistence.HSMStore
import net.corda.crypto.persistence.HSMUsage
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.EncodingUtils.toHex
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TestHSMStore(
    coordinatorFactory: LifecycleCoordinatorFactory
) : HSMStore {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val lock = ReentrantLock()
    private val associations = mutableListOf<HSMAssociationEntity>()
    private val categoryAssociations = mutableListOf<HSMCategoryAssociationEntity>()

    val lifecycleCoordinator = coordinatorFactory.createCoordinator<HSMStore> { event, coordinator ->
        logger.info("LifecycleEvent received: $event")
        if(event is StartEvent) { coordinator.updateStatus(LifecycleStatus.UP) }
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }

    override fun findTenantAssociation(tenantId: String, category: String): HSMAssociationInfo? = lock.withLock {
        categoryAssociations.firstOrNull {
            it.category == category && it.hsmAssociation.tenantId == tenantId
        }?.toHSMAssociation()
    }

    override fun getHSMUsage(): List<HSMUsage> = lock.withLock {
        categoryAssociations.groupBy { it.hsmAssociation.hsmId }.map {
            HSMUsage(
                hsmId = it.key,
                usages = it.value.count()
            )
        }
    }

    override fun associate(
        tenantId: String,
        category: String,
        hsmId: String,
        masterKeyPolicy: MasterKeyPolicy
    ): HSMAssociationInfo = lock.withLock {
        val association = associations.firstOrNull { it.tenantId == tenantId && it.hsmId == hsmId }
            ?: createAndPersistAssociation(tenantId, hsmId, masterKeyPolicy)
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            category = category,
            timestamp = Instant.now(),
            hsmAssociation = association,
            deprecatedAt = 0
        )
        categoryAssociations.add(categoryAssociation)
        categoryAssociation.toHSMAssociation()
    }

    private fun createAndPersistAssociation(
        tenantId: String,
        hsmId: String,
        masterKeyPolicy: MasterKeyPolicy
    ): HSMAssociationEntity {
        val association = HSMAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            hsmId = hsmId,
            timestamp = Instant.now(),
            masterKeyAlias = if (masterKeyPolicy == MasterKeyPolicy.UNIQUE) {
                generateRandomShortAlias()
            } else {
                null
            }
        )
        associations.add(association)
        return association
    }

    private fun generateRandomShortAlias() =
        toHex(UUID.randomUUID().toString().toByteArray()).take(12)

    private fun HSMCategoryAssociationEntity.toHSMAssociation() = HSMAssociationInfo(
        id,
        hsmAssociation.tenantId,
        hsmAssociation.hsmId,
        category,
        hsmAssociation.masterKeyAlias,
        deprecatedAt
    )
}