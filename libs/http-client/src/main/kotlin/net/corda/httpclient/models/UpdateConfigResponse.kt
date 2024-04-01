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

import net.corda.httpclient.models.ConfigSchemaVersion

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param config 
 * @param schemaVersion 
 * @param section 
 * @param version 
 */


data class UpdateConfigResponse (

    @Json(name = "config")
    val config: kotlin.String,

    @Json(name = "schemaVersion")
    val schemaVersion: ConfigSchemaVersion,

    @Json(name = "section")
    val section: kotlin.String,

    @Json(name = "version")
    val version: kotlin.Int

)

