package net.corda.crypto.service.impl.infra

import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.service.TenantInfoService
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.v5.base.util.EncodingUtils
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import net.corda.crypto.core.CryptoConsts

class TestTenantInfoService(val cryptoService: CryptoService) : TenantInfoService {
    private val lock = ReentrantLock()
    private val associations = mutableListOf<HSMAssociationEntity>()
    private val categoryAssociations = mutableListOf<HSMCategoryAssociationEntity>()
    
    override fun lookup(tenantId: String, category: String): HSMAssociationInfo? =
        categoryAssociations.firstOrNull {
            it.category == category && it.hsmAssociation.tenantId == tenantId
        }?.toHSMAssociation()


    override fun populate(
        tenantId: String,
        category: String,
    ): HSMAssociationInfo = lock.withLock {
        val association = associations.firstOrNull { it.tenantId == tenantId }
            ?: createAndPersistAssociation(tenantId, MasterKeyPolicy.UNIQUE)
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
        masterKeyPolicy: MasterKeyPolicy
    ): HSMAssociationEntity {
        val alias = generateRandomShortAlias()
        val association = HSMAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            hsmId = CryptoConsts.SOFT_HSM_ID,
            timestamp = Instant.now(),
            masterKeyAlias = if (masterKeyPolicy == MasterKeyPolicy.UNIQUE) {
                cryptoService.createWrappingKey(alias, true, emptyMap())
                alias
            } else {
                null
            }
        )
        associations.add(association)
        return association
    }

    private fun generateRandomShortAlias() =
        EncodingUtils.toHex(UUID.randomUUID().toString().toByteArray()).take(12)

}

private fun HSMCategoryAssociationEntity.toHSMAssociation() = HSMAssociationInfo(
    id,
    hsmAssociation.tenantId,
    CryptoConsts.SOFT_HSM_ID,
    category,
    hsmAssociation.masterKeyAlias,
    deprecatedAt
)