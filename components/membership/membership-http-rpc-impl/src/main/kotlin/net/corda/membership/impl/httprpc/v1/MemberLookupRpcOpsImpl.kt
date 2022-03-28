package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
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

    private interface InnerMemberLookupRpcOps {
        fun lookup(
            holdingIdentityId: String,
            commonName: String?,
            organisation: String?,
            organisationUnit: String?,
            locality: String?,
            state: String?,
            country: String?
        ): RpcMemberInfoList
    }
    override val protocolVersion = 1

    private val className = this::class.java.simpleName

    private var impl: InnerMemberLookupRpcOps = InactiveImpl(className)

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
        holdingIdentityId: String,
        commonName: String?,
        organisation: String?,
        organisationUnit: String?,
        locality: String?,
        state: String?,
        country: String?
    ) = impl.lookup(holdingIdentityId, commonName, organisation, organisationUnit, locality, state, country)

    fun activate(reason: String) {
        impl = ActiveImpl(virtualNodeInfoReadService, membershipGroupReaderProvider)
        updateStatus(LifecycleStatus.UP, reason)
    }

    fun deactivate(reason: String) {
        updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl(className)
    }

    private fun updateStatus(status: LifecycleStatus, reason: String) {
        if(coordinator.status != status) {
            coordinator.updateStatus(status, reason)
        }
    }

    private class InactiveImpl(
        val className: String
    ) : InnerMemberLookupRpcOps {
        override fun lookup(
            holdingIdentityId: String,
            commonName: String?,
            organisation: String?,
            organisationUnit: String?,
            locality: String?,
            state: String?,
            country: String?
        ) = throw ServiceUnavailableException("$className is not running. Operation cannot be fulfilled.")
    }

    private class ActiveImpl(
        val virtualNodeInfoReadService: VirtualNodeInfoReadService,
        val membershipGroupReaderProvider: MembershipGroupReaderProvider
    ) : InnerMemberLookupRpcOps {
        override fun lookup(
            holdingIdentityId: String,
            commonName: String?,
            organisation: String?,
            organisationUnit: String?,
            locality: String?,
            state: String?,
            country: String?
        ): RpcMemberInfoList {
            val holdingIdentity = virtualNodeInfoReadService.getById(holdingIdentityId)?.holdingIdentity
                ?: throw ResourceNotFoundException("Could not find holding identity associated with member.")

            val reader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)
            val filteredMembers = reader.lookup()
                .filter { member ->
                    val memberName = member.name
                    commonName?.let { memberName.commonName.equals(it, true) } ?: true &&
                    organisation?.let { memberName.organisation.equals(it, true) } ?: true &&
                    organisationUnit?.let { memberName.organisationUnit.equals(it, true) } ?: true &&
                    locality?.let { memberName.locality.equals(it, true) } ?: true &&
                    state?.let { memberName.state.equals(it, true) } ?: true &&
                    country?.let { memberName.country.equals(it, true) } ?: true
                }

            return RpcMemberInfoList(
                filteredMembers
                    .map {
                        RpcMemberInfo(
                            it.memberProvidedContext.entries.associate { it.key to it.value },
                            it.mgmProvidedContext.entries.associate { it.key to it.value },
                        )
                    }
            )
        }
    }
}