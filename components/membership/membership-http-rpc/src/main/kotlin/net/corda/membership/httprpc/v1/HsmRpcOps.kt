package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.response.HsmInfo

@HttpRpcResource(
    name = "HsmRpcOps",
    description = "HSM API",
    path = "/"
)
interface HsmRpcOps : RpcOps {
    /**
     * GET endpoint which list the available HSMs.
     *
     * @return A list of available HSMs.
     */
    @HttpRpcGET(
        path = "hsm",
        description = "Get list of available HSMs."
    )
    fun listHsms(): Collection<HsmInfo>

    /**
     * GET endpoint which list the assigned HSMs.
     *
     * @return A list of assigned HSMs.
     */
    @HttpRpcGET(
        path = "{tenantId}/hsm",
        description = "Get list of assigned HSMs."
    )
    fun assignedHsm(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity identity ID.")
        tenantId: String,
        @HttpRpcQueryParameter(description = "Category", required = true)
        category: String,
    ): HsmInfo?

    /**
     * POST endpoint which assign soft HSM.
     *
     * @return The newly assigned HSM.
     */
    @HttpRpcPOST(
        path = "{tenantId}/hsm/soft",
        description = "Assign soft HSM"
    )
    fun assignSoftHsm(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity identity ID.")
        tenantId: String,
        @HttpRpcQueryParameter(description = "Category", required = true)
        category: String,
    ): HsmInfo

    /**
     * POST endpoint which assign HSM.
     *
     * @return The newly assigned HSM.
     */
    @HttpRpcPOST(
        path = "{tenantId}/hsm",
        description = "Assign HSM."
    )
    fun assignHsm(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity identity ID.")
        tenantId: String,
        @HttpRpcQueryParameter(description = "Category", required = true)
        category: String,
    ): HsmInfo
}
