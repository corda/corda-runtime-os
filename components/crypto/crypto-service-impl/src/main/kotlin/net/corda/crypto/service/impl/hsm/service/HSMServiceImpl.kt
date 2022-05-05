package net.corda.crypto.service.impl.hsm.service

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.impl.config.rootEncryptor
import net.corda.crypto.impl.config.softPersistence
import net.corda.crypto.persistence.HSMCache
import net.corda.crypto.persistence.HSMCacheActions
import net.corda.crypto.persistence.HSMTenantAssociation
import net.corda.crypto.service.SoftCryptoServiceConfig
import net.corda.crypto.service.impl.hsm.soft.SoftCryptoService
import net.corda.crypto.service.impl.hsm.soft.SoftCryptoServiceProviderImpl
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import java.time.Instant

class HSMServiceImpl(
    config: SmartConfig,
    private val hsmCache: HSMCache,
    private val schemeMetadata: CipherSchemeMetadata,
    private val opsProxyClient: CryptoOpsProxyClient
) : AutoCloseable {
    companion object {
        private val logger = contextLogger()
    }

    private val encryptor = config.rootEncryptor()

    private val softConfig = config.softPersistence()

    fun assignHSM(tenantId: String, category: String): HSMInfo {
        logger.info("assignHSM(tenant={}, category={})", tenantId, category)
        val association = hsmCache.act {
            val min = it.getHSMStats(category).minByOrNull { s -> s.usages }
                ?: throw CryptoServiceLibraryException("There is no available HSMs.")
            it.associate(tenantId = tenantId, category = category, configId = min.configId)
        }
        ensureWrappingKey(association)
        logger.info("assignHSM(tenant={}, category={})={}", tenantId, category, association.config.info)
        return association.config.info
    }

    fun assignSoftHSM(tenantId: String, category: String): HSMInfo {
        logger.info("assignSoftHSM(tenant={}, category={})", tenantId, category)
        val association = hsmCache.act {
            val hsm = it.findConfig(CryptoConsts.SOFT_HSM_CONFIG_ID)?.info
                ?: it.addSoftConfig()
            it.associate(tenantId = tenantId, category = category, configId = hsm.id)
        }
        ensureWrappingKey(association)
        logger.info("assignSoftHSM(tenant={}, category={})={}", tenantId, category, association.config.info)
        return association.config.info
    }

    fun findAssignedHSM(tenantId: String, category: String): HSMTenantAssociation? {
        logger.debug("findAssignedHSM(tenant={}, category={})", tenantId, category)
        return hsmCache.act {
            it.findTenantAssociation(tenantId, category)
        }
    }

    fun findHSMConfig(configId: String): HSMConfig? {
        logger.debug("getPrivateHSMConfig(configId={})", configId)
        return hsmCache.act {
            it.findConfig(configId)
        }
    }

    fun putHSMConfig(config: HSMConfig) {
        logger.info("putHSMConfig(id={},description={})", config.info.id, config.info.description)
        if(config.info.masterKeyPolicy == MasterKeyPolicy.SHARED) {
            require(!config.info.masterKeyAlias.isNullOrBlank()) {
                "The master key alias must be specified for '${config.info.masterKeyPolicy}' master key policy."
            }
        }
        hsmCache.act {
            if(config.info.id.isNullOrBlank()) {
                it.add(config.info, encryptor.encrypt(config.serviceConfig.array()))
            } else if(it.findConfig(config.info.id) != null) {
                it.merge(config.info, encryptor.encrypt(config.serviceConfig.array()))
            } else {
                throw CryptoServiceLibraryException(
                    "Cannot update the HSM Config with id '${config.info.id}' as it doesn't exist."
                )
            }
        }
        ensureWrappingKey(config.info)
    }

    fun lookup(filter: Map<String, String>): List<HSMInfo> {
        return hsmCache.act { it.lookup(filter) }
    }

    override fun close() {
        hsmCache.close()
    }

    private fun ensureWrappingKey(association: HSMTenantAssociation) {
        if (association.config.info.masterKeyPolicy == MasterKeyPolicy.NEW) {
            // All config information at that point is persisted, so it's safe to call crypto operations
            // for that tenant and category
            opsProxyClient.createWrappingKey(
                configId = association.config.info.id,
                failIfExists = false,
                context = emptyMap()
            )
        }
    }

    private fun ensureWrappingKey(info: HSMInfo) {
        if (info.masterKeyPolicy == MasterKeyPolicy.SHARED) {
            // All config information at that point is persisted, so it's safe to call crypto operations
            // for that tenant and category
            opsProxyClient.createWrappingKey(
                configId = info.id,
                failIfExists = false,
                context = emptyMap()
            )
        }
    }

    private fun HSMCacheActions.addSoftConfig(): HSMInfo {
        logger.info("Creating config for Soft HSM")
        val info = HSMInfo(
            CryptoConsts.SOFT_HSM_CONFIG_ID,
            Instant.now(),
            1,
            null,
            "Standard Soft HSM configuration",
            MasterKeyPolicy.NEW,
            null,
            softConfig.retries,
            softConfig.timeoutMills,
            SoftCryptoService.produceSupportedSchemes(schemeMetadata).map { it.codeName },
            SoftCryptoServiceProviderImpl.SERVICE_NAME,
            -1
        )
        val serviceConfig = ObjectMapper().writeValueAsBytes(SoftCryptoServiceConfig())
        add(info, encryptor.encrypt(serviceConfig))
        return info
    }
}