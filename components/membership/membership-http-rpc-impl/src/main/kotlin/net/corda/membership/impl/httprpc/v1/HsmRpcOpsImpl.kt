package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.httprpc.v1.HsmRpcOps
import net.corda.membership.httprpc.v1.types.response.HsmAssociationInfo
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PluggableRPCOps::class])
class HsmRpcOpsImpl @Activate constructor(
    @Reference(service = HSMRegistrationClient::class)
    private val hsmRegistrationClient: HSMRegistrationClient,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : HsmRpcOps, PluggableRPCOps<HsmRpcOps>, Lifecycle {

    companion object {
        private fun HSMAssociationInfo.expose() =
            HsmAssociationInfo(
                id = this.id,
                hsmId = this.hsmId,
                category = this.category,
                masterKeyAlias = this.masterKeyAlias,
                deprecatedAt = this.deprecatedAt
            )

        private fun String.toCategory() = this.uppercase().also {
            if (!CryptoConsts.Categories.all.contains(it)) {
                throw ResourceNotFoundException("Invalid category: $it")
            }
        }
    }

    override fun assignedHsm(tenantId: String, category: String) =
        hsmRegistrationClient.findHSM(tenantId, category.toCategory())?.expose()

    override fun assignSoftHsm(tenantId: String, category: String) = hsmRegistrationClient.assignSoftHSM(
        tenantId, category.toCategory()
    ).expose()

    override fun assignHsm(tenantId: String, category: String) = hsmRegistrationClient.assignHSM(
        tenantId, category.toCategory(), emptyMap()
    ).expose()

    override val targetInterface = HsmRpcOps::class.java

    override val protocolVersion = 1

    private val coordinatorName = LifecycleCoordinatorName.forComponent<HsmRpcOps>(
        protocolVersion.toString()
    )
    private fun updateStatus(status: LifecycleStatus, reason: String) {
        coordinator.updateStatus(status, reason)
    }

    private fun activate(reason: String) {
        updateStatus(LifecycleStatus.UP, reason)
    }

    private fun deactivate(reason: String) {
        updateStatus(LifecycleStatus.DOWN, reason)
    }

    private val lifecycleHandler = RpcOpsLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(
            LifecycleCoordinatorName.forComponent<HSMRegistrationClient>(),
        )
    )
    private val coordinator = lifecycleCoordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val isRunning
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
