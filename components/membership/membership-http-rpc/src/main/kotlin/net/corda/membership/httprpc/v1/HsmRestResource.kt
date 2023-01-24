package net.corda.membership.httprpc.v1

import net.corda.httprpc.RestResource
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
interface HsmRestResource : RestResource {

    /**
     * The [assignedHsm] method enables you to retrieve information on the HSM of the specified category if one is
     * already assigned to the specified tenant. This method returns null if no such HSM is assigned.
     *
     * Example usage:
     * ```
     * hsmOps.assignedHsm(tenantId = "58B6030FABDD", category = "TLS")
     *
     * hsmOps.assignedHsm(tenantId = "p2p", category = "TLS")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param category The category of the HSM; can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT',
     * 'TLS', or 'JWT_KEY'.
     *
     * @return Information on the assigned HSM, or null if no HSM is assigned.
     */
    @HttpRpcGET(
        path = "{tenantId}/{category}",
        description = "This method retrieves information on the HSM of the specified category assigned to the tenant.",
        responseDescription = """
            The HSM association details including:
            id: the unique identifier of the HSM association
            hsmId: the HSM identifier included into the association
            category: the category of the HSM; can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 
                'TLS', or 'JWT_KEY'
            masterKeyAlias: optional master key alias to be used on HSM
            deprecatedAt: time when the association was deprecated, epoch time in seconds; 
                value of 0 means the association is active"""
    )
    fun assignedHsm(
        @HttpRpcPathParameter(description = "Can either be a holding identity ID, the value 'p2p' for a cluster-level" +
                " tenant of the P2P services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API")
        tenantId: String,
        @HttpRpcPathParameter(description = "The category of the HSM; can be the value 'ACCOUNTS', 'CI', 'LEDGER'," +
                " 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'")
        category: String
    ): HsmAssociationInfo?

    /**
     * The [assignSoftHsm] method enables you to assign a soft HSM to the tenant for the specified category. Unlike a
     * hardware-backed HSM, a soft HSM is a software emulation of HSM security features without a physical device.
     *
     * Example usage:
     * ```
     * hsmOps.assignSoftHsm(tenantId = "58B6030FABDD", category = "CI")
     *
     * hsmOps.assignSoftHsm(tenantId = "p2p", category = "CI")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param category The category of the HSM; can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT',
     * 'TLS', or 'JWT_KEY'.
     *
     * @return Information on the newly assigned HSM.
     */
    @HttpRpcPOST(
        path = "soft/{tenantId}/{category}",
        description = "This method enables you to assign a soft HSM to the tenant for the specified category.",
        responseDescription = """
            The HSM association details including:
            id: the unique identifier of the HSM association
            hsmId: the HSM identifier included into the association
            category: the category of the HSM; can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 
                'TLS', or 'JWT_KEY'
            masterKeyAlias: optional master key alias to be used on HSM
            deprecatedAt: time when the association was deprecated, epoch time in seconds; 
                value of 0 means the association is active"""
    )
    fun assignSoftHsm(
        @HttpRpcPathParameter(description = "Can either be a holding identity ID, the value 'p2p' for a cluster-level" +
                " tenant of the P2P services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API")
        tenantId: String,
        @HttpRpcPathParameter(description = "The category of the HSM; can be the value 'ACCOUNTS', 'CI', 'LEDGER'," +
                " 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'")
        category: String
    ): HsmAssociationInfo

    /**
     * The [assignHsm] method enables you to assign a hardware-backed HSM to the tenant for the specified category.
     *
     * Example usage:
     * ```
     * hsmOps.assignHsm(tenantId = "58B6030FABDD", category = "LEDGER")
     *
     * hsmOps.assignHsm(tenantId = "rpc-api", category = "LEDGER")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param category The category of the HSM; can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT',
     * 'TLS', or 'JWT_KEY'.
     *
     * @return Information on the newly assigned HSM.
     */
    @HttpRpcPOST(
        path = "{tenantId}/{category}",
        description = "This method enables you to assign a hardware-backed HSM to the tenant for the specified " +
                "category.",
        responseDescription = """
            The HSM association details including:
            id: the unique identifier of the HSM association
            hsmId: the HSM identifier included into the association
            category: the category of the HSM; can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 
                'TLS', or 'JWT_KEY'
            masterKeyAlias: optional master key alias to be used on HSM
            deprecatedAt: time when the association was deprecated, epoch time in seconds; 
                value of 0 means the association is active"""
    )
    fun assignHsm(
        @HttpRpcPathParameter(description = "Can either be a holding identity ID, the value 'p2p' for a cluster-level" +
                " tenant of the P2P services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API")
        tenantId: String,
        @HttpRpcPathParameter(description = "The category of the HSM; can be the value 'ACCOUNTS', 'CI', 'LEDGER'," +
                " 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'")
        category: String
    ): HsmAssociationInfo
}
