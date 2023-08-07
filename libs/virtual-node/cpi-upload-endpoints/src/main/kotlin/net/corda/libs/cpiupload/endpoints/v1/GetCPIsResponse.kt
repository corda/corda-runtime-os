package net.corda.libs.cpiupload.endpoints.v1

/**
 * Response from CPI list API
 *
 * @param cpis List of CPIs.
 */
@Deprecated("Deprecated, unused in newer endpoint getAllCpisList, remove once out of LTS")
data class GetCPIsResponse(val cpis: List<CpiMetadata>)
