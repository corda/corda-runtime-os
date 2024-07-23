package net.corda.sdk.network.config

data class VNode(
    var x500Name: String? = null,
    var cpi: String? = null,
    var serviceX500Name : String? = null,
    var flowProtocolName: String? = null,
    var backchainRequired: String? = null,
    var mgmNode: String? = null,
)
