package net.corda.crypto.service.impl.infra

import net.corda.crypto.persistence.hsm.HSMConfig
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.impl.hsm.service.HSMServiceImpl
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent

class TestHSMService(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val impl: HSMServiceImpl
) : HSMService {
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

    override fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo {
        check(isRunning) {
            "The component is in invalid state."
        }
        return impl.assignHSM(tenantId, category, context)
    }

    override fun assignSoftHSM(tenantId: String, category: String): HSMInfo {
        check(isRunning) {
            "The component is in invalid state."
        }
        return impl.assignSoftHSM(tenantId, category)
    }

    override fun linkCategories(configId: String, links: List<HSMCategoryInfo>) {
        check(isRunning) {
            "The component is in invalid state."
        }
        return impl.linkCategories(configId, links)
    }

    override fun getLinkedCategories(configId: String): List<HSMCategoryInfo> {
        check(isRunning) {
            "The component is in invalid state."
        }
        return impl.getLinkedCategories(configId)
    }

    override fun lookup(filter: Map<String, String>): List<HSMInfo> {
        check(isRunning) {
            "The component is in invalid state."
        }
        return impl.lookup(filter)
    }

    override fun findAssignedHSM(tenantId: String, category: String): HSMTenantAssociation? {
        check(isRunning) {
            "The component is in invalid state."
        }
        return impl.findAssignedHSM(tenantId, category)
    }

    override fun findHSMConfig(configId: String): HSMConfig? {
        check(isRunning) {
            "The component is in invalid state."
        }
        return impl.findHSMConfig(configId)
    }

    override fun putHSMConfig(info: HSMInfo, serviceConfig: ByteArray): String {
        check(isRunning) {
            "The component is in invalid state."
        }
        return impl.putHSMConfig(info, serviceConfig)
    }
}