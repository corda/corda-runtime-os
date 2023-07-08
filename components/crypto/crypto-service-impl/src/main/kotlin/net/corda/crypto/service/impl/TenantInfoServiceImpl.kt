package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.persistence.getEntityManagerFactory
import net.corda.crypto.service.TenantInfoService
import net.corda.crypto.softhsm.HSMRepository
import net.corda.crypto.softhsm.impl.HSMRepositoryImpl
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.orm.JpaEntitiesRegistry
import net.corda.utilities.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TenantInfoServiceImpl(
    private val dbConnectionManager: DbConnectionManager,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val cryptoService: CryptoService,
) : TenantInfoService {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun populate(tenantId: String, category: String): HSMAssociationInfo {
        logger.info("assignSoftHSM(tenant={}, category={})", tenantId, category)
        val existing = openRepository().findTenantAssociation(tenantId, category)
        if(existing != null) {
            logger.warn("Already have tenant information populated for tenant={}, category={}", tenantId, category)
            ensureWrappingKey(existing)
            return existing
        }
        return openRepository().use {
            it.associate(
                tenantId = tenantId,
                category = category,
                // Defaulting the below to what it used be in crypto default config - but it probably needs be removed now
                masterKeyPolicy = MasterKeyPolicy.UNIQUE
            )
        }.also {
            ensureWrappingKey(it)
        }
    }

    override fun lookup(tenantId: String, category: String): HSMAssociationInfo? {
        logger.debug { "findAssignedHSM(tenant=$tenantId, category=$category)"  }
        return openRepository().use {
            it.findTenantAssociation(tenantId, category)
        }
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

    private fun openRepository(): HSMRepository = HSMRepositoryImpl(
        getEntityManagerFactory(
            CryptoTenants.CRYPTO,
            dbConnectionManager,
            virtualNodeInfoReadService,
            jpaEntitiesRegistry
        ),
        CryptoTenants.CRYPTO
    )

}