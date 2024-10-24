package net.corda.flow

import com.typesafe.config.ConfigFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.base.types.MemberX500Name

const val BOB_X500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
const val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
val BOB_X500_NAME = MemberX500Name.parse(BOB_X500)
val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
val BOB_X500_HOLDING_IDENTITY = HoldingIdentity(BOB_X500, "group1")
val ALICE_X500_HOLDING_IDENTITY = HoldingIdentity(ALICE_X500, "group1")
const val SESSION_ID_1 = "S1"
const val FLOW_ID_1 = "F1"
const val REQUEST_ID_1 ="R1"

val MINIMUM_SMART_CONFIG = SmartConfigFactory.createWithoutSecurityServices().create(
    ConfigFactory.parseMap(
        mapOf<String, Any>(
            FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS to 5,
            FlowConfig.PROCESSING_MAX_RETRY_WINDOW_DURATION to 10000,
            FlowConfig.PROCESSING_FLOW_FIBER_TIMEOUT to 11250
        )
    )
)
