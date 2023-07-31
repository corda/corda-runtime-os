package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.softhsm.HSMRepository
import net.corda.crypto.softhsm.TenantInfoService
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.utilities.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TenantInfoServiceImpl(
    private val hsmRepositoryFactory: () -> HSMRepository,
) : TenantInfoService {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun populate(tenantId: String, category: String, cryptoService: CryptoService): HSMAssociationInfo {
        logger.info("assignSoftHSM(tenant={}, category={})", tenantId, category)
        return hsmRepositoryFactory().use { hsmRepository ->
            val existing = hsmRepository.findTenantAssociation(tenantId, category)
            if (existing != null) {
                logger.warn("Already have tenant information populated for tenant={}, category={}", tenantId, category)
                ensureWrappingKey(existing, cryptoService)
                return existing
            }
            hsmRepository.associate(
                tenantId = tenantId,
                category = category,
                // Defaulting the below to what it used be in crypto default config - but it probably needs be removed now
                masterKeyPolicy = MasterKeyPolicy.UNIQUE
            )
        }.also {
            ensureWrappingKey(it, cryptoService)
        }
    }
    
    override fun lookup(tenantId: String, category: String): HSMAssociationInfo? {
        logger.debug { "findAssignedHSM(tenant=$tenantId, category=$category)"  }
        return hsmRepositoryFactory().use {
            it.findTenantAssociation(tenantId, category)
        }
    }

    private fun ensureWrappingKey(association: HSMAssociationInfo, cryptoService: CryptoService) {
        require(!association.masterKeyAlias.isNullOrBlank()) {
            "The master key alias is not specified."
        }

        cryptoService.createWrappingKey(
            failIfExists = false,
            wrappingKeyAlias = association.masterKeyAlias
                ?: throw InvalidParamsException("no masterKeyAlias in association"),
            context = mapOf(
                CRYPTO_TENANT_ID to association.tenantId
            )
        )
    }
}