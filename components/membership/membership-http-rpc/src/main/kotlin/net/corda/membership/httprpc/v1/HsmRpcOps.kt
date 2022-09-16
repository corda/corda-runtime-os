package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.response.HsmAssociationInfo

/**
 * The HSM API consists of endpoints used to work with Hardware Security Modules (HSM) for securely storing keys. The API
 * allows you to assign a hardware-backed or soft HSM to a tenant, and retrieve HSM information if one is already
 * assigned to the specified tenant.
 */
@HttpRpcResource(
    name = "HSM API",
    description = "The HSM API consists of endpoints used to work with Hardware Security Modules (HSM) for securely storing keys.",
    path = "hsm"
)
interface HsmRpcOps : RpcOps {

    /**
     * The [assignedHsm] method enables you to retrieve information on the HSM of the specified category if one is
     * already assigned to the specified tenant. This method returns null if no such HSM is assigned.
     *
     * Example usage:
     * ```
     * hsmOps.assignedHsm("58B6030FABDD", "TLS")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param category Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'.
     *
     * @return Information on the assigned HSM, or null if no HSM is assigned.
     */
    @HttpRpcGET(
        path = "{tenantId}/{category}",
        description = "Enables you to retrieve information on the HSM of the specified category assigned to the tenant."
    )
    fun assignedHsm(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api' or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "Category of the HSM. Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'.")
        category: String
    ): HsmAssociationInfo?

    /**
     * The [assignSoftHsm] method enables you to assign a soft HSM to the tenant for the specified category. Unlike a
     * hardware-backed HSM, a soft HSM is a software emulation of HSM security features without a physical device.
     *
     * Example usage:
     * ```
     * hsmOps.assignSoftHsm("58B6030FABDD", "CI")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param category Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'.
     *
     * @return Information on the newly assigned HSM.
     */
    @HttpRpcPOST(
        path = "soft/{tenantId}/{category}",
        description = "Enables you to assign a soft HSM to the tenant for the specified category."
    )
    fun assignSoftHsm(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api' or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "Category of the HSM. Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'.")
        category: String
    ): HsmAssociationInfo

    /**
     * The [assignHsm] method enables you to assign a hardware-backed HSM to the tenant for the specified category.
     *
     * Example usage:
     * ```
     * hsmOps.assignHsm("58B6030FABDD", "LEDGER")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param category Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'.
     *
     * @return Information on the newly assigned HSM.
     */
    @HttpRpcPOST(
        path = "{tenantId}/{category}",
        description = "Enables you to assign a hardware-backed HSM to the tenant for the specified category."
    )
    fun assignHsm(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api' or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "Category of the HSM. Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'.")
        category: String
    ): HsmAssociationInfo
}
