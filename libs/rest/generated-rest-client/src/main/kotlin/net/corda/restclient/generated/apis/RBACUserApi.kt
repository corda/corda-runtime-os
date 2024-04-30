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

import net.corda.restclient.generated.models.PostUserSelfpasswordRequest

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

class RBACUserApi(basePath: kotlin.String = defaultBasePath, client: OkHttpClient = ApiClient.defaultClient) : ApiClient(basePath, client) {
    companion object {
        @JvmStatic
        val defaultBasePath: String by lazy {
            System.getProperties().getProperty(ApiClient.baseUrlKey, "/api/v5_3")
        }
    }

    /**
     * 
     * This method removes the specified role from the specified user.
     * @param loginname The login name of the user
     * @param roleid The ID of the role to remove from the user
     * @return net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     * @throws UnsupportedOperationException If the API returns an informational or redirection response
     * @throws ClientException If the API returns a client error response
     * @throws ServerException If the API returns a server error response
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun deleteUserLoginnameRoleRoleid(loginname: kotlin.String, roleid: kotlin.String) : net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType {
        val localVarResponse = deleteUserLoginnameRoleRoleidWithHttpInfo(loginname = loginname, roleid = roleid)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
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
     * This method removes the specified role from the specified user.
     * @param loginname The login name of the user
     * @param roleid The ID of the role to remove from the user
     * @return ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?>
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun deleteUserLoginnameRoleRoleidWithHttpInfo(loginname: kotlin.String, roleid: kotlin.String) : ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?> {
        val localVariableConfig = deleteUserLoginnameRoleRoleidRequestConfig(loginname = loginname, roleid = roleid)

        return request<Unit, net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType>(
            localVariableConfig
        )
    }

    /**
     * To obtain the request config of the operation deleteUserLoginnameRoleRoleid
     *
     * @param loginname The login name of the user
     * @param roleid The ID of the role to remove from the user
     * @return RequestConfig
     */
    fun deleteUserLoginnameRoleRoleidRequestConfig(loginname: kotlin.String, roleid: kotlin.String) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json"

        return RequestConfig(
            method = RequestMethod.DELETE,
            path = "/user/{loginname}/role/{roleid}".replace("{"+"loginname"+"}", encodeURIComponent(loginname.toString())).replace("{"+"roleid"+"}", encodeURIComponent(roleid.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
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
    fun getUserGetprotocolversion() : kotlin.Int {
        val localVarResponse = getUserGetprotocolversionWithHttpInfo()

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
    fun getUserGetprotocolversionWithHttpInfo() : ApiResponse<kotlin.Int?> {
        val localVariableConfig = getUserGetprotocolversionRequestConfig()

        return request<Unit, kotlin.Int>(
            localVariableConfig
        )
    }

    /**
     * To obtain the request config of the operation getUserGetprotocolversion
     *
     * @return RequestConfig
     */
    fun getUserGetprotocolversionRequestConfig() : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json"

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/user/getprotocolversion",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    /**
     * 
     * This method returns a user based on the specified login name.
     * @param loginname The login name of the user to be returned
     * @return net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     * @throws UnsupportedOperationException If the API returns an informational or redirection response
     * @throws ClientException If the API returns a client error response
     * @throws ServerException If the API returns a server error response
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getUserLoginname(loginname: kotlin.String) : net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType {
        val localVarResponse = getUserLoginnameWithHttpInfo(loginname = loginname)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
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
     * This method returns a user based on the specified login name.
     * @param loginname The login name of the user to be returned
     * @return ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?>
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun getUserLoginnameWithHttpInfo(loginname: kotlin.String) : ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?> {
        val localVariableConfig = getUserLoginnameRequestConfig(loginname = loginname)

        return request<Unit, net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType>(
            localVariableConfig
        )
    }

    /**
     * To obtain the request config of the operation getUserLoginname
     *
     * @param loginname The login name of the user to be returned
     * @return RequestConfig
     */
    fun getUserLoginnameRequestConfig(loginname: kotlin.String) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json"

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/user/{loginname}".replace("{"+"loginname"+"}", encodeURIComponent(loginname.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    /**
     * 
     * This method returns a summary of the user&#39;s permissions.
     * @param loginname The login name of the user
     * @return net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     * @throws UnsupportedOperationException If the API returns an informational or redirection response
     * @throws ClientException If the API returns a client error response
     * @throws ServerException If the API returns a server error response
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getUserLoginnamePermissionsummary(loginname: kotlin.String) : net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType {
        val localVarResponse = getUserLoginnamePermissionsummaryWithHttpInfo(loginname = loginname)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType
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
     * This method returns a summary of the user&#39;s permissions.
     * @param loginname The login name of the user
     * @return ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType?>
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun getUserLoginnamePermissionsummaryWithHttpInfo(loginname: kotlin.String) : ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType?> {
        val localVariableConfig = getUserLoginnamePermissionsummaryRequestConfig(loginname = loginname)

        return request<Unit, net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType>(
            localVariableConfig
        )
    }

    /**
     * To obtain the request config of the operation getUserLoginnamePermissionsummary
     *
     * @param loginname The login name of the user
     * @return RequestConfig
     */
    fun getUserLoginnamePermissionsummaryRequestConfig(loginname: kotlin.String) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json"

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/user/{loginname}/permissionsummary".replace("{"+"loginname"+"}", encodeURIComponent(loginname.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    /**
     * 
     * This method creates a new user.
     * @param netCordaLibsPermissionsEndpointsV1UserTypesCreateUserType requestBody
     * @return net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     * @throws UnsupportedOperationException If the API returns an informational or redirection response
     * @throws ClientException If the API returns a client error response
     * @throws ServerException If the API returns a server error response
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun postUser(netCordaLibsPermissionsEndpointsV1UserTypesCreateUserType: net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType) : net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType {
        val localVarResponse = postUserWithHttpInfo(netCordaLibsPermissionsEndpointsV1UserTypesCreateUserType = netCordaLibsPermissionsEndpointsV1UserTypesCreateUserType)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
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
     * This method creates a new user.
     * @param netCordaLibsPermissionsEndpointsV1UserTypesCreateUserType requestBody
     * @return ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?>
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun postUserWithHttpInfo(netCordaLibsPermissionsEndpointsV1UserTypesCreateUserType: net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType) : ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?> {
        val localVariableConfig = postUserRequestConfig(netCordaLibsPermissionsEndpointsV1UserTypesCreateUserType = netCordaLibsPermissionsEndpointsV1UserTypesCreateUserType)

        return request<net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType, net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType>(
            localVariableConfig
        )
    }

    /**
     * To obtain the request config of the operation postUser
     *
     * @param netCordaLibsPermissionsEndpointsV1UserTypesCreateUserType requestBody
     * @return RequestConfig
     */
    fun postUserRequestConfig(netCordaLibsPermissionsEndpointsV1UserTypesCreateUserType: net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType) : RequestConfig<net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType> {
        val localVariableBody = netCordaLibsPermissionsEndpointsV1UserTypesCreateUserType
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json"

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/user",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    /**
     * 
     * This method updates another user&#39;s password, only usable by admin.
     * @param netCordaRestclientDtoChangeOtherUserPasswordWrapperRequest requestBody
     * @return net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     * @throws UnsupportedOperationException If the API returns an informational or redirection response
     * @throws ClientException If the API returns a client error response
     * @throws ServerException If the API returns a server error response
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun postUserOtheruserpassword(netCordaRestclientDtoChangeOtherUserPasswordWrapperRequest: net.corda.restclient.dto.ChangeOtherUserPasswordWrapperRequest) : net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType {
        val localVarResponse = postUserOtheruserpasswordWithHttpInfo(netCordaRestclientDtoChangeOtherUserPasswordWrapperRequest = netCordaRestclientDtoChangeOtherUserPasswordWrapperRequest)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
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
     * This method updates another user&#39;s password, only usable by admin.
     * @param netCordaRestclientDtoChangeOtherUserPasswordWrapperRequest requestBody
     * @return ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?>
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun postUserOtheruserpasswordWithHttpInfo(netCordaRestclientDtoChangeOtherUserPasswordWrapperRequest: net.corda.restclient.dto.ChangeOtherUserPasswordWrapperRequest) : ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?> {
        val localVariableConfig = postUserOtheruserpasswordRequestConfig(netCordaRestclientDtoChangeOtherUserPasswordWrapperRequest = netCordaRestclientDtoChangeOtherUserPasswordWrapperRequest)

        return request<net.corda.restclient.dto.ChangeOtherUserPasswordWrapperRequest, net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType>(
            localVariableConfig
        )
    }

    /**
     * To obtain the request config of the operation postUserOtheruserpassword
     *
     * @param netCordaRestclientDtoChangeOtherUserPasswordWrapperRequest requestBody
     * @return RequestConfig
     */
    fun postUserOtheruserpasswordRequestConfig(netCordaRestclientDtoChangeOtherUserPasswordWrapperRequest: net.corda.restclient.dto.ChangeOtherUserPasswordWrapperRequest) : RequestConfig<net.corda.restclient.dto.ChangeOtherUserPasswordWrapperRequest> {
        val localVariableBody = netCordaRestclientDtoChangeOtherUserPasswordWrapperRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json"

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/user/otheruserpassword",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    /**
     * 
     * This method updates a users own password.
     * @param postUserSelfpasswordRequest requestBody
     * @return net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     * @throws UnsupportedOperationException If the API returns an informational or redirection response
     * @throws ClientException If the API returns a client error response
     * @throws ServerException If the API returns a server error response
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun postUserSelfpassword(postUserSelfpasswordRequest: PostUserSelfpasswordRequest) : net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType {
        val localVarResponse = postUserSelfpasswordWithHttpInfo(postUserSelfpasswordRequest = postUserSelfpasswordRequest)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
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
     * This method updates a users own password.
     * @param postUserSelfpasswordRequest requestBody
     * @return ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?>
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun postUserSelfpasswordWithHttpInfo(postUserSelfpasswordRequest: PostUserSelfpasswordRequest) : ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?> {
        val localVariableConfig = postUserSelfpasswordRequestConfig(postUserSelfpasswordRequest = postUserSelfpasswordRequest)

        return request<PostUserSelfpasswordRequest, net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType>(
            localVariableConfig
        )
    }

    /**
     * To obtain the request config of the operation postUserSelfpassword
     *
     * @param postUserSelfpasswordRequest requestBody
     * @return RequestConfig
     */
    fun postUserSelfpasswordRequestConfig(postUserSelfpasswordRequest: PostUserSelfpasswordRequest) : RequestConfig<PostUserSelfpasswordRequest> {
        val localVariableBody = postUserSelfpasswordRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json"

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/user/selfpassword",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    /**
     * 
     * This method assigns a specified role to a specified user.
     * @param loginname The login name of the user
     * @param roleid The ID of the role to assign to the user
     * @return net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     * @throws UnsupportedOperationException If the API returns an informational or redirection response
     * @throws ClientException If the API returns a client error response
     * @throws ServerException If the API returns a server error response
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun putUserLoginnameRoleRoleid(loginname: kotlin.String, roleid: kotlin.String) : net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType {
        val localVarResponse = putUserLoginnameRoleRoleidWithHttpInfo(loginname = loginname, roleid = roleid)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
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
     * This method assigns a specified role to a specified user.
     * @param loginname The login name of the user
     * @param roleid The ID of the role to assign to the user
     * @return ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?>
     * @throws IllegalStateException If the request is not correctly configured
     * @throws IOException Rethrows the OkHttp execute method exception
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun putUserLoginnameRoleRoleidWithHttpInfo(loginname: kotlin.String, roleid: kotlin.String) : ApiResponse<net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType?> {
        val localVariableConfig = putUserLoginnameRoleRoleidRequestConfig(loginname = loginname, roleid = roleid)

        return request<Unit, net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType>(
            localVariableConfig
        )
    }

    /**
     * To obtain the request config of the operation putUserLoginnameRoleRoleid
     *
     * @param loginname The login name of the user
     * @param roleid The ID of the role to assign to the user
     * @return RequestConfig
     */
    fun putUserLoginnameRoleRoleidRequestConfig(loginname: kotlin.String, roleid: kotlin.String) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json"

        return RequestConfig(
            method = RequestMethod.PUT,
            path = "/user/{loginname}/role/{roleid}".replace("{"+"loginname"+"}", encodeURIComponent(loginname.toString())).replace("{"+"roleid"+"}", encodeURIComponent(roleid.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }


    private fun encodeURIComponent(uriComponent: kotlin.String): kotlin.String =
        HttpUrl.Builder().scheme("http").host("localhost").addPathSegment(uriComponent).build().encodedPathSegments[0]
}
