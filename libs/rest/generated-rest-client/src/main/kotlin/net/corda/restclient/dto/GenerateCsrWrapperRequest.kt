package net.corda.restclient.dto

/**
 * GenerateCsrWrapperRequest
 *
 * @param x500Name The X.500 name that will be the subject associated with the request
 * @param contextMap Used to add additional attributes to the CSR; for example, signature spec
 * @param subjectAlternativeNames
 */


data class GenerateCsrWrapperRequest (

    /* The X.500 name that will be the subject associated with the request */
    val x500Name: String,

    /* Used to add additional attributes to the CSR; for example, signature spec */
    val contextMap: Map<String, String>? = null,

    val subjectAlternativeNames: List<String>? = null

)