package net.corda.membership.impl.rest.v1

import net.corda.crypto.core.ShortHash
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfo
import net.corda.membership.rest.v1.types.response.RestMemberInfoList
import net.corda.membership.impl.rest.v1.lifecycle.RestResourceLifecycleHandler
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.read.rest.extensions.getByHoldingIdentityShortHashOrThrow
import net.corda.virtualnode.read.rest.extensions.parseOrThrow
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

    private interface InnerMemberLookupRestResource {
        @Suppress("LongParameterList")
        fun lookup(
            holdingIdentityShortHash: ShortHash,
            commonName: String?,
            organization: String?,
            organizationUnit: String?,
            locality: String?,
            state: String?,
            country: String?,
            statuses: List<String>,
        ): RestMemberInfoList

        fun viewGroupParameters(holdingIdentityShortHash: ShortHash): Map<String, String>
    }

    override val protocolVersion = 1

    private val className = this::class.java.simpleName

    private var impl: InnerMemberLookupRestResource = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MemberLookupRestResource>(
        protocolVersion.toString()
    )

    private val lifecycleHandler = RestResourceLifecycleHandler(
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
        country: String?,
        statuses: List<String>,
    ) = impl.lookup(
        ShortHash.parseOrThrow(holdingIdentityShortHash),
        commonName,
        organization,
        organizationUnit,
        locality,
        state,
        country,
        statuses,
    )

    override fun viewGroupParameters(holdingIdentityShortHash: String): Map<String, String> =
        impl.viewGroupParameters(ShortHash.parseOrThrow(holdingIdentityShortHash))

    fun activate(reason: String) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, reason)
    }

    fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl
    }

    private object InactiveImpl : InnerMemberLookupRestResource {
        override fun lookup(
            holdingIdentityShortHash: ShortHash,
            commonName: String?,
            organization: String?,
            organizationUnit: String?,
            locality: String?,
            state: String?,
            country: String?,
            statuses: List<String>,
        ) = throw ServiceUnavailableException(
            "${MemberLookupRestResourceImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
        )

        override fun viewGroupParameters(holdingIdentityShortHash: ShortHash): Map<String, String> =
            throw ServiceUnavailableException(
                "${MemberLookupRestResourceImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
    }

    private inner class ActiveImpl : InnerMemberLookupRestResource {
        @Suppress("ComplexMethod")
        override fun lookup(
            holdingIdentityShortHash: ShortHash,
            commonName: String?,
            organization: String?,
            organizationUnit: String?,
            locality: String?,
            state: String?,
            country: String?,
            statuses: List<String>,
        ): RestMemberInfoList {
            val holdingIdentity = virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(
                holdingIdentityShortHash
            ) { "Could not find holding identity '$holdingIdentityShortHash' associated with member." }.holdingIdentity

            val reader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)
            val statusFilter = statuses.getStatusFilter(reader.isMgm(holdingIdentity))
            val filteredMembers = reader.lookup(MembershipStatusFilter.ACTIVE_OR_SUSPENDED).filter { member ->
                val memberName = member.name
                commonName?.let { memberName.commonName.equals(it, true) } ?: true &&
                organization?.let { memberName.organization.equals(it, true) } ?: true &&
                organizationUnit?.let { memberName.organizationUnit.equals(it, true) } ?: true &&
                locality?.let { memberName.locality.equals(it, true) } ?: true &&
                state?.let { memberName.state.equals(it, true) } ?: true &&
                country?.let { memberName.country.equals(it, true) } ?: true &&
                statusFilter.contains(member.status)
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

        override fun viewGroupParameters(holdingIdentityShortHash: ShortHash): Map<String, String> {
            val holdingIdentity = virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(
                holdingIdentityShortHash
            ) { "Could not find holding identity '$holdingIdentityShortHash' associated with member." }.holdingIdentity

            return membershipGroupReaderProvider
                .getGroupReader(holdingIdentity)
                .groupParameters
                ?.toMap() ?: throw ResourceNotFoundException("Could not find group parameters for holding identity " +
                    "'$holdingIdentityShortHash'.")
        }

        private fun GroupParameters.toMap() = entries.associate { it.key to it.value }

        private fun List<String>.getStatusFilter(isMgm: Boolean): List<String> {
            val filter = this.map {
                val status = it.uppercase()
                if (!listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED).contains(status)) {
                    throw ResourceNotFoundException("Invalid status: $it")
                }
                status
            }

            if (filter.isEmpty()) {
                return listOf(MEMBER_STATUS_ACTIVE)
            }

            return filter.filter { if (!isMgm) (it in setOf(MEMBER_STATUS_ACTIVE)) else true }.distinct()
        }

        private fun MembershipGroupReader.isMgm(holdingIdentity: HoldingIdentity): Boolean =
            // Uses member lookup with defaulting behaviour until CORE-11660.
            lookup(holdingIdentity.x500Name)?.isMgm ?: false
    }
}
