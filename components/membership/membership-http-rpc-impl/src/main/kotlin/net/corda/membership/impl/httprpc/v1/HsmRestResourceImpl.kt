package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants.P2P
import net.corda.crypto.core.CryptoTenants.REST_API
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.httprpc.v1.HsmRestResource
import net.corda.membership.httprpc.v1.types.response.HsmAssociationInfo
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.read.rpc.extensions.getByHoldingIdentityShortHashOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PluggableRestResource::class])
class HsmRestResourceImpl @Activate constructor(
    @Reference(service = HSMRegistrationClient::class)
    private val hsmRegistrationClient: HSMRegistrationClient,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : HsmRestResource, PluggableRestResource<HsmRestResource>, Lifecycle {

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

    override fun assignedHsm(tenantId: String, category: String): HsmAssociationInfo? {
        verifyTenantId(tenantId)
        return hsmRegistrationClient.findHSM(tenantId, category.toCategory())?.expose()
    }

    override fun assignSoftHsm(tenantId: String, category: String): HsmAssociationInfo {
        verifyTenantId(tenantId)
        return hsmRegistrationClient.assignSoftHSM(
            tenantId,
            category.toCategory()
        ).expose()
    }

    override fun assignHsm(tenantId: String, category: String): HsmAssociationInfo {
        verifyTenantId(tenantId)
        return hsmRegistrationClient.assignHSM(
            tenantId,
            category.toCategory(),
            emptyMap()
        ).expose()
    }

    override val targetInterface = HsmRestResource::class.java

    override val protocolVersion = 1

    private val coordinatorName = LifecycleCoordinatorName.forComponent<HsmRestResource>(
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
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
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

    private fun verifyTenantId(tenantId: String) {
        if((tenantId == P2P) || (tenantId == REST_API)) {
            return
        }
        virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(
            tenantId
        ) { "Could not find holding identity '$tenantId' associated with member." }
    }
}
