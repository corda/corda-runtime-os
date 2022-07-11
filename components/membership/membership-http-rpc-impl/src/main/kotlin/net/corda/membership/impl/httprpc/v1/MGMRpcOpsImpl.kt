package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.client.MGMOpsClient
import net.corda.membership.httprpc.v1.MGMRpcOps
import net.corda.membership.impl.MemberInfoExtension.Companion.isMgm
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.base.types.MemberX500Name.Companion.parse
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [PluggableRPCOps::class])
class MGMRpcOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MGMOpsClient::class)
    private val mgmOpsClient: MGMOpsClient,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : MGMRpcOps, PluggableRPCOps<MGMRpcOps>, Lifecycle {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private interface InnerMGMRpcOps {
        fun generateGroupPolicy(holdingIdentityId: String): Set<Map.Entry<String, String?>>
    }

    private val className = this::class.java.simpleName

    override val protocolVersion = 1

    private var impl: InnerMGMRpcOps = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MGMRpcOps>(
        protocolVersion.toString()
    )

    private val lifecycleHandler = RpcOpsLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(LifecycleCoordinatorName.forComponent<MGMOpsClient>())
    )

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val targetInterface: Class<MGMRpcOps> = MGMRpcOps::class.java

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("$className started.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("$className stopped.")
        coordinator.stop()
    }

    override fun generateGroupPolicy(holdingIdentityId: String) =
        impl.generateGroupPolicy(holdingIdentityId)

    fun activate(reason: String) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, reason)
    }

    fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl
    }

    private object InactiveImpl : InnerMGMRpcOps {
        override fun generateGroupPolicy(holdingIdentityId: String) =
            throw ServiceUnavailableException(
                "${MGMRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
    }

    private inner class ActiveImpl : InnerMGMRpcOps {
        override fun generateGroupPolicy(holdingIdentityId: String): Set<Map.Entry<String, String?>> {

            val holdingIdentity = virtualNodeInfoReadService.getById(holdingIdentityId)?.holdingIdentity
                ?: throw ResourceNotFoundException("Could not find holding identity associated with member.")

            val reader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)

            val isMgm = reader.lookup(parse(holdingIdentity.x500Name))?.isMgm
                ?: throw ResourceNotFoundException("The current member is not a MGM")

            if(isMgm) {
                val filteredMembers = reader.lookup(parse(holdingIdentity.x500Name))

                val mgmProvidedContext = filteredMembers!!.mgmProvidedContext

                return mgmProvidedContext.entries
            }
            return emptySet()
//            mgmOpsClient.generateGroupPolicy(holdingIdentityId)
        }
    }
}