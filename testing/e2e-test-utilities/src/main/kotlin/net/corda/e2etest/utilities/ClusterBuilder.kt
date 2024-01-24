package net.corda.e2etest.utilities

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.rest.annotations.RestApiVersion
import net.corda.tracing.configureTracing
import net.corda.tracing.trace
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Paths
import java.time.Instant

/**
 *  All functions return a [SimpleResponse] if not explicitly declared.
 *
 *  The caller needs to marshall the response body to json, and then query
 *  the json for the expected results.
 */
@Suppress("TooManyFunctions")
class ClusterBuilder(clusterInfo: ClusterInfo, val REST_API_VERSION_PATH: String) {

    internal companion object {
        init {
            configureTracing("E2eClusterTracing", null, null)
        }
    }

    private val logger = LoggerFactory.getLogger("ClusterBuilder - ${clusterInfo.id}")

    private val client: HttpsClient =
        UnirestHttpsClient(clusterInfo.rest.uri, clusterInfo.rest.user, clusterInfo.rest.password)

    data class VNodeCreateBody(
        val cpiFileChecksum: String,
        val x500Name: String,
        val cryptoDdlConnection: String?,
        val cryptoDmlConnection: String?,
        val uniquenessDdlConnection: String?,
        val uniquenessDmlConnection: String?,
        val vaultDdlConnection: String?,
        val vaultDmlConnection: String?
    )

    data class VNodeChangeConnectionStringsBody(
        val cryptoDdlConnection: String?,
        val cryptoDmlConnection: String?,
        val uniquenessDdlConnection: String?,
        val uniquenessDmlConnection: String?,
        val vaultDdlConnection: String?,
        val vaultDmlConnection: String?
    )

    data class ExternalDBConnectionParams(
        val cryptoDdlConnection: String? = null,
        val cryptoDmlConnection: String? = null,
        val uniquenessDdlConnection: String? = null,
        val uniquenessDmlConnection: String? = null,
        val vaultDdlConnection: String? = null,
        val vaultDmlConnection: String? = null
    )


    /** POST, but most useful for running flows */
    fun post(cmd: String, body: String) = client.post(cmd, body)

    fun put(cmd: String, body: String) = client.put(cmd, body)

    fun get(cmd: String) = client.get(cmd)

    fun delete(cmd: String) = client.delete(cmd)

    private fun uploadCpiResource(
        cmd: String,
        cpbResourceName: String?,
        groupPolicy: String,
        cpiName: String,
        cpiVersion: String
    ): SimpleResponse {
        return CpiLoader.get(cpbResourceName, groupPolicy, cpiName, cpiVersion).use {
            client.postMultiPart(cmd, emptyMap(), mapOf("upload" to HttpsClientFileUpload(it, cpiName)))
        }
    }

    private fun uploadUnmodifiedResource(cmd: String, resourceName: String): SimpleResponse {
        val fileName = Paths.get(resourceName).fileName.toString()
        return CpiLoader.getRawResource(resourceName).use {
            client.postMultiPart(cmd, emptyMap(), mapOf("upload" to HttpsClientFileUpload(it, fileName)))
        }
    }

    private fun uploadCertificateResource(cmd: String, resourceName: String, alias: String) =
        getInputStream(resourceName).uploadCertificateInputStream(
            cmd,
            alias,
            Paths.get(resourceName).fileName.toString()
        )


    private fun uploadCertificateFile(cmd: String, certificate: File, alias: String) =
        certificate.inputStream().uploadCertificateInputStream(cmd, alias, certificate.name)


    private fun InputStream.uploadCertificateInputStream(
        cmd: String, alias: String, fileName: String
    ) = use {
        client.putMultiPart(
            cmd,
            mapOf("alias" to alias),
            mapOf("certificate" to HttpsClientFileUpload(it, fileName))
        )
    }

    private fun getInputStream(resourceName: String) =
        this::class.java.getResource(resourceName)?.openStream()
            ?: throw FileNotFoundException("No such resource: '$resourceName'")

    fun importCertificate(resourceName: String, usage: String, alias: String) =
        uploadCertificateResource(
            "/api/$REST_API_VERSION_PATH/${REST_API_VERSION_PATH.certificatePath()}/cluster/$usage",
            resourceName,
            alias,
        )

    @Suppress("unused")
    // Used to test RestApiVersion.C5_0 CertificateRestResource from 5.1 cluster, remove after LTS
    fun deprecatedImportCertificate(resourceName: String, usage: String, alias: String) =
        uploadCertificateResource(
            "/api/${RestApiVersion.C5_0.versionPath}/certificates/cluster/$usage",
            resourceName,
            alias
        )

    /**
     * If [holdingIdentityId] is not specified, it will be uploaded as a cluster-level certificate.
     * If [holdingIdentityId] is specified, it will be uploaded as a vnode-level certificate under the specified vnode.
     */
    fun importCertificate(file: File, usage: String, alias: String, holdingIdentityId: String?): SimpleResponse {
        return if (holdingIdentityId == null) {
            importClusterCertificate(file, usage, alias)
        } else {
            importVnodeCertificate(file, usage, alias, holdingIdentityId)
        }
    }

    private fun importClusterCertificate(file: File, usage: String, alias: String) =
        uploadCertificateFile(
            "/api/$REST_API_VERSION_PATH/${REST_API_VERSION_PATH.certificatePath()}/cluster/$usage",
            file,
            alias,
        )

    private fun importVnodeCertificate(file: File, usage: String, alias: String, holdingIdentityId: String) =
        uploadCertificateFile(
            "/api/$REST_API_VERSION_PATH/${REST_API_VERSION_PATH.certificatePath()}/vnode/$holdingIdentityId/$usage",
            file,
            alias
        )

    fun getCertificateChain(usage: String, alias: String) =
        client.get("/api/$REST_API_VERSION_PATH/${REST_API_VERSION_PATH.certificatePath()}/cluster/$usage/$alias")

    /**
     * Returns the correct path for certificate rest resource based on the rest api version we use.
     */
    private fun String.certificatePath(): String =
        if (this == RestApiVersion.C5_0.versionPath) {
            "certificates"
        } else {
            "certificate"
        }

    @Suppress("unused")
    /** Assumes the resource *is* a CPB */
    fun cpbUpload(resourceName: String) = uploadUnmodifiedResource("/api/$REST_API_VERSION_PATH/cpi/", resourceName)

    /**
     * Assumes the resource is a CPB and converts it to CPI by adding a group policy file.
     *
     * @param cpbResourceName Name of the CPB resource
     * @param groupId Group ID to be used with the static group policy
     * @param staticMemberNames List of member names to be added to the static group policy
     * @param cpiName Name associated with the uploaded CPI
     * @param cpiVersion Optional. Version associated with the uploaded CPI
     * @param customGroupParameters Optional. Custom properties to be included in the group parameters of the static
     * network. May only include custom keys with the prefix "ext." or minimum platform version (with key
     * "corda.minimum.platform.version").
     * */
    @Suppress("LongParameterList")
    fun cpiUpload(
        cpbResourceName: String,
        groupId: String,
        staticMemberNames: List<String>,
        cpiName: String,
        cpiVersion: String = "1.0.0.0-SNAPSHOT",
        customGroupParameters: Map<String, Any> = emptyMap(),
    ) = cpiUpload(
        cpbResourceName,
        getDefaultStaticNetworkGroupPolicy(groupId, staticMemberNames, customGroupParameters = customGroupParameters),
        cpiName,
        cpiVersion
    )

    fun cpiUpload(
        cpbResourceName: String?,
        groupPolicy: String,
        cpiName: String,
        cpiVersion: String = "1.0.0.0-SNAPSHOT"
    ) = uploadCpiResource("/api/$REST_API_VERSION_PATH/cpi/", cpbResourceName, groupPolicy, cpiName, cpiVersion)

    @Suppress("unused")
    fun updateVirtualNodeState(holdingIdHash: String, newState: String) =
        put("/api/$REST_API_VERSION_PATH/virtualnode/$holdingIdHash/state/$newState", "")

    @Suppress("unused")
    /** Assumes the resource is a CPB and converts it to CPI by adding a group policy file */
    fun forceCpiUpload(
        cpbResourceName: String?,
        groupId: String,
        staticMemberNames: List<String>,
        cpiName: String,
        cpiVersion: String = "1.0.0.0-SNAPSHOT"
    ) =
        uploadCpiResource(
            "/api/$REST_API_VERSION_PATH/maintenance/virtualnode/forcecpiupload/",
            cpbResourceName,
            getDefaultStaticNetworkGroupPolicy(groupId, staticMemberNames),
            cpiName,
            cpiVersion
        )

    @Suppress("unused")
    /** Assumes the resource is a CPB and converts it to CPI by adding a group policy file */
    fun syncVirtualNode(virtualNodeShortId: String) =
        post("/api/$REST_API_VERSION_PATH/maintenance/virtualnode/$virtualNodeShortId/vault-schema/force-resync", "")

    /** Return the status for the given request id */
    fun cpiStatus(id: String) = client.get("/api/$REST_API_VERSION_PATH/cpi/status/$id")

    /** List all CPIs in the system */
    fun cpiList() = client.get("/api/$REST_API_VERSION_PATH/cpi")

    @Suppress("LongParameterList")
    private fun vNodeBody(
        cpiHash: String,
        x500Name: String,
        cryptoDdlConnection: String?,
        cryptoDmlConnection: String?,
        uniquenessDdlConnection: String?,
        uniquenessDmlConnection: String?,
        vaultDdlConnection: String?,
        vaultDmlConnection: String?
    ): String {
        val body = VNodeCreateBody(
            cpiHash,
            x500Name,
            cryptoDdlConnection,
            cryptoDmlConnection,
            uniquenessDdlConnection,
            uniquenessDmlConnection,
            vaultDdlConnection,
            vaultDmlConnection
        )
        return jacksonObjectMapper().writeValueAsString(body)
    }

    @Suppress("LongParameterList")
    private fun vNodeChangeConnectionStringsBody(
        cryptoDdlConnection: String?,
        cryptoDmlConnection: String?,
        uniquenessDdlConnection: String?,
        uniquenessDmlConnection: String?,
        vaultDdlConnection: String?,
        vaultDmlConnection: String?
    ): String {
        val body = VNodeChangeConnectionStringsBody(
            cryptoDdlConnection,
            cryptoDmlConnection,
            uniquenessDdlConnection,
            uniquenessDmlConnection,
            vaultDdlConnection,
            vaultDmlConnection
        )
        return jacksonObjectMapper().writeValueAsString(body)
    }

    private fun registerMemberBody(
        customMetadata: Map<String, String>,
    ): String {
        val context = (mapOf("corda.key.scheme" to "CORDA.ECDSA.SECP256R1") + customMetadata).map {
            "\"${it.key}\" : \"${it.value}\""
        }.joinToString()
        return """{ "context": { $context } }""".trimMargin()
    }

    private fun registerNotaryBody(
        notaryServiceName: String,
        customMetadata: Map<String, String>,
        isBackchainRequiredNotary: Boolean = true,
        notaryPlugin: String = "nonvalidating"
    ): String {
        val context = (mapOf(
            "corda.key.scheme" to "CORDA.ECDSA.SECP256R1",
            "corda.roles.0" to "notary",
            "corda.notary.service.name" to notaryServiceName,
            "corda.notary.service.flow.protocol.name" to "com.r3.corda.notary.plugin.$notaryPlugin",
            "corda.notary.service.flow.protocol.version.0" to "1",
            "corda.notary.service.backchain.required" to "$isBackchainRequiredNotary"
        ) + customMetadata)
            .map { "\"${it.key}\" : \"${it.value}\"" }
            .joinToString()
        return """{ "context": { $context } }""".trimMargin()
    }

    private fun createRbacRoleBody(roleName: String, groupVisibility: String?): String {
        val body: List<String> = mutableListOf(""""roleName": "$roleName"""").apply {
            groupVisibility?.let { add(""""groupVisibility": "$it"""") }
        }
        return body.joinToString(prefix = "{", postfix = "}")
    }

    @Suppress("LongParameterList")
    private fun createRbacUserBody(
        enabled: Boolean,
        fullName: String,
        password: String,
        loginName: String,
        parentGroup: String?,
        passwordExpiry: Instant?
    ): String {
        val body: List<String> = mutableListOf(
            """"enabled": "$enabled"""",
            """"fullName": "$fullName"""",
            """"initialPassword": "$password"""",
            """"loginName": "$loginName""""
        ).apply {
            parentGroup?.let { add(""""parentGroup": "$it"""") }
            passwordExpiry?.let { add(""""passwordExpiry": "$it"""") }
        }
        return body.joinToString(prefix = "{", postfix = "}")
    }

    @Suppress("unused")
    fun changeUserPasswordSelf(password: String) =
        post(
            "/api/$REST_API_VERSION_PATH/user/selfpassword",
            """{"password": "$password"}"""
        )

    @Suppress("unused")
    fun changeUserPasswordOther(username: String, password: String) =
        post(
            "/api/$REST_API_VERSION_PATH/user/otheruserpassword",
            """{"username": "$username", "password": "$password"}"""
        )

    private fun createPermissionBody(
        permissionString: String,
        permissionType: String,
        groupVisibility: String?,
        virtualNode: String?
    ): String {
        val body: List<String> = mutableListOf(
            """"permissionString": "$permissionString"""",
            """"permissionType": "$permissionType""""
        ).apply {
            groupVisibility?.let { add(""""groupVisibility": "$it"""") }
            virtualNode?.let { add(""""virtualNode": "$it"""") }
        }
        return body.joinToString(prefix = "{", postfix = "}")
    }

    private fun createBulkPermissionsBody(
        permissionsToCreate: Set<Pair<String, String>>,
        roleIds: Set<String>
    ): String {

        val body1 = permissionsToCreate.map { createPermissionBody(it.second, it.first, null, null) }

        val bodyStr1 = if (body1.isEmpty()) {
            ""
        } else {
            body1.joinToString(prefix = """"permissionsToCreate": [""", postfix = "]")
        }

        val body2 = roleIds.map { """"$it"""" }
        val bodyStr2 = if (body2.isEmpty()) {
            ""
        } else {
            body2.joinToString(prefix = """, "roleIds": [""", postfix = "]")
        }
        return "{$bodyStr1$bodyStr2}"
    }

    @Suppress("unused")
    /** Get schema SQL to create crypto DB */
    fun getCryptoSchemaSql() =
        get("/api/$REST_API_VERSION_PATH/virtualnode/create/db/crypto")

    @Suppress("unused")
    /** Get schema SQL to create uniqueness DB */
    fun getUniquenessSchemaSql() =
        get("/api/$REST_API_VERSION_PATH/virtualnode/create/db/uniqueness")

    @Suppress("unused")
    /** Get schema SQL to create vault and CPI DB */
    fun getVaultSchemaSql(cpiChecksum: String) =
        get("/api/$REST_API_VERSION_PATH/virtualnode/create/db/vault/$cpiChecksum")

    @Suppress("unused")
    /** Get schema SQL to update vault and CPI DB */
    fun getUpdateSchemaSql(virtualNodeShortHash: String, newCpiChecksum: String) =
        get("/api/$REST_API_VERSION_PATH/virtualnode/$virtualNodeShortHash/db/vault/$newCpiChecksum")

    /** Create a virtual node */
    @Suppress("LongParameterList")
    fun vNodeCreate(
        cpiHash: String,
        x500Name: String,
        externalDBConnectionParams: ExternalDBConnectionParams? = null
    ) =
        post(
            "/api/$REST_API_VERSION_PATH/virtualnode",
            vNodeBody(
                cpiHash,
                x500Name,
                externalDBConnectionParams?.cryptoDdlConnection,
                externalDBConnectionParams?.cryptoDmlConnection,
                externalDBConnectionParams?.uniquenessDdlConnection,
                externalDBConnectionParams?.uniquenessDmlConnection,
                externalDBConnectionParams?.vaultDdlConnection,
                externalDBConnectionParams?.vaultDmlConnection
            )
        )

    @Suppress("LongParameterList", "unused")
    fun vNodeChangeConnectionStrings(
        holdingIdShortHash: String,
        externalDBConnectionParams: ExternalDBConnectionParams? = null
    ) =
        put(
            "/api/$REST_API_VERSION_PATH/virtualnode/$holdingIdShortHash/db",
            vNodeChangeConnectionStringsBody(
                externalDBConnectionParams?.cryptoDdlConnection,
                externalDBConnectionParams?.cryptoDmlConnection,
                externalDBConnectionParams?.uniquenessDdlConnection,
                externalDBConnectionParams?.uniquenessDmlConnection,
                externalDBConnectionParams?.vaultDdlConnection,
                externalDBConnectionParams?.vaultDmlConnection
            )
        )


    @Suppress("unused")
    /** Trigger upgrade of a virtual node's CPI to the given  */
    fun vNodeUpgrade(virtualNodeShortHash: String, targetCpiFileChecksum: String) =
        put("/api/$REST_API_VERSION_PATH/virtualnode/$virtualNodeShortHash/cpi/$targetCpiFileChecksum", "")

    @Suppress("unused")
    fun getVNodeOperationStatus(requestId: String) =
        get("/api/$REST_API_VERSION_PATH/virtualnode/status/$requestId")

    /** List all virtual nodes */
    fun vNodeList() = client.get("/api/$REST_API_VERSION_PATH/virtualnode")

    /** List all virtual nodes */
    fun getVNode(holdingIdentityShortHash: String) =
        client.get("/api/$REST_API_VERSION_PATH/virtualnode/$holdingIdentityShortHash")

    fun getVNodeStatus(requestId: String) = client.get("/api/$REST_API_VERSION_PATH/virtualnode/status/$requestId")

    /**
     * Register a member to the network.
     *
     * Optional: Use [customMetadata] to specify custom properties which will be added to the member's
     * [net.corda.v5.membership.MemberInfo].
     * Keys of properties specified in [customMetadata] must have the prefix "ext.".
     *
     * KNOWN LIMITATION: Registering a notary static member will currently always provision a new
     * notary service. This is fine for now as we only support a 1-1 mapping from notary service to
     * notary vnode. It will need revisiting when 1-* is supported.
     */
    fun registerStaticMember(
        holdingIdShortHash: String,
        notaryServiceName: String? = null,
        customMetadata: Map<String, String> = emptyMap(),
        isBackchainRequiredNotary: Boolean = true,
        notaryPlugin: String = "nonvalidating"
    ) = register(
        holdingIdShortHash,
        if (notaryServiceName != null) registerNotaryBody(
            notaryServiceName,
            customMetadata,
            isBackchainRequiredNotary,
            notaryPlugin
        ) else registerMemberBody(
            customMetadata
        )
    )

    fun register(holdingIdShortHash: String, registrationContext: String) =
        post(
            "/api/$REST_API_VERSION_PATH/membership/$holdingIdShortHash",
            registrationContext
        )

    fun getRegistrationStatus(holdingIdShortHash: String) =
        get("/api/$REST_API_VERSION_PATH/membership/$holdingIdShortHash")

    fun getRegistrationStatus(holdingIdShortHash: String, registrationId: String) =
        get("/api/$REST_API_VERSION_PATH/membership/$holdingIdShortHash/$registrationId")

    fun addSoftHsmToVNode(holdingIdentityShortHash: String, category: String) =
        post("/api/$REST_API_VERSION_PATH/hsm/soft/$holdingIdentityShortHash/$category", body = "")

    fun createKey(holdingIdentityShortHash: String, alias: String, category: String, scheme: String) =
        if (REST_API_VERSION_PATH == RestApiVersion.C5_0.versionPath) {
            // Used to test RestApiVersion.C5_0 CertificateRestResource, remove after LTS
            deprecatedCreateKey(holdingIdentityShortHash, alias, category, scheme)
        } else {
            post(
                "/api/$REST_API_VERSION_PATH/key/$holdingIdentityShortHash/alias/$alias/category/$category/scheme/$scheme",
                body = ""
            )
        }

    // Used to test RestApiVersion.C5_0 KeysRestResource from 5.1 cluster, remove after LTS
    fun deprecatedCreateKey(holdingIdentityShortHash: String, alias: String, category: String, scheme: String) =
        post(
            "/api/${RestApiVersion.C5_0.versionPath}/keys/$holdingIdentityShortHash/alias/$alias/category/$category/scheme/$scheme",
            body = ""
        )

    fun getKey(tenantId: String, keyId: String) =
        get("/api/$REST_API_VERSION_PATH/${REST_API_VERSION_PATH.keyPath()}/$tenantId/$keyId")

    fun getKey(
        tenantId: String,
        category: String? = null,
        alias: String? = null,
        ids: List<String>? = null
    ): SimpleResponse {
        val queries = mutableListOf<String>().apply {
            category?.let { add("category=$it") }
            alias?.let { add("alias=$it") }
            ids?.let { it.forEach { id -> add("id=$id") } }
        }
        val queryStr = if (queries.isEmpty()) {
            ""
        } else {
            queries.joinToString(prefix = "?", separator = "&")
        }
        return get("/api/$REST_API_VERSION_PATH/${REST_API_VERSION_PATH.keyPath()}/$tenantId$queryStr")
    }

    /**
     * Returns the correct path for key rest resource based on the rest api version we use.
     */
    private fun String.keyPath(): String =
        if (this == RestApiVersion.C5_0.versionPath) {
            "keys"
        } else {
            "key"
        }

    /** Get status of a flow */
    fun flowStatus(holdingIdentityShortHash: String, clientRequestId: String) =
        get("/api/$REST_API_VERSION_PATH/flow/$holdingIdentityShortHash/$clientRequestId")

    /** Get status of multiple flows */
    fun multipleFlowStatus(holdingIdentityShortHash: String, status: String? = null) =
        get("/api/$REST_API_VERSION_PATH/flow/$holdingIdentityShortHash/?status=$status")

    /** Get result of a flow execution */
    fun flowResult(holdingIdentityShortHash: String, clientRequestId: String) =
        get("/api/$REST_API_VERSION_PATH/flow/$holdingIdentityShortHash/$clientRequestId/result")

    /** Get status of multiple flows */
    fun runnableFlowClasses(holdingIdentityShortHash: String) =
        get("/api/$REST_API_VERSION_PATH/flowclass/$holdingIdentityShortHash")

    @Suppress("unused")
    /** Create a new RBAC role */
    fun createRbacRole(roleName: String, groupVisibility: String? = null) =
        post("/api/$REST_API_VERSION_PATH/role", createRbacRoleBody(roleName, groupVisibility))

    /** Get all RBAC roles */
    fun getRbacRoles() = get("/api/$REST_API_VERSION_PATH/role")

    @Suppress("unused")
    /** Get a role for a specified ID */
    fun getRole(roleId: String) = get("/api/$REST_API_VERSION_PATH/role/$roleId")

    /** Create new RBAC user */
    @Suppress("LongParameterList", "unused")
    fun createRbacUser(
        enabled: Boolean,
        fullName: String,
        password: String,
        loginName: String,
        parentGroup: String? = null,
        passwordExpiry: Instant? = null
    ) =
        post(
            "/api/$REST_API_VERSION_PATH/user",
            createRbacUserBody(enabled, fullName, password, loginName, parentGroup, passwordExpiry)
        )

    @Suppress("unused")
    /** Get an RBAC user for a specific login name */
    fun getRbacUser(loginName: String) =
        get("/api/$REST_API_VERSION_PATH/user/$loginName")

    @Suppress("unused")
    /** Assign a specified role to a specified user */
    fun assignRoleToUser(loginName: String, roleId: String) =
        put("/api/$REST_API_VERSION_PATH/user/$loginName/role/$roleId", "")

    @Suppress("unused")
    /** Remove the specified role from a specified user */
    fun removeRoleFromUser(loginName: String, roleId: String) =
        delete("/api/$REST_API_VERSION_PATH/user/$loginName/role/$roleId")

    @Suppress("unused")
    /** Get a summary of the user's permissions */
    fun getPermissionSummary(loginName: String) =
        get("/api/$REST_API_VERSION_PATH/user/$loginName/permissionsummary")

    @Suppress("unused")
    /** Create a new permission */
    fun createPermission(
        permissionString: String,
        permissionType: String,
        groupVisibility: String? = null,
        virtualNode: String? = null
    ) =
        post(
            "/api/$REST_API_VERSION_PATH/permission",
            createPermissionBody(permissionString, permissionType, groupVisibility, virtualNode)
        )

    @Suppress("unused")
    /** Create a set of permissions and optionally assigns them to existing roles */
    fun createBulkPermissions(
        permissionsToCreate: Set<Pair<String, String>>,
        roleIds: Set<String>
    ) =
        post("/api/$REST_API_VERSION_PATH/permission/bulk", createBulkPermissionsBody(permissionsToCreate, roleIds))

    @Suppress("unused")
    /** Get the permissions which satisfy the query */
    fun getPermissionByQuery(
        limit: Int,
        permissionType: String,
        permissionStringPrefix: String? = null
    ): SimpleResponse {
        val queries: List<String> = mutableListOf("limit=$limit", "permissiontype=$permissionType").apply {
            permissionStringPrefix?.let { add("permissionstringprefix=$it") }
        }
        val queryStr = queries.joinToString(prefix = "?", separator = "&")
        return get("/api/$REST_API_VERSION_PATH/permission$queryStr")
    }

    @Suppress("unused")
    /** Get the permission associated with a specific ID */
    fun getPermissionById(permissionId: String) =
        get("/api/$REST_API_VERSION_PATH/permission/$permissionId")

    @Suppress("unused")
    /** Add the specified permission to the specified role */
    fun assignPermissionToRole(roleId: String, permissionId: String) =
        put("/api/$REST_API_VERSION_PATH/role/$roleId/permission/$permissionId", "")

    @Suppress("unused")
    /** Remove the specified permission from the specified role */
    fun removePermissionFromRole(roleId: String, permissionId: String) =
        delete("/api/$REST_API_VERSION_PATH/role/$roleId/permission/$permissionId")

    /** Start a flow */
    fun flowStart(
        holdingIdentityShortHash: String,
        clientRequestId: String,
        flowClassName: String,
        requestData: String
    ): SimpleResponse {
        return trace("flowStart") {
            traceVirtualNodeId(holdingIdentityShortHash)
            traceRequestId(clientRequestId)
            logger.info("Sending flowStart, vNode: '$holdingIdentityShortHash', " +
                    "clientRequestId: '$clientRequestId', traceId: '$traceIdString'")
            post(
                "/api/$REST_API_VERSION_PATH/flow/$holdingIdentityShortHash",
                flowStartBody(clientRequestId, flowClassName, requestData)
            )
        }
    }

    private fun flowStartBody(clientRequestId: String, flowClassName: String, requestData: String) =
        """{ "clientRequestId" : "$clientRequestId", "flowClassName" : "$flowClassName", "requestBody" : 
            |"$requestData" }
        """.trimMargin()

    /** Get cluster configuration for the specified section */
    fun getConfig(section: String) = get("/api/$REST_API_VERSION_PATH/config/$section")

    /** Update the cluster configuration for the specified section and versions with unescaped Json */
    fun putConfig(
        config: String,
        section: String,
        configVersion: String,
        schemaMajorVersion: String,
        schemaMinorVersion: String
    ): SimpleResponse {
        val payload = """
            {
                "config": $config,
                "schemaVersion": {
                  "major": "$schemaMajorVersion",
                  "minor": "$schemaMinorVersion"
                },
                "section": "$section",
                "version": "$configVersion"
            }
        """.trimIndent()

        return put("/api/$REST_API_VERSION_PATH/config", payload)
    }

    fun configureNetworkParticipant(
        holdingIdentityShortHash: String,
        sessionKeyId: String,
        sessionCertAlias: String? = null
    ): SimpleResponse {
        val sessionKeysSection = if (sessionCertAlias == null) {
            """
                    "sessionKeysAndCertificates": [{
                      "preferred": true,
                      "sessionKeyId": "$sessionKeyId"
                    }]
            """.trim()
        } else {
            """
                    "sessionKeysAndCertificates": [{
                      "preferred": true,
                      "sessionKeyId": "$sessionKeyId",
                      "sessionCertificateChainAlias": "$sessionCertAlias"
                    }]
            """.trim()
        }
        val body =
            """
                {
                    "p2pTlsCertificateChainAlias": "$CERT_ALIAS_P2P",
                    "useClusterLevelTlsCertificateAndKey": true,
                    $sessionKeysSection
                }
            """.trimIndent()
        return put(
            "/api/$REST_API_VERSION_PATH/network/setup/$holdingIdentityShortHash",
            body = body
        )
    }

    fun doRotateCryptoUnmanagedWrappingKeys(
        oldKeyAlias: String,
        newKeyAlias: String
    ): SimpleResponse {
        return post(
            "/api/$REST_API_VERSION_PATH/wrappingkey/unmanaged/rotation/${oldKeyAlias}",
            body = """{
                "newKeyAlias": "$newKeyAlias"
            }""".trimMargin()
        )
    }

    fun getCryptoUnmanagedWrappingKeysRotationStatus(keyAlias: String): SimpleResponse {
        return get(
            "/api/$REST_API_VERSION_PATH/wrappingkey/unmanaged/rotation/${keyAlias}",
        )
    }

    fun getWrappingKeysProtocolVersion(): SimpleResponse {
        return get(
            "/api/$REST_API_VERSION_PATH/wrappingkey/getprotocolversion",
        )
    }
}

fun <T> cluster(
    initialize: ClusterBuilder.() -> T,
): T = DEFAULT_CLUSTER.cluster(initialize)

fun <T> ClusterInfo.cluster(
    initialize: ClusterBuilder.() -> T
): T = ClusterBuilder(this, restApiVersion.versionPath).let(initialize)
