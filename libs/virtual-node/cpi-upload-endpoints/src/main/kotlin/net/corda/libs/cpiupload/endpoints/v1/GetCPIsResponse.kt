package net.corda.libs.cpiupload.endpoints.v1

/**
 * Response from CPI list API
 *
 * @param cpis List of CPIs.
 */
data class GetCPIsResponse(val cpis: List<CpiMetadata>)
