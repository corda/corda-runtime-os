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
    private val hsmRepository: HSMRepository,
) : TenantInfoService {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun populate(tenantId: String, category: String, cryptoService: CryptoService): HSMAssociationInfo {
        logger.info("Assigning Soft HSM tenant={}, category={}", tenantId, category)
        return hsmRepository.use {
            it.createOrLookupCategoryAssociation(tenantId, category, MasterKeyPolicy.UNIQUE)
        }.also {
            ensureWrappingKey(it, cryptoService)
        }
    }

    override fun lookup(tenantId: String, category: String): HSMAssociationInfo? {
        logger.debug { "Finding assigned HSM, tenant=$tenantId, category=$category"  }
        return hsmRepository.use {
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