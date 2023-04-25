package com.r3.corda.testing.smoketests.flow.messages

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class InitiatedSmokeTestMessage(
    val message: String
)