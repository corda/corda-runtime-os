package com.r3.corda.testing.smoketests.flow.messages

data class RestSmokeTestOutput(
    var command: String,
    var result: String
)