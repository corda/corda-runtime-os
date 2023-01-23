package net.corda.ledger.persistence

import com.typesafe.config.ConfigFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.v5.base.types.MemberX500Name


val MINIMUM_SMART_CONFIG = SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())

const val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
val ALICE_X500_HOLDING_ID = HoldingIdentity(ALICE_X500, "g1")