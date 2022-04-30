package net.corda.crypto.service.impl.infra

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.aes.AesEncryptor
import net.corda.crypto.core.aes.AesKey
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.HSMTenantAssociation
import net.corda.crypto.service.impl.createSoftHSMConfig
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TestHSMService(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val schemeMetadata: CipherSchemeMetadata
) : HSMService {
    companion object {
        private val serializer = ObjectMapper()
        val masterEncryptor = AesEncryptor(AesKey.derive(passphrase = "P1", salt = "S1"))
    }

    val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<HSMService>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private val lock = ReentrantLock()
    private val hsmConfigsById = mutableMapOf<String, HSMConfig>()
    private val softHSsmForTenants = mutableMapOf<String, String>()
    private val hsmForTenantsAndCategory = mutableMapOf<Pair<String, String>, String>()

    override fun assignHSM(tenantId: String, category: String): HSMInfo = lock.withLock {
        check(isRunning) {
            "The component is in invalid state."
        }
        TODO("Not yet implemented")
    }

    override fun assignSoftHSM(tenantId: String, category: String, passphrase: String): HSMInfo = lock.withLock {
        check(isRunning) {
            "The component is in invalid state."
        }
        val existingId = softHSsmForTenants[tenantId]
        if(existingId != null) {
            val existing = getHSMConfig(existingId)
            if(hsmForTenantsAndCategory.putIfAbsent(Pair(tenantId, category), existingId) != null) {
                throw CryptoServiceBadRequestException("The association already exists.")
            }
            return existing.info
        }
        val new = createSoftHSMConfig(schemeMetadata, masterEncryptor, tenantId, passphrase)
        if(hsmForTenantsAndCategory.putIfAbsent(Pair(tenantId, category), new.info.id) != null) {
            throw CryptoServiceBadRequestException("The association already exists.")
        }
        softHSsmForTenants[tenantId] = new.info.id
        putHSMConfig(new)
        new.info
    }

    override fun findAssignedHSM(tenantId: String, category: String): HSMInfo? = lock.withLock {
        check(isRunning) {
            "The component is in invalid state."
        }
        val id = hsmForTenantsAndCategory[Pair(tenantId, category)]
        if(id == null) {
            null
        } else {
            return getHSMConfig(id).info
        }
    }

    override fun putHSMConfig(config: HSMConfig) = lock.withLock {
        check(isRunning) {
            "The component is in invalid state."
        }
        if(hsmConfigsById.putIfAbsent(config.info.id, config) != null) {
            throw CryptoServiceBadRequestException("The config already exists.")
        }
    }

    override fun getHSMConfig(id: String): HSMConfig = lock.withLock {
        check(isRunning) {
            "The component is in invalid state."
        }
        hsmConfigsById.getValue(id)
    }

    // wrraping key policy
    // none
    // use single for all (default), the config will need extra field then
    // generate each time


    // alias secret
    // generated each time by the system
    override fun getPrivateTenantAssociation(tenantId: String, category: String): HSMTenantAssociation = lock.withLock {
        check(isRunning) {
            "The component is in invalid state."
        }
        return when(category) {
            CryptoConsts.HsmCategories.TLS -> TenantHSMConfig(
                tenantId,
                "dummy-$category",
                category,
                RSA_CODE_NAME,
                "wrapping-key",
                null
            )
            else -> TenantHSMConfig(
                tenantId,
                "dummy-$category",
                category,
                ECDSA_SECP256R1_CODE_NAME,
                "wrapping-key",
                null
            )
        }
    }
}