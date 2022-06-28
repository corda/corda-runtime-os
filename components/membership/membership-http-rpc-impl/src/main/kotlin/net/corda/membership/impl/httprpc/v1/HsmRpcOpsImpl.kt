package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.hsm.HSMConfigurationClient
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.httprpc.v1.HsmRpcOps
import net.corda.membership.httprpc.v1.types.response.HsmInfo
import net.corda.membership.httprpc.v1.types.response.New
import net.corda.membership.httprpc.v1.types.response.None
import net.corda.membership.httprpc.v1.types.response.Shared
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

@Component(service = [PluggableRPCOps::class])
class HsmRpcOpsImpl @Activate constructor(
    @Reference(service = HSMConfigurationClient::class)
    private val hsmConfigurationClient: HSMConfigurationClient,
    @Reference(service = HSMRegistrationClient::class)
    private val hsmRegistrationClient: HSMRegistrationClient,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : HsmRpcOps, PluggableRPCOps<HsmRpcOps>, Lifecycle {

    companion object {
        private fun HSMInfo.expose() =
            HsmInfo(
                id = this.id,
                workerLabel = this.workerLabel,
                description = this.description,
                supportedSchemes = this.supportedSchemes,
                capacity = this.capacity.let {
                    if (it < 0) {
                        null
                    } else {
                        it
                    }
                },
                masterKeyPolicy = when (this.masterKeyPolicy) {
                    MasterKeyPolicy.NONE -> None
                    MasterKeyPolicy.NEW -> New
                    MasterKeyPolicy.SHARED -> Shared(this.masterKeyAlias)
                    else -> None
                },
                createdAt = this.timestamp,
                maxAttempts = this.maxAttempts,
                serviceName = this.serviceName,
                attemptTimeout = Duration.ofMillis(this.attemptTimeoutMills)
            )

        private fun String.toCategory() = this.uppercase().also {
            if (!CryptoConsts.Categories.all.contains(it)) {
                throw ResourceNotFoundException("Invalid category: $it")
            }
        }
    }

    override fun listHsms(): Collection<HsmInfo> {
        return hsmConfigurationClient.lookup(emptyMap()).map {
            it.expose()
        }
    }

    override fun assignedHsm(tenantId: String, category: String) =
        hsmRegistrationClient.findHSM(tenantId, category.toCategory())?.expose()

    override fun assignSoftHsm(tenantId: String, category: String) = hsmRegistrationClient.assignSoftHSM(
        tenantId, category.toCategory(), emptyMap()
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
            LifecycleCoordinatorName.forComponent<HSMConfigurationClient>(),
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
