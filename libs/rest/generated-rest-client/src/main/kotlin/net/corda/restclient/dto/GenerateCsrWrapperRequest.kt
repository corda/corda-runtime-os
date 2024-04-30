package net.corda.restclient.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * GenerateCsrWrapperRequest
 *
 * @param x500Name The X.500 name that will be the subject associated with the request
 * @param contextMap Used to add additional attributes to the CSR; for example, signature spec
 * @param subjectAlternativeNames
 */


data class GenerateCsrWrapperRequest (

    /* The X.500 name that will be the subject associated with the request */
    @field:JsonProperty("x500Name")
    val x500Name: kotlin.String,

    /* Used to add additional attributes to the CSR; for example, signature spec */
    @field:JsonProperty("contextMap")
    val contextMap: kotlin.collections.Map<kotlin.String, kotlin.String>? = null,

    @field:JsonProperty("subjectAlternativeNames")
    val subjectAlternativeNames: kotlin.collections.List<kotlin.String>? = null

)