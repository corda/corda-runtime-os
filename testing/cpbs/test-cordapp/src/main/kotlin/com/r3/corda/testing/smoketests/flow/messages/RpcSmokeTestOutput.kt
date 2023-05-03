package com.r3.corda.testing.smoketests.flow.messages

data class RpcSmokeTestOutput(
    var command: String,
    var result: String
)