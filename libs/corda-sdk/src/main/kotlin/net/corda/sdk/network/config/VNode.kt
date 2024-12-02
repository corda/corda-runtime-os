package net.corda.sdk.network.config

import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity

data class VNode(
    @JsonProperty("x500Name")
    val x500Name: String,
    @JsonProperty("cpi")
    val cpi: String,
    @JsonProperty("serviceX500Name")
    var serviceX500Name: String? = null,
    @JsonProperty("flowProtocolName")
    var flowProtocolName: String? = null,
    @JsonProperty("backchainRequired")
    var backchainRequired: String? = null,
    @JsonProperty("mgmNode")
    var mgmNode: String? = null,
) {
    fun getHoldingIdentityForGroup(groupId: String): HoldingIdentity = HoldingIdentity(MemberX500Name.parse(x500Name), groupId)
}
