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

package net.corda.httpclient.apis

import net.corda.httpclient.models.StartableFlowsResponse

import net.corda.httpclient.infrastructure.*
import io.ktor.client.HttpClientConfig
import io.ktor.client.request.forms.formData
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.ParametersBuilder

    open class FlowInfoApi(
    baseUrl: String = ApiClient.BASE_URL,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: ((HttpClientConfig<*>) -> Unit)? = null,
    ) : ApiClient(
        baseUrl,
        httpClientEngine,
        httpClientConfig,
    ) {

        /**
        * 
        * 
         * @return kotlin.Int
        */
            @Suppress("UNCHECKED_CAST")
        open suspend fun getFlowclassGetprotocolversion(): HttpResponse<kotlin.Int> {

            val localVariableAuthNames = listOf<String>("basicAuth")

            val localVariableBody = 
                    io.ktor.client.utils.EmptyContent

            val localVariableQuery = mutableMapOf<String, List<String>>()

            val localVariableHeaders = mutableMapOf<String, String>()

            val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/flowclass/getprotocolversion",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            )

            return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
            ).wrap()
            }

        /**
        * 
        * This method gets all flows that can be used by the specified holding identity.
         * @param holdingidentityshorthash The short hash of the holding identity; this is obtained during node registration 
         * @return StartableFlowsResponse
        */
            @Suppress("UNCHECKED_CAST")
        open suspend fun getFlowclassHoldingidentityshorthash(holdingidentityshorthash: kotlin.String): HttpResponse<StartableFlowsResponse> {

            val localVariableAuthNames = listOf<String>("basicAuth")

            val localVariableBody = 
                    io.ktor.client.utils.EmptyContent

            val localVariableQuery = mutableMapOf<String, List<String>>()

            val localVariableHeaders = mutableMapOf<String, String>()

            val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/flowclass/{holdingidentityshorthash}".replace("{" + "holdingidentityshorthash" + "}", "$holdingidentityshorthash"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            )

            return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
            ).wrap()
            }

        }
