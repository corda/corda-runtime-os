package com.r3.corda.atomic.swap.states

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

@CordaSerializable
class Member(
    val name: MemberX500Name,
    val ledgerKey: PublicKey
) {
    override fun toString(): String {
        return "Member{" + "name=" + name + '}'
    }
}