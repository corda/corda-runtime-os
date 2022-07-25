package net.corda.crypto.service.impl.infra

import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.persistence.hsm.HSMUsage
import net.corda.crypto.persistence.hsm.HSMStore
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.toHex
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TestHSMStore(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val schemeMetadata: CipherSchemeMetadata
) : HSMStore {
    companion object {
        private val logger = contextLogger()
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

    override fun findTenantAssociation(tenantId: String, category: String): HSMTenantAssociation? = lock.withLock {
        categoryAssociations.firstOrNull {
            it.category == category && it.hsmAssociation.tenantId == tenantId
        }?.toHSMTenantAssociation()
    }

    override fun getHSMUsage(): List<HSMUsage> = lock.withLock {
        categoryAssociations.groupBy { it.hsmAssociation.workerSetId }.map {
            HSMUsage(
                workerSetId = it.key,
                usages = it.value.count()
            )
        }
    }

    override fun associate(
        tenantId: String,
        category: String,
        workerSetId: String,
        masterKeyPolicy: MasterKeyPolicy
    ): HSMTenantAssociation = lock.withLock {
        val association = associations.firstOrNull { it.tenantId == tenantId && it.workerSetId == workerSetId }
            ?: createAndPersistAssociation(tenantId, workerSetId, masterKeyPolicy)
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            category = category,
            timestamp = Instant.now(),
            hsmAssociation = association,
            deprecatedAt = 0
        )
        categoryAssociations.add(categoryAssociation)
        categoryAssociation.toHSMTenantAssociation()
    }

    private fun createAndPersistAssociation(
        tenantId: String,
        workerSetId: String,
        masterKeyPolicy: MasterKeyPolicy
    ): HSMAssociationEntity {
        val aliasSecret = ByteArray(32)
        schemeMetadata.secureRandom.nextBytes(aliasSecret)
        val association = HSMAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            workerSetId = workerSetId,
            timestamp = Instant.now(),
            masterKeyAlias = if (masterKeyPolicy == MasterKeyPolicy.UNIQUE) {
                generateRandomShortAlias()
            } else {
                null
            },
            aliasSecret = aliasSecret
        )
        associations.add(association)
        return association
    }

    private fun generateRandomShortAlias() =
        UUID.randomUUID().toString().toByteArray().toHex().take(12)

    private fun HSMCategoryAssociationEntity.toHSMTenantAssociation() = HSMTenantAssociation(
        id = id,
        tenantId = hsmAssociation.tenantId,
        category = category,
        masterKeyAlias = hsmAssociation.masterKeyAlias,
        aliasSecret = hsmAssociation.aliasSecret,
        workerSetId = hsmAssociation.workerSetId,
        deprecatedAt = deprecatedAt
    )
}