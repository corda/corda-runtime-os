package net.corda.restclient.dto

import net.corda.data.crypto.SecureHash
import com.fasterxml.jackson.annotation.JsonProperty

/**
 *
 *
 * @param name
 * @param signerSummaryHash
 * @param version
 */


data class CpiIdentifier1 (

    @field:JsonProperty("name")
    val name: kotlin.String,

    @field:JsonProperty("signerSummaryHash")
    val signerSummaryHash: SecureHash,

    @field:JsonProperty("version")
    val version: kotlin.String

)
