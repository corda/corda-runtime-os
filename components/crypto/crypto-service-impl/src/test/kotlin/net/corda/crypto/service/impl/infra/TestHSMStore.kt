package net.corda.crypto.service.impl.infra

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_ALIASED
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_KEY
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryMapEntity
import net.corda.crypto.persistence.db.model.HSMConfigEntity
import net.corda.crypto.persistence.db.model.PrivateKeyPolicy
import net.corda.crypto.persistence.hsm.HSMStore
import net.corda.crypto.persistence.hsm.HSMStoreActions
import net.corda.crypto.persistence.hsm.HSMConfig
import net.corda.crypto.persistence.hsm.HSMStat
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import net.corda.v5.base.util.toHex
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import javax.persistence.RollbackException
import kotlin.concurrent.withLock

class TestHSMStore : HSMStore {
    private val lock = ReentrantLock()

    private val configs = mutableListOf<HSMConfigEntity>()
    private val categoryMap = mutableListOf<HSMCategoryMapEntity>()
    private val associations = mutableListOf<HSMAssociationEntity>()
    private val categoryAssociations = mutableListOf<HSMCategoryAssociationEntity>()

    override fun act(): HSMStoreActions = Actions(this)

    override fun close() = Unit

    private class Actions(
        private val cache: TestHSMStore
    ) : HSMStoreActions {
        companion object {
            private val secureRandom = SecureRandom()
        }

        override fun findConfig(configId: String): HSMConfig? = cache.lock.withLock {
            cache.configs.firstOrNull { it.id == configId }?.toHSMConfig()
        }

        override fun findTenantAssociation(
            tenantId: String,
            category: String
        ): HSMTenantAssociation? = cache.lock.withLock {
            cache.categoryAssociations.firstOrNull {
                it.category == category && it.hsm.tenantId == tenantId
            }?.toHSMTenantAssociation()
        }

        override fun findTenantAssociation(associationId: String): HSMTenantAssociation? = cache.lock.withLock {
            cache.categoryAssociations.firstOrNull {
                it.id == associationId
            }?.toHSMTenantAssociation()
        }

        override fun lookup(filter: Map<String, String>): List<HSMInfo> = cache.lock.withLock {
            if (filter.any {
                    it.key == PREFERRED_PRIVATE_KEY_POLICY_KEY &&
                            it.value == PREFERRED_PRIVATE_KEY_POLICY_ALIASED
                }) {
                cache.configs.filter { c ->
                    cache.categoryMap.any { it.keyPolicy == PrivateKeyPolicy.ALIASED && it.config.id == c.id }
                }
            } else {
                cache.configs
            }.map { it.toHSMInfo() }
        }

        override fun getHSMStats(category: String): List<HSMStat> = cache.lock.withLock {
            cache.configs.filter {
                it.id != CryptoConsts.SOFT_HSM_CONFIG_ID && cache.categoryMap.any { a ->
                    a.config.id == it.id && a.category == category
                }
            }.map {
                HSMStat(
                    configId = it.id,
                    usages = cache.categoryAssociations.count { a ->
                        a.hsm.config.id == it.id
                    },
                    privateKeyPolicy = net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.valueOf(
                        cache.categoryMap.first { a ->
                            a.config.id == it.id && a.category == category
                        }.keyPolicy.name
                    ),
                    serviceName = it.serviceName,
                    capacity = it.capacity
                )
            }
        }

        override fun getLinkedCategories(configId: String): List<HSMCategoryInfo> = cache.lock.withLock {
            cache.categoryMap.filter { it.config.id == configId }.map {
                HSMCategoryInfo(
                    it.category,
                    net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.valueOf(it.keyPolicy.name)
                )
            }
        }

        override fun linkCategories(configId: String, links: List<HSMCategoryInfo>) = cache.lock.withLock {
            try {
                val config = cache.configs.first { it.id == configId }
                cache.categoryMap.removeIf { it.config.id == configId }
                links.forEach {
                    val entry = HSMCategoryMapEntity(
                        id = UUID.randomUUID().toString(),
                        category = it.category,
                        keyPolicy = PrivateKeyPolicy.valueOf(it.keyPolicy.name),
                        timestamp = Instant.now(),
                        config = config
                    )
                    cache.categoryMap.add(entry)
                }
            } catch (e: Throwable) {
                throw CryptoServiceLibraryException(e.message ?: "Error", e)
            }
        }

        override fun add(info: HSMInfo, serviceConfig: ByteArray): String = cache.lock.withLock {
            if (cache.configs.any { it.id == info.id }) {
                throw RollbackException()
            }
            val config = info.toHSMConfigEntity(serviceConfig)
            cache.configs.add(config)
            return config.id
        }

        override fun merge(info: HSMInfo, serviceConfig: ByteArray) = cache.lock.withLock {
            cache.configs.removeIf { it.id == info.id }
            val config = info.toHSMConfigEntity(serviceConfig)
            cache.configs.add(config)
            return@withLock
        }

        override fun associate(
            tenantId: String,
            category: String,
            configId: String
        ): HSMTenantAssociation = cache.lock.withLock {
            val association = cache.associations.firstOrNull { it.tenantId == tenantId && it.config.id == configId }
                ?: createAndPersistAssociation(tenantId, configId)
            val categoryAssociation = HSMCategoryAssociationEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                category = category,
                timestamp = Instant.now(),
                hsm = association,
                deprecatedAt = 0
            )
            cache.categoryAssociations.add(categoryAssociation)
            categoryAssociation.toHSMTenantAssociation()
        }

        override fun close() = cache.lock.withLock {
        }

        private fun createAndPersistAssociation(tenantId: String, configId: String): HSMAssociationEntity {
            val config = cache.configs.first { it.id == configId }
            val aliasSecret = ByteArray(32)
            secureRandom.nextBytes(aliasSecret)
            val association = HSMAssociationEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                config = config,
                timestamp = Instant.now(),
                masterKeyAlias = if (config.masterKeyPolicy == net.corda.crypto.persistence.db.model.MasterKeyPolicy.NEW) {
                    generateRandomShortAlias()
                } else {
                    null
                },
                aliasSecret = aliasSecret
            )
            cache.associations.add(association)
            return association
        }

        private fun generateRandomShortAlias() =
            UUID.randomUUID().toString().toByteArray().toHex().take(12)

        private fun HSMConfigEntity.toHSMConfig() = HSMConfig(
            toHSMInfo(),
            serviceConfig
        )

        private fun HSMInfo.toHSMConfigEntity(serviceConfig: ByteArray) = HSMConfigEntity(
            id = if (id.isNullOrBlank()) UUID.randomUUID().toString() else id,
            timestamp = Instant.now(),
            workerLabel = workerLabel,
            description = description,
            masterKeyPolicy = net.corda.crypto.persistence.db.model.MasterKeyPolicy.valueOf(masterKeyPolicy.name),
            masterKeyAlias = masterKeyAlias,
            supportedSchemes = supportedSchemes.joinToString(","),
            maxAttempts = maxAttempts,
            attemptTimeoutMills = attemptTimeoutMills,
            serviceName = serviceName,
            capacity = capacity,
            serviceConfig = serviceConfig
        )

        private fun HSMConfigEntity.toHSMInfo() = HSMInfo(
            id,
            timestamp,
            workerLabel,
            description,
            MasterKeyPolicy.valueOf(masterKeyPolicy.name),
            masterKeyAlias,
            maxAttempts,
            attemptTimeoutMills,
            supportedSchemes.split(","),
            serviceName,
            capacity
        )

        private fun HSMCategoryAssociationEntity.toHSMTenantAssociation() = HSMTenantAssociation(
            id = id,
            tenantId = hsm.tenantId,
            category = category,
            masterKeyAlias = hsm.masterKeyAlias,
            aliasSecret = hsm.aliasSecret,
            config = hsm.config.toHSMConfig(),
            deprecatedAt = deprecatedAt
        )
    }
}