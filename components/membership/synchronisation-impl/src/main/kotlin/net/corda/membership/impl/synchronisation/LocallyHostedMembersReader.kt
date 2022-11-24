package net.corda.membership.impl.synchronisation

import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService

internal class LocallyHostedMembersReader(
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) {
    data class LocallyHostedMember(
        val member: HoldingIdentity,
        val mgm: HoldingIdentity,
    )
    fun readAllLocalMembers(): Collection<LocallyHostedMember> {
        return virtualNodeInfoReadService.getAll().map {
            it.holdingIdentity
        }
            .mapNotNull { viewingMemberId ->
                val knownMembers = membershipGroupReaderProvider
                    .getGroupReader(viewingMemberId)
                    .lookup()
                knownMembers.firstOrNull { potentialMgm ->
                    potentialMgm.isMgm
                }?.let { mgm ->
                    knownMembers
                        .filter {
                            it != mgm
                        }
                        .firstOrNull {
                            it.name == viewingMemberId.x500Name
                        }?.let { member ->
                            LocallyHostedMember(
                                mgm = mgm.holdingIdentity,
                                member = member.holdingIdentity,
                            )
                        }
                }
            }
    }
}
