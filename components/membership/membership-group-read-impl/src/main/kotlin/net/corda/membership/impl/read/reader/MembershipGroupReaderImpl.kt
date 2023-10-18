package net.corda.membership.impl.read.reader

import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.ledgerKeyHashes
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionKeyHashes
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.toSortedMap
import net.corda.membership.lib.toWire
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity

class MembershipGroupReaderImpl(
    private val holdingIdentity: HoldingIdentity,
    private val membershipGroupReadCache: MembershipGroupReadCache,
    private val groupParametersReaderService: GroupParametersReaderService,
    private val memberInfoFactory: MemberInfoFactory,
    private val platformInfoProvider: PlatformInfoProvider,
) : MembershipGroupReader {
    override val groupId: String = holdingIdentity.groupId
    override val owningMember: MemberX500Name = holdingIdentity.x500Name

    private val memberList: List<MemberInfo>
        get() = membershipGroupReadCache.memberListCache.get(holdingIdentity)
            ?: throw IllegalStateException(
                "Failed to find member list for ID='${holdingIdentity.shortHash}, Group ID='${holdingIdentity.groupId}'"
            )

    override val groupParameters: InternalGroupParameters?
        get() = groupParametersReaderService.get(holdingIdentity)

    override val signedGroupParameters: SignedGroupParameters?
        get() = groupParametersReaderService.getSigned(holdingIdentity)

    override fun lookup(filter: MembershipStatusFilter): Collection<MemberInfo> =
        withCurrentMgmPlatformVersion(memberList.filterBy(filter))

    override fun lookupByLedgerKey(ledgerKeyHash: SecureHash, filter: MembershipStatusFilter): MemberInfo? =
        memberList.filterBy(filter).singleOrNull { ledgerKeyHash in it.ledgerKeyHashes }

    override fun lookupBySessionKey(sessionKeyHash: SecureHash, filter: MembershipStatusFilter): MemberInfo? =
        memberList.filterBy(filter).singleOrNull { it.sessionKeyHashes.contains(sessionKeyHash) }?.let {
            withCurrentMgmPlatformVersion(setOf(it)).first()
        }

    override val notaryVirtualNodeLookup: NotaryVirtualNodeLookup by lazy {
        NotaryVirtualNodeLookupImpl(this)
    }

    override fun lookup(name: MemberX500Name, filter: MembershipStatusFilter): MemberInfo? =
        memberList.filterBy(filter).singleOrNull { it.name == name }?.let {
            withCurrentMgmPlatformVersion(setOf(it)).first()
        }

    private fun List<MemberInfo>.filterBy(filter: MembershipStatusFilter): List<MemberInfo> {
        return when (filter) {
            MembershipStatusFilter.PENDING -> this.filter { it.status == MEMBER_STATUS_PENDING }
            MembershipStatusFilter.ACTIVE -> this.filter { it.status == MEMBER_STATUS_ACTIVE }
            MembershipStatusFilter.ACTIVE_IF_PRESENT_OR_PENDING ->
                this.groupBy { it.name }.flatMap { memberEntry ->
                    memberEntry.value.filterBy(MembershipStatusFilter.ACTIVE).ifEmpty {
                        memberEntry.value.filterBy(MembershipStatusFilter.PENDING)
                    }
                }
            MembershipStatusFilter.ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING -> groupBy { it.name }.flatMap {
                it.value.filterBy(MembershipStatusFilter.ACTIVE_OR_SUSPENDED).ifEmpty {
                    it.value.filterBy(MembershipStatusFilter.PENDING)
                }
            }
            else -> this.filter { it.status == MEMBER_STATUS_ACTIVE || it.status == MEMBER_STATUS_SUSPENDED }
        }
    }

    /**
     * Note: This method should be called on every lookup result returned by the following methods: [lookup], [lookup],
     * [lookupBySessionKey].
     *
     * This method refreshes an MGM's view on its own [MemberInfo] to reflect the correct platform version, as
     * retrieved from [PlatformInfoProvider]. Without this, an MGM would continue to see the older platform version
     * in its [MemberInfo] after performing a platform upgrade.
     */
    private fun withCurrentMgmPlatformVersion(members: Collection<MemberInfo>): Collection<MemberInfo> =
        members.firstOrNull { it.isMgm && it.name == owningMember }?.let { mgm ->
            members
                .minus(mgm)
                .plus(
                    memberInfoFactory.createMemberInfo(
                        mgm.memberProvidedContext.refreshPlatformVersion().toSortedMap(),
                        mgm.mgmProvidedContext.toWire().toSortedMap()
                    )
                )
        } ?: members

    private fun MemberContext.refreshPlatformVersion(): Map<String, String?> =
        entries.filterNot { it.key == PLATFORM_VERSION }
            .associate { it.key to it.value }
            .plus(PLATFORM_VERSION to platformInfoProvider.activePlatformVersion.toString())
}
