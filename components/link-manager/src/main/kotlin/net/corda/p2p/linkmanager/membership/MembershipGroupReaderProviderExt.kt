package net.corda.p2p.linkmanager.membership

import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.crypto.PublicKeyHash
import net.corda.virtualnode.HoldingIdentity

fun MembershipGroupReaderProvider.lookup(
    requestingIdentity: HoldingIdentity,
    lookupIdentity: HoldingIdentity,
) = this.getGroupReader(requestingIdentity)
    .lookup(lookupIdentity.x500Name)

fun MembershipGroupReaderProvider.lookupByKey(
    requestingIdentity: HoldingIdentity,
    key: ByteArray
) = this.getGroupReader(requestingIdentity)
    .lookupBySessionKey(PublicKeyHash.parse(key))
