package com.r3.corda.testing.testflows.messages

data class TestFlowOutput(
    val inputValue: String,
    val virtualNodeX500Name: String,
    val foundMemberInfo: String
)
