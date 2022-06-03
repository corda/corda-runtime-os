package net.corda.crypto.service.impl.hsm.service

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.HSMContext.NOT_FAIL_IF_ASSOCIATION_EXISTS
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_ALIASED
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_KEY
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_SERVICE_NAME
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.impl.config.hsmPersistence
import net.corda.crypto.impl.config.rootEncryptor
import net.corda.crypto.impl.config.softPersistence
import net.corda.crypto.impl.executeWithRetry
import net.corda.crypto.persistence.hsm.HSMStore
import net.corda.crypto.persistence.hsm.HSMStoreActions
import net.corda.crypto.persistence.hsm.HSMConfig
import net.corda.crypto.persistence.hsm.HSMStat
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.service.SoftCryptoServiceConfig
import net.corda.crypto.service.impl.hsm.soft.SoftCryptoService
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import net.corda.data.crypto.wire.hsm.PrivateKeyPolicy
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import java.time.Instant

@Suppress("TooManyFunctions")
class HSMServiceImpl(
    config: SmartConfig,
    private val hsmStore: HSMStore,
    private val schemeMetadata: CipherSchemeMetadata,
    private val opsProxyClient: CryptoOpsProxyClient
) : AutoCloseable {
    companion object {
        private val logger = contextLogger()

        private fun Map<String, String>.isPreferredPrivateKeyPolicy(policy: String): Boolean =
            this[PREFERRED_PRIVATE_KEY_POLICY_KEY] == policy

        private fun Map<String, String>.notFailIfAssociationExists(): Boolean =
            this[NOT_FAIL_IF_ASSOCIATION_EXISTS]?.equals("YES", true) ?: false
    }

    private val encryptor = config.rootEncryptor()

    private val softConfig = config.softPersistence()

    private val hsmConfig = config.hsmPersistence()

    fun putHSMConfig(info: HSMInfo, serviceConfig: ByteArray): String {
        logger.info("putHSMConfig(id={},description={})", info.id, info.description)
        validatePutHSMConfig(info)
        val encryptedServiceConfig = encryptor.encrypt(serviceConfig)
        val id = hsmStore.act {
            if (info.id.isNullOrBlank()) {
                it.add(info, encryptedServiceConfig)
            } else if (it.findConfig(info.id) != null) {
                it.merge(info, encryptedServiceConfig)
                info.id
            } else {
                throw CryptoServiceLibraryException(
                    "Cannot update the HSM Config with id '${info.id}' as it doesn't exist."
                )
            }
        }
        ensureWrappingKey(info)
        return id
    }

    fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo {
        logger.info("assignHSM(tenant={}, category={})", tenantId, category)
        val pre = hsmStore.act {
            Pair(
                if (context.notFailIfAssociationExists()) {
                    it.findTenantAssociation(tenantId, category)
                } else {
                    null
                },
                it.getHSMStats(category)
            )
        }
        val association = if (pre.first != null) {
            pre.first
        } else {
            val stats = pre.second.filter { s ->
                s.usages < s.capacity && (!s.serviceName.equals(SOFT_HSM_SERVICE_NAME, true))
            }
            val chosen = if (context.isPreferredPrivateKeyPolicy(PREFERRED_PRIVATE_KEY_POLICY_ALIASED)) {
                tryChooseAliased(stats)
            } else {
                tryChooseAny(stats)
            }
            hsmStore.act {
                it.associate(tenantId = tenantId, category = category, configId = chosen.configId)
            }
        }
        ensureWrappingKey(association!!)
        return association.config.info
    }

    fun assignSoftHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo {
        logger.info("assignSoftHSM(tenant={}, category={})", tenantId, category)
        val association = hsmStore.act {
            val existing = if (context.notFailIfAssociationExists()) {
                it.findTenantAssociation(tenantId, category)
            } else {
                null
            }
            if (existing != null) {
                existing
            } else {
                val hsm = it.findConfig(CryptoConsts.SOFT_HSM_CONFIG_ID)?.info
                    ?: it.addSoftConfig()
                it.associate(tenantId = tenantId, category = category, configId = hsm.id)
            }
        }
        ensureWrappingKey(association)
        return association.config.info
    }

    fun linkCategories(configId: String, links: List<HSMCategoryInfo>) {
        logger.info("linkCategories(configId={}, links=[{}])", configId, links.joinToString { it.category })
        validateLinkCategories(links)
        hsmStore.act {
            it.linkCategories(configId, links)
        }
    }

    fun getLinkedCategories(configId: String): List<HSMCategoryInfo> {
        logger.debug { "getLinkedCategories(configId=$configId)" }
        return hsmStore.act {
            it.getLinkedCategories(configId)
        }
    }

    fun findAssignedHSM(tenantId: String, category: String): HSMTenantAssociation? {
        logger.debug { "findAssignedHSM(tenant=$tenantId, category=$category)"  }
        return hsmStore.act {
            it.findTenantAssociation(tenantId, category)
        }
    }

    fun findAssociation(associationId: String): HSMTenantAssociation? {
        logger.debug {"findAssociation(associationId=$associationId)" }
        return hsmStore.act {
            it.findTenantAssociation(associationId)
        }
    }

    fun findHSMConfig(configId: String): HSMConfig? {
        logger.debug { "getPrivateHSMConfig(configId=$configId)"  }
        return hsmStore.act {
            it.findConfig(configId)
        }
    }

    fun lookup(filter: Map<String, String>): List<HSMInfo> {
        logger.debug { "lookup(filter=${filter.keys.joinToString()})"  }
        return hsmStore.act { it.lookup(filter) }
    }

    override fun close() {
        hsmStore.close()
    }

    private fun validatePutHSMConfig(info: HSMInfo) {
        if (info.masterKeyPolicy == MasterKeyPolicy.SHARED) {
            require(!info.masterKeyAlias.isNullOrBlank()) {
                "The master key alias must be specified for '${info.masterKeyPolicy}' master key policy."
            }
        } else {
            require(info.masterKeyAlias.isNullOrBlank()) {
                "The master key alias must not be specified for '${info.masterKeyPolicy}' master key policy."
            }
        }
    }

    private fun validateLinkCategories(links: List<HSMCategoryInfo>) {
        links.forEach {
            require(it.category.isNotBlank()) {
                "The category must be specified."
            }
        }
    }

    private fun ensureWrappingKey(association: HSMTenantAssociation) {
        if (association.config.info.masterKeyPolicy == MasterKeyPolicy.NEW) {
            require(!association.masterKeyAlias.isNullOrBlank()) {
                "The master key alias is not specified."
            }
            // All config information at that point is persisted, so it's safe to call crypto operations
            // for that tenant and category
            executeWithRetry(logger, hsmConfig.downstreamRetries) {
                opsProxyClient.createWrappingKey(
                    configId = association.config.info.id,
                    failIfExists = false,
                    masterKeyAlias = association.masterKeyAlias!!,
                    context = mapOf(
                        CRYPTO_TENANT_ID to association.tenantId
                    )
                )
            }
        }
    }

    private fun ensureWrappingKey(info: HSMInfo) {
        if (info.masterKeyPolicy == MasterKeyPolicy.SHARED) {
            // All config information at that point is persisted, so it's safe to call crypto operations
            // for that tenant and category
            executeWithRetry(logger, hsmConfig.downstreamRetries) {
                opsProxyClient.createWrappingKey(
                    configId = info.id,
                    failIfExists = false,
                    masterKeyAlias = info.masterKeyAlias,
                    context = mapOf(
                        CRYPTO_TENANT_ID to CryptoTenants.CRYPTO
                    )
                )
            }
        }
    }

    private fun HSMStoreActions.addSoftConfig(): HSMInfo {
        logger.info("Creating config for Soft HSM")
        val info = HSMInfo(
            CryptoConsts.SOFT_HSM_CONFIG_ID,
            Instant.now(),
            null,
            "Standard Soft HSM configuration",
            MasterKeyPolicy.NEW,
            null,
            softConfig.retries,
            softConfig.timeoutMills,
            SoftCryptoService.produceSupportedSchemes(schemeMetadata).map { it.codeName },
            SOFT_HSM_SERVICE_NAME,
            -1
        )
        val serviceConfig = ObjectMapper().writeValueAsBytes(SoftCryptoServiceConfig())
        add(info, encryptor.encrypt(serviceConfig))
        linkCategories(
            info.id,
            CryptoConsts.Categories.all.map {
                HSMCategoryInfo(it, PrivateKeyPolicy.WRAPPED)
            }
        )
        return info
    }

    private fun tryChooseAliased(stats: List<HSMStat>): HSMStat =
        stats.filter {
            it.privateKeyPolicy == PrivateKeyPolicy.ALIASED ||
                    it.privateKeyPolicy == PrivateKeyPolicy.BOTH
        }.minByOrNull { s ->
            s.usages
        } ?: tryChooseAny(stats)

    private fun tryChooseAny(stats: List<HSMStat>): HSMStat =
        stats.minByOrNull { s ->
            s.usages
        } ?: throw CryptoServiceLibraryException("There is no available HSMs.")
}