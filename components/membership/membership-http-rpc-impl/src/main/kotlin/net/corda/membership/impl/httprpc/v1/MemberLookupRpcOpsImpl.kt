package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.httprpc.v1.MemberLookupRpcOps
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfo
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfoList
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.read.rpc.extensions.getByHoldingIdentityShortHashOrThrow
import net.corda.virtualnode.read.rpc.extensions.ofOrThrow
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

    private interface InnerMemberLookupRpcOps {
        @Suppress("LongParameterList")
        fun lookup(
            holdingIdentityShortHash: ShortHash,
            commonName: String?,
            organization: String?,
            organizationUnit: String?,
            locality: String?,
            state: String?,
            country: String?
        ): RpcMemberInfoList
    }

    override val protocolVersion = 1

    private val className = this::class.java.simpleName

    private var impl: InnerMemberLookupRpcOps = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MemberLookupRpcOps>(
        protocolVersion.toString()
    )

    private val lifecycleHandler = RpcOpsLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()
        )
    )

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val targetInterface: Class<MemberLookupRpcOps> = MemberLookupRpcOps::class.java

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

    override fun lookup(
        holdingIdentityShortHash: String,
        commonName: String?,
        organization: String?,
        organizationUnit: String?,
        locality: String?,
        state: String?,
        country: String?
    ) = impl.lookup(
        ShortHash.ofOrThrow(holdingIdentityShortHash),
        commonName,
        organization,
        organizationUnit,
        locality,
        state,
        country
    )

    fun activate(reason: String) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, reason)
    }

    fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl
    }

    private object InactiveImpl : InnerMemberLookupRpcOps {
        override fun lookup(
            holdingIdentityShortHash: ShortHash,
            commonName: String?,
            organization: String?,
            organizationUnit: String?,
            locality: String?,
            state: String?,
            country: String?
        ) = throw ServiceUnavailableException(
            "${MemberLookupRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
        )
    }

    private inner class ActiveImpl : InnerMemberLookupRpcOps {
        @Suppress("ComplexMethod")
        override fun lookup(
            holdingIdentityShortHash: ShortHash,
            commonName: String?,
            organization: String?,
            organizationUnit: String?,
            locality: String?,
            state: String?,
            country: String?
        ): RpcMemberInfoList {
            val holdingIdentity = virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(
                holdingIdentityShortHash
            ) { "Could not find holding identity '$holdingIdentityShortHash' associated with member." }.holdingIdentity

            val reader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)
            val filteredMembers = reader.lookup().filter { member ->
                val memberName = member.name
                commonName?.let { memberName.commonName.equals(it, true) } ?: true &&
                organization?.let { memberName.organization.equals(it, true) } ?: true &&
                organizationUnit?.let { memberName.organizationUnit.equals(it, true) } ?: true &&
                locality?.let { memberName.locality.equals(it, true) } ?: true &&
                state?.let { memberName.state.equals(it, true) } ?: true &&
                country?.let { memberName.country.equals(it, true) } ?: true
            }

            return RpcMemberInfoList(
                filteredMembers.map {
                    RpcMemberInfo(
                        it.memberProvidedContext.entries.associate { it.key to it.value },
                        it.mgmProvidedContext.entries.associate { it.key to it.value }
                    )
                }
            )
        }
    }
}
