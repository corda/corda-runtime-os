package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfo
import net.corda.membership.rest.v1.types.response.RestMemberInfoList
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.read.rpc.extensions.getByHoldingIdentityShortHashOrThrow
import net.corda.virtualnode.read.rpc.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(service = [PluggableRestResource::class])
class MemberLookupRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService
) : MemberLookupRestResource, PluggableRestResource<MemberLookupRestResource>, Lifecycle {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        ): RestMemberInfoList
    }

    override val protocolVersion = 1

    private val className = this::class.java.simpleName

    private var impl: InnerMemberLookupRpcOps = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MemberLookupRestResource>(
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

    override val targetInterface: Class<MemberLookupRestResource> = MemberLookupRestResource::class.java

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
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
        ShortHash.parseOrThrow(holdingIdentityShortHash),
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
            "${MemberLookupRestResourceImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
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
        ): RestMemberInfoList {
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

            return RestMemberInfoList(
                filteredMembers.map {
                    RestMemberInfo(
                        it.memberProvidedContext.entries.associate { it.key to it.value },
                        it.mgmProvidedContext.entries.associate { it.key to it.value }
                    )
                }
            )
        }
    }
}
