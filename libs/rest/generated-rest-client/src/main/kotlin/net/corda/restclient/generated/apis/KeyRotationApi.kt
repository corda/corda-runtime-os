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

package net.corda.restclient.generated.apis

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.HttpUrl


import com.fasterxml.jackson.annotation.JsonProperty

import net.corda.restclient.generated.infrastructure.ApiClient
import net.corda.restclient.generated.infrastructure.ApiResponse
import net.corda.restclient.generated.infrastructure.ClientException
import net.corda.restclient.generated.infrastructure.ClientError
import net.corda.restclient.generated.infrastructure.ServerException
import net.corda.restclient.generated.infrastructure.ServerError
import net.corda.restclient.generated.infrastructure.MultiValueMap
import net.corda.restclient.generated.infrastructure.PartConfig
import net.corda.restclient.generated.infrastructure.RequestConfig
import net.corda.restclient.generated.infrastructure.RequestMethod
import net.corda.restclient.generated.infrastructure.ResponseType
import net.corda.restclient.generated.infrastructure.Success
import net.corda.restclient.generated.infrastructure.toMultiValue

class KeyRotationApi(basePath: kotlin.String = defaultBasePath, client: OkHttpClient = ApiClient.defaultClient) : ApiClient(basePath, client) {
    companion object {
        @JvmStatic
        val defaultBasePath: String by lazy {
            System.getProperties().getProperty(ApiClient.baseUrlKey, "/api/v5_3")
        }
    }

    /**
     * 
     * 
     * @return kotlin.Int
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     * @throws UnsupportedOperationException If the API returns an informational or redirection response
     * @throws ClientException If the API returns a client error response
     * @throws ServerException If the API returns a server error response
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getWrappingkeyGetprotocolversion() : kotlin.Int {
        val localVarResponse = getWrappingkeyGetprotocolversionWithHttpInfo()

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as kotlin.Int
            ResponseType.Informational -> throw UnsupportedOperationException("Client does not support Informational responses.")
            ResponseType.Redirection -> throw UnsupportedOperationException("Client does not support Redirection responses.")
            ResponseType.ClientError -> {
                val localVarError = localVarResponse as ClientError<*>
                throw ClientException("Client error : ${localVarError.statusCode} ${localVarError.message.orEmpty()}", localVarError.statusCode, localVarResponse)
            }
            ResponseType.ServerError -> {
                val localVarError = localVarResponse as ServerError<*>
                throw ServerException("Server error : ${localVarError.statusCode} ${localVarError.message.orEmpty()} ${localVarError.body}", localVarError.statusCode, localVarResponse)
            }
        }
    }

    /**
     * 
     * 
     * @return ApiResponse<kotlin.Int?>
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun getWrappingkeyGetprotocolversionWithHttpInfo() : ApiResponse<kotlin.Int?> {
        val localVariableConfig = getWrappingkeyGetprotocolversionRequestConfig()

        return request<Unit, kotlin.Int>(
            localVariableConfig
        )
    }

    /**
     * To obtain the request config of the operation getWrappingkeyGetprotocolversion
     *
     * @return RequestConfig
     */
    fun getWrappingkeyGetprotocolversionRequestConfig() : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json"

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/wrappingkey/getprotocolversion",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    /**
     * 
     * This method gets the status of the latest key rotation for [tenantId].
     * @param tenantid Can either be a holding identity ID, the value &#39;master&#39; for master wrapping key or one of the values &#39;rest&#39;, &#39;crypto&#39; for corresponding cluster-level services.  NOTE: the &#39;p2p&#39; tenant ID does not support key rotation and should not be used.
     * @return net.corda.crypto.rest.response.KeyRotationStatusResponse
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     * @throws UnsupportedOperationException If the API returns an informational or redirection response
     * @throws ClientException If the API returns a client error response
     * @throws ServerException If the API returns a server error response
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getWrappingkeyRotationTenantid(tenantid: kotlin.String) : net.corda.crypto.rest.response.KeyRotationStatusResponse {
        val localVarResponse = getWrappingkeyRotationTenantidWithHttpInfo(tenantid = tenantid)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as net.corda.crypto.rest.response.KeyRotationStatusResponse
            ResponseType.Informational -> throw UnsupportedOperationException("Client does not support Informational responses.")
            ResponseType.Redirection -> throw UnsupportedOperationException("Client does not support Redirection responses.")
            ResponseType.ClientError -> {
                val localVarError = localVarResponse as ClientError<*>
                throw ClientException("Client error : ${localVarError.statusCode} ${localVarError.message.orEmpty()}", localVarError.statusCode, localVarResponse)
            }
            ResponseType.ServerError -> {
                val localVarError = localVarResponse as ServerError<*>
                throw ServerException("Server error : ${localVarError.statusCode} ${localVarError.message.orEmpty()} ${localVarError.body}", localVarError.statusCode, localVarResponse)
            }
        }
    }

    /**
     * 
     * This method gets the status of the latest key rotation for [tenantId].
     * @param tenantid Can either be a holding identity ID, the value &#39;master&#39; for master wrapping key or one of the values &#39;rest&#39;, &#39;crypto&#39; for corresponding cluster-level services.  NOTE: the &#39;p2p&#39; tenant ID does not support key rotation and should not be used.
     * @return ApiResponse<net.corda.crypto.rest.response.KeyRotationStatusResponse?>
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun getWrappingkeyRotationTenantidWithHttpInfo(tenantid: kotlin.String) : ApiResponse<net.corda.crypto.rest.response.KeyRotationStatusResponse?> {
        val localVariableConfig = getWrappingkeyRotationTenantidRequestConfig(tenantid = tenantid)

        return request<Unit, net.corda.crypto.rest.response.KeyRotationStatusResponse>(
            localVariableConfig
        )
    }

    /**
     * To obtain the request config of the operation getWrappingkeyRotationTenantid
     *
     * @param tenantid Can either be a holding identity ID, the value &#39;master&#39; for master wrapping key or one of the values &#39;rest&#39;, &#39;crypto&#39; for corresponding cluster-level services.  NOTE: the &#39;p2p&#39; tenant ID does not support key rotation and should not be used.
     * @return RequestConfig
     */
    fun getWrappingkeyRotationTenantidRequestConfig(tenantid: kotlin.String) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json"

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/wrappingkey/rotation/{tenantid}".replace("{"+"tenantid"+"}", encodeURIComponent(tenantid.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    /**
     * 
     * This method enables to rotate master wrapping key or all wrapping keys for tenantId (holding identity ID or cluster-level tenant).
     * @param tenantid Can either be a holding identity ID, the value &#39;master&#39; for master wrapping key or one of the values &#39;rest&#39;, &#39;crypto&#39; for corresponding cluster-level services.  NOTE: the &#39;p2p&#39; tenant ID does not support key rotation and should not be used.
     * @return net.corda.crypto.rest.response.KeyRotationResponse
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     * @throws UnsupportedOperationException If the API returns an informational or redirection response
     * @throws ClientException If the API returns a client error response
     * @throws ServerException If the API returns a server error response
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun postWrappingkeyRotationTenantid(tenantid: kotlin.String) : net.corda.crypto.rest.response.KeyRotationResponse {
        val localVarResponse = postWrappingkeyRotationTenantidWithHttpInfo(tenantid = tenantid)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as net.corda.crypto.rest.response.KeyRotationResponse
            ResponseType.Informational -> throw UnsupportedOperationException("Client does not support Informational responses.")
            ResponseType.Redirection -> throw UnsupportedOperationException("Client does not support Redirection responses.")
            ResponseType.ClientError -> {
                val localVarError = localVarResponse as ClientError<*>
                throw ClientException("Client error : ${localVarError.statusCode} ${localVarError.message.orEmpty()}", localVarError.statusCode, localVarResponse)
            }
            ResponseType.ServerError -> {
                val localVarError = localVarResponse as ServerError<*>
                throw ServerException("Server error : ${localVarError.statusCode} ${localVarError.message.orEmpty()} ${localVarError.body}", localVarError.statusCode, localVarResponse)
            }
        }
    }

    /**
     * 
     * This method enables to rotate master wrapping key or all wrapping keys for tenantId (holding identity ID or cluster-level tenant).
     * @param tenantid Can either be a holding identity ID, the value &#39;master&#39; for master wrapping key or one of the values &#39;rest&#39;, &#39;crypto&#39; for corresponding cluster-level services.  NOTE: the &#39;p2p&#39; tenant ID does not support key rotation and should not be used.
     * @return ApiResponse<net.corda.crypto.rest.response.KeyRotationResponse?>
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun postWrappingkeyRotationTenantidWithHttpInfo(tenantid: kotlin.String) : ApiResponse<net.corda.crypto.rest.response.KeyRotationResponse?> {
        val localVariableConfig = postWrappingkeyRotationTenantidRequestConfig(tenantid = tenantid)

        return request<Unit, net.corda.crypto.rest.response.KeyRotationResponse>(
            localVariableConfig
        )
    }

    /**
     * To obtain the request config of the operation postWrappingkeyRotationTenantid
     *
     * @param tenantid Can either be a holding identity ID, the value &#39;master&#39; for master wrapping key or one of the values &#39;rest&#39;, &#39;crypto&#39; for corresponding cluster-level services.  NOTE: the &#39;p2p&#39; tenant ID does not support key rotation and should not be used.
     * @return RequestConfig
     */
    fun postWrappingkeyRotationTenantidRequestConfig(tenantid: kotlin.String) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json"

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/wrappingkey/rotation/{tenantid}".replace("{"+"tenantid"+"}", encodeURIComponent(tenantid.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }


    private fun encodeURIComponent(uriComponent: kotlin.String): kotlin.String =
        HttpUrl.Builder().scheme("http").host("localhost").addPathSegment(uriComponent).build().encodedPathSegments[0]
}
