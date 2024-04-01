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

package net.corda.httpclient.models


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param cpiName 
 * @param cpiVersion 
 * @param signerSummaryHash 
 */


data class CpiIdentifier (

    @Json(name = "cpiName")
    val cpiName: kotlin.String,

    @Json(name = "cpiVersion")
    val cpiVersion: kotlin.String,

    @Json(name = "signerSummaryHash")
    val signerSummaryHash: kotlin.String? = null

)

