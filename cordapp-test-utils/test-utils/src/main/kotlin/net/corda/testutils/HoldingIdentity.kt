package net.corda.testutils

import net.corda.v5.base.types.MemberX500Name

data class HoldingIdentity(val member: MemberX500Name) {
    companion object {
        fun create(commonName: String): HoldingIdentity {
            return HoldingIdentity(MemberX500Name.parse(
                "CN=$commonName, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB"))
        }
    }
}