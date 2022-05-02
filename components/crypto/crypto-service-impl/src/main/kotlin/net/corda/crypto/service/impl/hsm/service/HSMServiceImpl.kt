package net.corda.crypto.service.impl.hsm.service

import net.corda.crypto.core.Encryptor
import net.corda.crypto.impl.config.rootEncryptor
import net.corda.crypto.impl.config.softPersistence
import net.corda.crypto.persistence.HSMCache
import net.corda.crypto.persistence.HSMTenantAssociation
import net.corda.crypto.service.SoftCryptoServiceConfig
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import java.security.SecureRandom

class HSMServiceImpl(
    config: SmartConfig,
    private val hsmCache: HSMCache
) : AutoCloseable {
    private val encryptor: Encryptor

    private val softSalt: String

    private val softPassphrase: String

    init {
        encryptor = config.rootEncryptor()
        val softConfig = config.softPersistence()
        softSalt = softConfig.salt
        softPassphrase = softConfig.passphrase
    }

    private val secretRandom by lazy(LazyThreadSafetyMode.PUBLICATION) { SecureRandom() }

    fun assignHSM(tenantId: String, category: String): HSMInfo {
        TODO("Not yet implemented")
    }

    fun assignSoftHSM(tenantId: String, category: String): HSMInfo {
        // there is only one SOFT HSM configuration
        val config = SoftCryptoServiceConfig(
            salt = softSalt,
            passphrase = softPassphrase
        )
        TODO("Not yet implemented")
    }

    fun findAssignedHSM(tenantId: String, category: String): HSMInfo? =
        hsmCache.act {
            it.findTenantAssociation(tenantId, category)?.config?.info
        }

    fun getPrivateTenantAssociation(tenantId: String, category: String): HSMTenantAssociation =
        hsmCache.act {
            it.findTenantAssociation(tenantId, category)
                ?: throw CryptoServiceLibraryException(
                    "Cannot find tenant association for $tenantId and $category."
                )
        }

    fun putHSMConfig(config: HSMConfig) {
        hsmCache.act {
            if(config.info.id.isNullOrBlank()) {
                it.add(config.info, encryptor.encrypt(config.serviceConfig.array()))
            } else if(it.exists(config.info.id)) {
                it.merge(config.info, encryptor.encrypt(config.serviceConfig.array()))
            } else {
                throw CryptoServiceLibraryException(
                    "Cannot update the HSM Config with id '${config.info.id}' as it doesn't exist."
                )
            }
        }
    }

    fun lookup(): List<HSMInfo> {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}