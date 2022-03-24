package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.httprpc.v1.MemberLookupRpcOps
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfo
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfoList
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [PluggableRPCOps::class])
class MemberLookupRpcOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService
) : MemberLookupRpcOps, PluggableRPCOps<MemberLookupRpcOps>, Lifecycle {
    companion object {
        private val logger: Logger = contextLogger()
    }

    override val protocolVersion = 1

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MemberLookupRpcOps>(
        protocolVersion.toString()
    )

    private val lifecycleHandler = RpcOpsLifecycleHandler(
        setOf(
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()
        )
    )

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val targetInterface: Class<MemberLookupRpcOps> = MemberLookupRpcOps::class.java

    private val className = this::class.java.simpleName

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("$className starting..")
        coordinator.start()
    }

    override fun stop() {
        logger.info("$className stopping..")
        coordinator.stop()
    }

    override fun lookup(holdingIdentityId: String): RpcMemberInfoList {
        val holdingIdentity = virtualNodeInfoReadService.getById(holdingIdentityId)?.holdingIdentity
            ?: throw ResourceNotFoundException("Could not find holding identity associated with member.")

        val reader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)

        return RpcMemberInfoList(
            reader.lookup().map {
                RpcMemberInfo(
                    it.memberProvidedContext.entries.associate { it.key to it.value },
                    it.mgmProvidedContext.entries.associate { it.key to it.value },
                )
            }
        )
    }
}