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
 * @param key 
 * @param lastChangedTimestamp 
 * @param `value` 
 */


data class PropertyResponseType (

    @Json(name = "key")
    val key: kotlin.String,

    @Json(name = "lastChangedTimestamp")
    val lastChangedTimestamp: kotlin.String,

    @Json(name = "value")
    val `value`: kotlin.String

)

