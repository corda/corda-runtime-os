package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * Holding Identity endpoint class, decouples the internal version from what
 * we return in the REST api.
 */
data class HoldingIdentity(val x500Name: String, val groupId: String, val shortHash: String, val fullHash: String)
