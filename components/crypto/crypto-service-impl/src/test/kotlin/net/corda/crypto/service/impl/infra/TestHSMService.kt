package net.corda.crypto.service.impl.infra

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.core.aes.AesEncryptor
import net.corda.crypto.core.aes.AesKey
import net.corda.crypto.persistence.hsm.HSMConfig
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.service.HSMService
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.v5.cipher.suite.CipherSchemeMetadata
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

    override fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo = lock.withLock {
        check(isRunning) {
            "The component is in invalid state."
        }
        TODO("Not yet implemented")
    }

    override fun assignSoftHSM(tenantId: String, category: String): HSMInfo = lock.withLock {
        check(isRunning) {
            "The component is in invalid state."
        }
        TODO("Not yet implemented")
    }

    override fun linkCategories(configId: String, links: List<HSMCategoryInfo>) {
        TODO("Not yet implemented")
    }

    override fun getLinkedCategories(configId: String): List<HSMCategoryInfo> {
        TODO("Not yet implemented")
    }

    override fun lookup(filter: Map<String, String>): List<HSMInfo> {
        TODO("Not yet implemented")
    }

    override fun findAssignedHSM(tenantId: String, category: String): HSMTenantAssociation? = lock.withLock {
        check(isRunning) {
            "The component is in invalid state."
        }
        TODO("Not yet implemented")
    }

    override fun findHSMConfig(configId: String): HSMConfig? {
        TODO("Not yet implemented")
    }

    override fun putHSMConfig(info: HSMInfo, serviceConfig: ByteArray) = lock.withLock {
        check(isRunning) {
            "The component is in invalid state."
        }
        TODO("Not yet implemented")
    }
}