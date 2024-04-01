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

import net.corda.httpclient.models.HostedIdentitySetupRequest

import net.corda.httpclient.infrastructure.*
import io.ktor.client.HttpClientConfig
import io.ktor.client.request.forms.formData
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.ParametersBuilder

    open class NetworkApi(
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
        open suspend fun getNetworkGetprotocolversion(): HttpResponse<kotlin.Int> {

            val localVariableAuthNames = listOf<String>("basicAuth")

            val localVariableBody = 
                    io.ktor.client.utils.EmptyContent

            val localVariableQuery = mutableMapOf<String, List<String>>()

            val localVariableHeaders = mutableMapOf<String, String>()

            val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/network/getprotocolversion",
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
        * This method configures a holding identity as a network participant by setting properties required for P2P messaging.
         * @param holdingidentityshorthash ID of the holding identity to set up 
         * @param hostedIdentitySetupRequest requestBody 
         * @return void
        */
        open suspend fun putNetworkSetupHoldingidentityshorthash(holdingidentityshorthash: kotlin.String, hostedIdentitySetupRequest: HostedIdentitySetupRequest): HttpResponse<Unit> {

            val localVariableAuthNames = listOf<String>("basicAuth")

            val localVariableBody = hostedIdentitySetupRequest

            val localVariableQuery = mutableMapOf<String, List<String>>()

            val localVariableHeaders = mutableMapOf<String, String>()

            val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.PUT,
            "/network/setup/{holdingidentityshorthash}".replace("{" + "holdingidentityshorthash" + "}", "$holdingidentityshorthash"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            )

            return jsonRequest(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
            ).wrap()
            }

        }
