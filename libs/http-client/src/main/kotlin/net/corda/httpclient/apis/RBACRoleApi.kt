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

import net.corda.httpclient.models.CreateRoleType
import net.corda.httpclient.models.RoleResponseType

import net.corda.httpclient.infrastructure.*
import io.ktor.client.HttpClientConfig
import io.ktor.client.request.forms.formData
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.ParametersBuilder

    open class RBACRoleApi(
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
        * This method removes the specified permission from the specified role.
         * @param roleid Identifier for an existing role 
         * @param permissionid Identifier for an existing permission 
         * @return RoleResponseType
        */
            @Suppress("UNCHECKED_CAST")
        open suspend fun deleteRoleRoleidPermissionPermissionid(roleid: kotlin.String, permissionid: kotlin.String): HttpResponse<RoleResponseType> {

            val localVariableAuthNames = listOf<String>("basicAuth")

            val localVariableBody = 
                    io.ktor.client.utils.EmptyContent

            val localVariableQuery = mutableMapOf<String, List<String>>()

            val localVariableHeaders = mutableMapOf<String, String>()

            val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.DELETE,
            "/role/{roleid}/permission/{permissionid}".replace("{" + "roleid" + "}", "$roleid").replace("{" + "permissionid" + "}", "$permissionid"),
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
        * This method returns an array with information about all roles in the permission system.
         * @return kotlin.collections.Set<RoleResponseType>
        */
            @Suppress("UNCHECKED_CAST")
        open suspend fun getRole(): HttpResponse<kotlin.collections.Set<RoleResponseType>> {

            val localVariableAuthNames = listOf<String>("basicAuth")

            val localVariableBody = 
                    io.ktor.client.utils.EmptyContent

            val localVariableQuery = mutableMapOf<String, List<String>>()

            val localVariableHeaders = mutableMapOf<String, String>()

            val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/role",
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
        * 
         * @return kotlin.Int
        */
            @Suppress("UNCHECKED_CAST")
        open suspend fun getRoleGetprotocolversion(): HttpResponse<kotlin.Int> {

            val localVariableAuthNames = listOf<String>("basicAuth")

            val localVariableBody = 
                    io.ktor.client.utils.EmptyContent

            val localVariableQuery = mutableMapOf<String, List<String>>()

            val localVariableHeaders = mutableMapOf<String, String>()

            val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/role/getprotocolversion",
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
        * This method gets the details of a role specified by its ID.
         * @param id ID of the role to be returned. 
         * @return RoleResponseType
        */
            @Suppress("UNCHECKED_CAST")
        open suspend fun getRoleId(id: kotlin.String): HttpResponse<RoleResponseType> {

            val localVariableAuthNames = listOf<String>("basicAuth")

            val localVariableBody = 
                    io.ktor.client.utils.EmptyContent

            val localVariableQuery = mutableMapOf<String, List<String>>()

            val localVariableHeaders = mutableMapOf<String, String>()

            val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/role/{id}".replace("{" + "id" + "}", "$id"),
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
        * The method creates a new role in the RBAC permission system.
         * @param createRoleType requestBody 
         * @return RoleResponseType
        */
            @Suppress("UNCHECKED_CAST")
        open suspend fun postRole(createRoleType: CreateRoleType): HttpResponse<RoleResponseType> {

            val localVariableAuthNames = listOf<String>("basicAuth")

            val localVariableBody = createRoleType

            val localVariableQuery = mutableMapOf<String, List<String>>()

            val localVariableHeaders = mutableMapOf<String, String>()

            val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/role",
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

        /**
        * 
        * This method adds the specified permission to the specified role.
         * @param roleid Identifier for an existing role 
         * @param permissionid Identifier for an existing permission 
         * @return RoleResponseType
        */
            @Suppress("UNCHECKED_CAST")
        open suspend fun putRoleRoleidPermissionPermissionid(roleid: kotlin.String, permissionid: kotlin.String): HttpResponse<RoleResponseType> {

            val localVariableAuthNames = listOf<String>("basicAuth")

            val localVariableBody = 
                    io.ktor.client.utils.EmptyContent

            val localVariableQuery = mutableMapOf<String, List<String>>()

            val localVariableHeaders = mutableMapOf<String, String>()

            val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.PUT,
            "/role/{roleid}/permission/{permissionid}".replace("{" + "roleid" + "}", "$roleid").replace("{" + "permissionid" + "}", "$permissionid"),
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
