package net.corda.p2p.linkmanager.membership

import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.crypto.PublicKeyHash
import net.corda.virtualnode.HoldingIdentity

fun MembershipGroupReaderProvider.lookup(
    requestingIdentity: HoldingIdentity,
    lookupIdentity: HoldingIdentity,
    filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
) = this.getGroupReader(requestingIdentity)
    .lookup(lookupIdentity.x500Name, filter)

fun MembershipGroupReaderProvider.lookupByKey(
    requestingIdentity: HoldingIdentity,
    key: ByteArray,
    filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
) = this.getGroupReader(requestingIdentity)
    .lookupBySessionKey(PublicKeyHash.parse(key), filter)
