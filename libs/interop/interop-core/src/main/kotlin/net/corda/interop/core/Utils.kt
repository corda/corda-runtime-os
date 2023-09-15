package net.corda.interop.core

import net.corda.crypto.core.ShortHash
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity


class Utils {
    companion object {
        fun computeShortHash(name: String, groupId: String): ShortHash {
            return HoldingIdentity(MemberX500Name.parse(name), groupId).shortHash
        }
    }
}
