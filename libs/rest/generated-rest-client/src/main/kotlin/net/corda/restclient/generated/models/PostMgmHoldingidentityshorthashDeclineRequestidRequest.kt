/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package net.corda.restclient.generated.models


import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 
 *
 * @param reason Reason for declining the specified registration request
 */


data class PostMgmHoldingidentityshorthashDeclineRequestidRequest (

    /* Reason for declining the specified registration request */
    @field:JsonProperty("reason")
    val reason: kotlin.String

)

