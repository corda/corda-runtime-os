package net.corda.flow

import com.typesafe.config.ConfigFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.v5.base.types.MemberX500Name

val BOB_X500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
val BOB_X500_NAME = MemberX500Name.parse(BOB_X500)
val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
val BOB_X500_HOLDING_IDENTITY = HoldingIdentity(BOB_X500, "group1")
val ALICE_X500_HOLDING_IDENTITY = HoldingIdentity(ALICE_X500, "group1")
val SESSION_ID_1 = "S1"
val FLOW_ID_1 = "F1"

val EMPTY_SMART_CONFIG =  SmartConfigFactory
    .create(ConfigFactory.empty())
    .create(ConfigFactory.empty())

