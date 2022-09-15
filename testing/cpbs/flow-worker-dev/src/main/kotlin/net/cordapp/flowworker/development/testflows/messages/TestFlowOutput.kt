package net.cordapp.flowworker.development.testflows.messages

data class TestFlowOutput(
    val inputValue: String,
    val virtualNodeX500Name: String,
    val foundMemberInfo: String
)