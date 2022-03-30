package net.corda.flow

import net.corda.data.identity.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name

val BOB_X500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
val BOB_X500_NAME = MemberX500Name.parse(BOB_X500)
val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
val BOB_X500_HOLDING_IDENTITY = HoldingIdentity(BOB_X500, "group1")