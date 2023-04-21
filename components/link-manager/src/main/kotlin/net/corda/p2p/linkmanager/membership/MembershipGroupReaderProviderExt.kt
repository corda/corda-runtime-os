package net.corda.p2p.linkmanager.membership

import net.corda.crypto.core.SecureHashImpl
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.virtualnode.HoldingIdentity

fun MembershipGroupReaderProvider.lookup(
    requestingIdentity: HoldingIdentity,
    lookupIdentity: HoldingIdentity,
    filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
) = this.getGroupReader(requestingIdentity)
    .lookup(lookupIdentity.x500Name, filter)

fun MembershipGroupReaderProvider.lookupByKey(
    requestingIdentity: HoldingIdentity,
    keyBytes: ByteArray,
    filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
) = this.getGroupReader(requestingIdentity)
    .lookupBySessionKey(
        keyBytes.run {
            require(this.size == 32) {
                "Input must be 32 bytes long for SHA-256 hash."
            }
            SecureHashImpl(DigestAlgorithmName.SHA2_256.name, this)
        },
        filter
    )
