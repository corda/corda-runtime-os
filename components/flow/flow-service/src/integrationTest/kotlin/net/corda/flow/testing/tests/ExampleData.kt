package net.corda.flow.testing.tests

import net.corda.crypto.core.SecureHashImpl
import net.corda.v5.base.types.MemberX500Name

// HACK: needed to use this group id as it is hard
// coded in the implementation code, we need to remove this
const val HOLDING_IDENTITY_GROUP = "test-cordapp"

const val BOB_X500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
val BOB_X500_NAME = MemberX500Name.parse(BOB_X500)
val BOB_HOLDING_IDENTITY = net.corda.data.identity.HoldingIdentity(BOB_X500, HOLDING_IDENTITY_GROUP)

const val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
val ALICE_HOLDING_IDENTITY = net.corda.data.identity.HoldingIdentity(ALICE_X500, HOLDING_IDENTITY_GROUP)

const val CHARLIE_X500 = "CN=Charlie, O=Charlie Corp, L=LDN, C=GB"
val CHARLIE_X500_NAME = MemberX500Name.parse(CHARLIE_X500)
val CHARLIE_HOLDING_IDENTITY = net.corda.data.identity.HoldingIdentity(CHARLIE_X500, HOLDING_IDENTITY_GROUP)

const val CPI1 = "cpi1"
const val CPK1 = "cpk1"
const val FLOW_ID1 = "f1"
const val REQUEST_ID1 = "r1"
const val FAKE_FLOW_NAME = "net.corda.flow.testing.fakes.FakeFlow"
const val FLOW_NAME = "flowClass1"
const val FLOW_NAME_2 = "flowClass2"
const val PROTOCOL = "protocol"
const val PROTOCOL_2 = "protocol2"
const val SESSION_ID_1 = "S1"
const val SESSION_ID_2 = "S2"
const val INITIATED_SESSION_ID_1 = "S1-INITIATED"
val DATA_MESSAGE_0 = byteArrayOf(0)
val DATA_MESSAGE_1 = byteArrayOf(1)
val DATA_MESSAGE_2 = byteArrayOf(2)
val CPK1_CHECKSUM = SecureHashImpl("ALG", byteArrayOf(0, 0, 0, 0))