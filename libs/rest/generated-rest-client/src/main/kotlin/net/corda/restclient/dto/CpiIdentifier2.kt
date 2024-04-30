package net.corda.restclient.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * This is a duplicate of [net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier]
 * But with the [signerSummaryHash] being String as we cannot deserialise the SecureHash type
 */
data class CpiIdentifier2(
    @field:JsonProperty("name")
    val name: String,

    @field:JsonProperty("signerSummaryHash")
    val signerSummaryHash: String?,

    @field:JsonProperty("version")
    val version: String
)
