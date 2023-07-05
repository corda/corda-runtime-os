package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.persistence.HSMStore
import net.corda.crypto.service.HSMService
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.utilities.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory


// TODO CORE-15265 there's no actual reason this couldn't all move into CryptoService
class HSMServiceImpl(
    private val store: HSMStore,
    private val cryptoService: CryptoService,
) : HSMService {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private fun Map<String, String>.isPreferredPrivateKeyPolicy(policy: String): Boolean =
            this[CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_KEY] == policy
    }

    override fun assignSoftHSM(tenantId: String, category: String): HSMAssociationInfo {
        logger.info("assignSoftHSM(tenant={}, category={})", tenantId, category)
        val existing = store.findTenantAssociation(tenantId, category)
        if(existing != null) {
            logger.warn(
                "The ${existing.hsmId} HSM already assigned for tenant={}, category={}",
                tenantId,
                category)
            ensureWrappingKey(existing)
            return existing
        }
        val association = store.associate(
            tenantId = tenantId,
            category = category,
            hsmId = SOFT_HSM_ID,
            // Defaulting the below to what it used be in crypto default config - but it probably needs be removed now
            masterKeyPolicy = MasterKeyPolicy.UNIQUE
        )
        ensureWrappingKey(association)
        return association
    }

    override fun findAssignedHSM(tenantId: String, category: String): HSMAssociationInfo? {
        logger.debug { "findAssignedHSM(tenant=$tenantId, category=$category)"  }
        return store.findTenantAssociation(tenantId, category)
    }

    private fun ensureWrappingKey(association: HSMAssociationInfo) {
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