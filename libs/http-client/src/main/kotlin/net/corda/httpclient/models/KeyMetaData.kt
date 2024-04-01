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
 * @param alias 
 * @param created 
 * @param hsmCategory 
 * @param keyId 
 * @param scheme 
 * @param masterKeyAlias 
 */


data class KeyMetaData (

    @Json(name = "alias")
    val alias: kotlin.String,

    @Json(name = "created")
    val created: kotlin.String,

    @Json(name = "hsmCategory")
    val hsmCategory: kotlin.String,

    @Json(name = "keyId")
    val keyId: kotlin.String,

    @Json(name = "scheme")
    val scheme: kotlin.String,

    @Json(name = "masterKeyAlias")
    val masterKeyAlias: kotlin.String? = null

)

