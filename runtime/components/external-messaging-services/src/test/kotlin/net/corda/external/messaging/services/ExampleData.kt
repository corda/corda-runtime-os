package net.corda.external.messaging.services

import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity

const val HOLDING_IDENTITY_GROUP = "test-cordapp"

const val BOB_X500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
val BOB_X500_NAME = MemberX500Name.parse(BOB_X500)
val BOB_HOLDING_ID = HoldingIdentity(BOB_X500_NAME,HOLDING_IDENTITY_GROUP)

const val ALICE_X500 = "CN=Alice, O=Bob Corp, L=LDN, C=GB"
val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
val ALICE_HOLDING_ID = HoldingIdentity(ALICE_X500_NAME,HOLDING_IDENTITY_GROUP)

const val CHARLIE_X500 = "CN=Charlie, O=Bob Corp, L=LDN, C=GB"
val CHARLIE_X500_NAME = MemberX500Name.parse(CHARLIE_X500)
val CHARLIE_HOLDING_ID = HoldingIdentity(CHARLIE_X500_NAME,HOLDING_IDENTITY_GROUP)
