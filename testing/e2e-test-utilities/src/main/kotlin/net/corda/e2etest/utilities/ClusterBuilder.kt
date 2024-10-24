package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.JsonNode
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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 *  All functions return a [SimpleResponse] if not explicitly declared.
 *
 *  The caller needs to marshall the response body to json, and then query
 *  the json for the expected results.
 */
@Suppress("TooManyFunctions", "LargeClass")
class ClusterBuilder(clusterInfo: ClusterInfo, val REST_API_VERSION_PATH: String) {

    internal companion object {
        init {
            configureTracing("E2eClusterTracing", null, null, emptyMap())
        }
        private const val VNODE_CREATOR_USER = "vnodecreatoruser"
        private val lock = ReentrantLock()
    }

    private val logger = LoggerFactory.getLogger("ClusterBuilder - ${clusterInfo.id}")

    val initialClient: HttpsClient =
        UnirestHttpsClient(clusterInfo.rest.uri, clusterInfo.rest.user, clusterInfo.rest.password)

    private fun checkVNodeCreatorRoleExists(): JsonNode? {
        return getRbacRoles().body.toJson().firstOrNull { it["roleName"].toString().contains("VNodeCreatorRole") }
    }

    private fun checkVNodeCreatorUserDoesNotExist(): Boolean {
        return getRbacUser(VNODE_CREATOR_USER).body.contains("User '$VNODE_CREATOR_USER' not found")
    }

    private fun checkIfNotPreviousVersion(): Boolean {
        return REST_API_VERSION_PATH != RestApiVersion.C5_1.versionPath && REST_API_VERSION_PATH != RestApiVersion.C5_2.versionPath
    }

    internal val vNodeCreatorClient: HttpsClient by lazy {
        lock.withLock {
            val vNodeCreatorRole = checkVNodeCreatorRoleExists()
            if (checkIfNotPreviousVersion() && vNodeCreatorRole != null && checkVNodeCreatorUserDoesNotExist()) {
                logger.info(
                    "Creating user '$VNODE_CREATOR_USER' with role '${vNodeCreatorRole["roleName"].textValue()}'"
                )
                assertWithRetry {
                    command {
                        createRbacUser(
                            true,
                            VNODE_CREATOR_USER,
                            VNODE_CREATOR_USER,
                            VNODE_CREATOR_USER,
                            null,
                            null
                        )
                    }
                    condition { it.code == 201 }
                }

                assertWithRetry {
                    command {
                        assignRoleToUser(VNODE_CREATOR_USER, vNodeCreatorRole["id"].textValue())
                    }
                    condition { it.code == 200 || it.code == 409 }
                }

                assertWithRetry {
                    command {
                        getRbacUser(VNODE_CREATOR_USER)
                    }
                    condition {
                        it.body.toJson()["roleAssociations"].firstOrNull()?.get("roleId")?.textValue()
                            .equals(vNodeCreatorRole["id"].textValue())
                    }
                }


                UnirestHttpsClient(clusterInfo.rest.uri, VNODE_CREATOR_USER, VNODE_CREATOR_USER)
            } else {
                initialClient
            }
        }
    }

    private data class VNodeCreateBody(
        val cpiFileChecksum: String,
        val x500Name: String,
        val cryptoDdlConnection: JsonNode?,
        val cryptoDmlConnection: JsonNode?,
        val uniquenessDdlConnection: JsonNode?,
        val uniquenessDmlConnection: JsonNode?,
        val vaultDdlConnection: JsonNode?,
        val vaultDmlConnection: JsonNode?
    )

    private data class VNodeChangeConnectionStringsBody(
        val cryptoDdlConnection: JsonNode?,
        val cryptoDmlConnection: JsonNode?,
        val uniquenessDdlConnection: JsonNode?,
        val uniquenessDmlConnection: JsonNode?,
        val vaultDdlConnection: JsonNode?,
        val vaultDmlConnection: JsonNode?
    )

    data class JsonExternalDBConnectionParams(
        val cryptoDdlConnection: JsonNode? = null,
        val cryptoDmlConnection: JsonNode? = null,
        val uniquenessDdlConnection: JsonNode? = null,
        val uniquenessDmlConnection: JsonNode? = null,
        val vaultDdlConnection: JsonNode? = null,
        val vaultDmlConnection: JsonNode? = null
    )

    @Suppress("LongParameterList")
    private fun uploadCpiResource(
        cmd: String,
        cpbResourceName: String?,
        groupPolicy: String,
        cpiName: String,
        cpiVersion: String,
        forceUpload: Boolean = false
    ): SimpleResponse {
        return trace("uploadCpiResource") {
            CpiLoader.get(cpbResourceName, groupPolicy, cpiName, cpiVersion).use {
                if (forceUpload) {
                    initialClient.postMultiPart(cmd, emptyMap(), mapOf("upload" to HttpsClientFileUpload(it, cpiName)))
                } else {
                    vNodeCreatorClient.postMultiPart(
                        cmd,
                        emptyMap(),
                        mapOf("upload" to HttpsClientFileUpload(it, cpiName))
                    )
                }
            }
        }
    }

    private fun uploadUnmodifiedResource(cmd: String, resourceName: String): SimpleResponse {
        val fileName = Paths.get(resourceName).fileName.toString()
        return trace("uploadUnmodifiedResource") {
            CpiLoader.getRawResource(resourceName).use {
                vNodeCreatorClient.postMultiPart(
                    cmd,
                    emptyMap(),
                    mapOf("upload" to HttpsClientFileUpload(it, fileName))
                )
            }
        }
    }

    private fun uploadCertificateResource(cmd: String, resourceName: String, alias: String): SimpleResponse =
        trace("uploadCertificateResource") {
            getInputStream(resourceName).uploadCertificateInputStream(
                cmd,
                alias,
                Paths.get(resourceName).fileName.toString(),
            )
        }


    private fun uploadCertificateFile(cmd: String, certificate: File, alias: String): SimpleResponse =
        trace("uploadCertificateFile") {
            certificate.inputStream().uploadCertificateInputStream(cmd, alias, certificate.name)
        }


    private fun InputStream.uploadCertificateInputStream(
        cmd: String, alias: String, fileName: String
    ): SimpleResponse = trace("uploadCertificateInputStream") {
        use {
            vNodeCreatorClient.putMultiPart(
                cmd,
                mapOf("alias" to alias),
                mapOf("certificate" to HttpsClientFileUpload(it, fileName))
            )
        }
    }

    private fun getInputStream(resourceName: String): InputStream =
        this::class.java.getResource(resourceName)?.openStream()
            ?: throw FileNotFoundException("No such resource: '$resourceName'")

    fun importCertificate(resourceName: String, usage: String, alias: String): SimpleResponse =
        trace("importCertificate") {
            uploadCertificateResource(
                "/api/$REST_API_VERSION_PATH/certificate/cluster/$usage",
                resourceName,
                alias,
            )
        }

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

    private fun importClusterCertificate(file: File, usage: String, alias: String): SimpleResponse =
        trace("importClusterCertificate") {
            uploadCertificateFile(
                "/api/$REST_API_VERSION_PATH/certificate/cluster/$usage",
                file,
                alias,
            )
        }

    private fun importVnodeCertificate(file: File, usage: String, alias: String, holdingIdentityId: String): SimpleResponse =
        trace("importVnodeCertificate") {
            uploadCertificateFile(
                "/api/$REST_API_VERSION_PATH/certificate/vnode/$holdingIdentityId/$usage",
                file,
                alias
            )
        }

    fun getCertificateChain(usage: String, alias: String): SimpleResponse =
        trace("getCertificateChain") {
            vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/certificate/cluster/$usage/$alias")
        }

    @Suppress("unused")
    /** Assumes the resource *is* a CPB */
    fun cpbUpload(resourceName: String): SimpleResponse =
        trace("cpbUpload") { uploadUnmodifiedResource("/api/$REST_API_VERSION_PATH/cpi/", resourceName) }

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
    ): SimpleResponse = cpiUpload(
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
    ): SimpleResponse = trace("cpiUpload") {
        uploadCpiResource("/api/$REST_API_VERSION_PATH/cpi/", cpbResourceName, groupPolicy, cpiName, cpiVersion, false)
    }

    @Suppress("unused")
    fun updateVirtualNodeState(holdingIdHash: String, newState: String): SimpleResponse =
        trace("updateVirtualNodeState") {
            vNodeCreatorClient.put("/api/$REST_API_VERSION_PATH/virtualnode/$holdingIdHash/state/$newState", "")
        }

    @Suppress("unused")
            /** Assumes the resource is a CPB and converts it to CPI by adding a group policy file */
    fun forceCpiUpload(
        cpbResourceName: String?,
        groupId: String,
        staticMemberNames: List<String>,
        cpiName: String,
        cpiVersion: String = "1.0.0.0-SNAPSHOT"
    ): SimpleResponse = trace("forceCpiUpload") {
        uploadCpiResource(
            "/api/$REST_API_VERSION_PATH/maintenance/virtualnode/forcecpiupload/",
            cpbResourceName,
            getDefaultStaticNetworkGroupPolicy(groupId, staticMemberNames),
            cpiName,
            cpiVersion,
            true
        )
    }

    @Suppress("unused")
    /** Assumes the resource is a CPB and converts it to CPI by adding a group policy file */
    fun syncVirtualNode(virtualNodeShortId: String): SimpleResponse = trace("syncVirtualNode") {
        initialClient.post(
            "/api/$REST_API_VERSION_PATH/maintenance/virtualnode/$virtualNodeShortId/vault-schema/force-resync",
            ""
        )
    }

    /** Return the status for the given request id */
    fun cpiStatus(id: String): SimpleResponse = trace("cpiStatus") {
        vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/cpi/status/$id")
    }

    /** List all CPIs in the system */
    fun cpiList() = trace("cpiList") { vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/cpi") }

    @Suppress("LongParameterList")
    private fun vNodeBody(
        cpiHash: String,
        x500Name: String,
        cryptoDdlConnection: JsonNode?,
        cryptoDmlConnection: JsonNode?,
        uniquenessDdlConnection: JsonNode?,
        uniquenessDmlConnection: JsonNode?,
        vaultDdlConnection: JsonNode?,
        vaultDmlConnection: JsonNode?
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
        cryptoDdlConnection: JsonNode?,
        cryptoDmlConnection: JsonNode?,
        uniquenessDdlConnection: JsonNode?,
        uniquenessDmlConnection: JsonNode?,
        vaultDdlConnection: JsonNode?,
        vaultDmlConnection: JsonNode?
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
        isBackchainRequiredNotary: Boolean? = null,
        notaryPlugin: String = "nonvalidating"
    ): String {

        val context = mutableMapOf(
            "corda.key.scheme" to "CORDA.ECDSA.SECP256R1",
            "corda.roles.0" to "notary",
            "corda.notary.service.name" to notaryServiceName,
            "corda.notary.service.flow.protocol.name" to "com.r3.corda.notary.plugin.$notaryPlugin",
            "corda.notary.service.flow.protocol.version.0" to "1"
        )
        if (isBackchainRequiredNotary != null) {
            context["corda.notary.service.backchain.required"] = "$isBackchainRequiredNotary"
        }

        val fullContext = (context + customMetadata).map { "\"${it.key}\" : \"${it.value}\"" }.joinToString()

        return """{ "context": { $fullContext } }""".trimMargin()
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

    private fun createRbacGroupBody(name: String, parentGroupId: String?): String {
        val body: List<String> = mutableListOf(
            """"name": "$name""""
        ).apply {
            parentGroupId?.let { add(""""parentGroupId": "$it"""") }
        }
        return body.joinToString(prefix = "{", postfix = "}")
    }

    private fun createUserPropertyBody(properties: Map<String, String>): String {
        val body = properties.map { "\"${it.key}\" : \"${it.value}\"" }
        return body.joinToString(prefix = "{", postfix = "}")
    }

    @Suppress("unused")
    fun changeUserPasswordSelf(password: String): SimpleResponse = trace("changeUserPasswordSelf") {
        initialClient.post(
            "/api/$REST_API_VERSION_PATH/user/selfpassword",
            """{"password": "$password"}"""
        )
    }

    @Suppress("unused")
    fun changeUserPasswordOther(username: String, password: String) = trace("changeUserPasswordOther") {
        initialClient.post(
            "/api/$REST_API_VERSION_PATH/user/otheruserpassword",
            """{"username": "$username", "password": "$password"}"""
        )
    }

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
    fun getCryptoSchemaSql(): SimpleResponse = trace("getCryptoSchemaSql") {
        vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/virtualnode/create/db/crypto")
    }

    @Suppress("unused")
    /** Get schema SQL to create uniqueness DB */
    fun getUniquenessSchemaSql(): SimpleResponse = trace("getUniquenessSchemaSql") {
        vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/virtualnode/create/db/uniqueness")
    }

    @Suppress("unused")
    /** Get schema SQL to create vault and CPI DB */
    fun getVaultSchemaSql(cpiChecksum: String): SimpleResponse = trace("getVaultSchemaSql") {
        vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/virtualnode/create/db/vault/$cpiChecksum")
    }

    @Suppress("unused")
    /** Get schema SQL to update vault and CPI DB */
    fun getUpdateSchemaSql(virtualNodeShortHash: String, newCpiChecksum: String): SimpleResponse =
        trace("getUpdateSchemaSql") {
            vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/virtualnode/$virtualNodeShortHash/db/vault/$newCpiChecksum")
        }

    private data class DeprecatedVNodeCreateBody(
        val cpiFileChecksum: String,
        val x500Name: String,
        val cryptoDdlConnection: String?,
        val cryptoDmlConnection: String?,
        val uniquenessDdlConnection: String?,
        val uniquenessDmlConnection: String?,
        val vaultDdlConnection: String?,
        val vaultDmlConnection: String?
    )

    @Suppress("LongParameterList")
    private fun deprecatedVNodeBody(
        cpiHash: String,
        x500Name: String,
        cryptoDdlConnection: String?,
        cryptoDmlConnection: String?,
        uniquenessDdlConnection: String?,
        uniquenessDmlConnection: String?,
        vaultDdlConnection: String?,
        vaultDmlConnection: String?
    ): String {
        val body = DeprecatedVNodeCreateBody(
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

    data class ExternalDBConnectionParams(
        val cryptoDdlConnection: String? = null,
        val cryptoDmlConnection: String? = null,
        val uniquenessDdlConnection: String? = null,
        val uniquenessDmlConnection: String? = null,
        val vaultDdlConnection: String? = null,
        val vaultDmlConnection: String? = null
    )

    /** Creates a virtual node with the deprecated method */
    @Suppress("LongParameterList", "unused")
    fun deprecatedVNodeCreate(
        cpiHash: String,
        x500Name: String,
        externalDBConnectionParams: ExternalDBConnectionParams? = null
    ): SimpleResponse = trace("deprecatedVNodeCreate") {
        initialClient.post(
            "/api/${RestApiVersion.C5_2.versionPath}/virtualnode",
            deprecatedVNodeBody(
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
    }

    /** Create a virtual node */
    @Suppress("LongParameterList")
    fun vNodeCreate(
        cpiHash: String,
        x500Name: String,
        externalDBConnectionParams: JsonExternalDBConnectionParams? = null
    ): SimpleResponse = trace("vNodeCreate") {
        vNodeCreatorClient.post(
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
    }

    @Suppress("LongParameterList", "unused")
    fun vNodeChangeConnectionStrings(
        holdingIdShortHash: String,
        externalDBConnectionParams: JsonExternalDBConnectionParams? = null
    ): SimpleResponse = trace("vNodeChangeConnectionStrings") {
        vNodeCreatorClient.put(
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
    }


    @Suppress("unused")
    /** Trigger upgrade of a virtual node's CPI to the given  */
    fun vNodeUpgrade(virtualNodeShortHash: String, targetCpiFileChecksum: String): SimpleResponse =
        trace("vNodeUpgrade") {
            vNodeCreatorClient.put(
                "/api/$REST_API_VERSION_PATH/virtualnode/$virtualNodeShortHash/cpi/$targetCpiFileChecksum",
                ""
            )
        }

    @Suppress("unused")
    fun getVNodeOperationStatus(requestId: String): SimpleResponse = trace("getVNodeOperationStatus") {
        vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/virtualnode/status/$requestId")
    }

    /** List all virtual nodes */
    fun vNodeList(): SimpleResponse =
        trace("vNodeList") { vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/virtualnode") }

    /** Gets virtual node info for a specified holding ID */
    fun getVNode(holdingIdentityShortHash: String): SimpleResponse = trace("getVNode") {
        vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/virtualnode/$holdingIdentityShortHash")
    }

    fun getVNodeStatus(requestId: String): SimpleResponse =
        trace("getVNodeStatus") { vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/virtualnode/status/$requestId") }

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
        isBackchainRequiredNotary: Boolean? = null,
        notaryPlugin: String = "nonvalidating"
    ): SimpleResponse = trace("registerStaticMember") {
        register(
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
    }

    fun register(holdingIdShortHash: String, registrationContext: String): SimpleResponse = trace("register") {
        vNodeCreatorClient.post(
            "/api/$REST_API_VERSION_PATH/membership/$holdingIdShortHash",
            registrationContext
        )
    }

    fun getRegistrationStatus(holdingIdShortHash: String): SimpleResponse = trace("getRegistrationStatus") {
        initialClient.get("/api/$REST_API_VERSION_PATH/membership/$holdingIdShortHash")
    }

    fun getRegistrationStatus(holdingIdShortHash: String, registrationId: String): SimpleResponse =
        trace("getRegistrationStatus") {
            vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/membership/$holdingIdShortHash/$registrationId")
        }

    fun addSoftHsmToVNode(holdingIdentityShortHash: String, category: String): SimpleResponse =
        trace("addSoftHsmToVNode") {
            vNodeCreatorClient.post(
                "/api/$REST_API_VERSION_PATH/hsm/soft/$holdingIdentityShortHash/$category",
                body = ""
            )
        }

    fun createKey(holdingIdentityShortHash: String, alias: String, category: String, scheme: String): SimpleResponse =
        trace("createKey") {
            vNodeCreatorClient.post(
                "/api/$REST_API_VERSION_PATH/key/$holdingIdentityShortHash/alias/$alias/category/$category/scheme/$scheme",
                body = ""
            )
        }

    @Suppress("unused")
    fun getKey(tenantId: String, keyId: String): SimpleResponse = trace("getKey") {
        vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/key/$tenantId/$keyId")
    }

    fun getKey(
        tenantId: String,
        category: String? = null,
        alias: String? = null,
        ids: List<String>? = null
    ): SimpleResponse  = trace("getKey") {
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
        vNodeCreatorClient.get("/api/$REST_API_VERSION_PATH/key/$tenantId$queryStr")
    }

    /** Get status of a flow */
    fun flowStatus(holdingIdentityShortHash: String, clientRequestId: String): SimpleResponse = trace("flowStatus") {
        initialClient.get("/api/$REST_API_VERSION_PATH/flow/$holdingIdentityShortHash/$clientRequestId")
    }

    /** Get status of multiple flows */
    fun multipleFlowStatus(holdingIdentityShortHash: String, status: String? = null): SimpleResponse =
        trace("multipleFlowStatus") {
            initialClient.get("/api/$REST_API_VERSION_PATH/flow/$holdingIdentityShortHash/?status=$status")
        }

    /** Get result of a flow execution */
    fun flowResult(holdingIdentityShortHash: String, clientRequestId: String): SimpleResponse = trace("flowResult") {
        initialClient.get("/api/$REST_API_VERSION_PATH/flow/$holdingIdentityShortHash/$clientRequestId/result")
    }

    /** Get status of multiple flows */
    fun runnableFlowClasses(holdingIdentityShortHash: String): SimpleResponse = trace("runnableFlowClasses") {
        initialClient.get("/api/$REST_API_VERSION_PATH/flowclass/$holdingIdentityShortHash")
    }

    @Suppress("unused")
    /** Create a new RBAC role */
    fun createRbacRole(roleName: String, groupVisibility: String? = null): SimpleResponse = trace("createRbacRole") {
        initialClient.post("/api/$REST_API_VERSION_PATH/role", createRbacRoleBody(roleName, groupVisibility))
    }

    /** Get all RBAC roles */
    fun getRbacRoles(): SimpleResponse = trace("getRbacRoles") { initialClient.get("/api/$REST_API_VERSION_PATH/role") }

    @Suppress("unused")
    /** Get a role for a specified ID */
    fun getRole(roleId: String) = trace("getRole") { initialClient.get("/api/$REST_API_VERSION_PATH/role/$roleId") }

    /** Create new RBAC user */
    @Suppress("LongParameterList", "unused")
    fun createRbacUser(
        enabled: Boolean,
        fullName: String,
        password: String,
        loginName: String,
        parentGroup: String? = null,
        passwordExpiry: Instant? = null
    ): SimpleResponse = trace("createRbacUser") {
        initialClient.post(
            "/api/$REST_API_VERSION_PATH/user",
            createRbacUserBody(enabled, fullName, password, loginName, parentGroup, passwordExpiry)
        )
    }

    @Suppress("unused")
    /** Get a RBAC user for a specific login name */
    fun getRbacUser(loginName: String): SimpleResponse = trace("getRbacUser") {
        initialClient.get("/api/$REST_API_VERSION_PATH/user/$loginName")
    }

    @Suppress("unused")
    /** Delete a RBAC user */
    fun deleteRbacUser(loginName: String): SimpleResponse = trace("deleteRbacUser") {
        initialClient.delete("/api/$REST_API_VERSION_PATH/user/$loginName")
    }

    @Suppress("unused")
    /** Change the parent group of a specified user */
    fun changeUserParentGroup(loginName: String, newParentGroupId: String?): SimpleResponse = trace("changeUserParentGroup") {
        initialClient.put("/api/$REST_API_VERSION_PATH/user/$loginName/parent/changeparentid/$newParentGroupId", "")
    }

    @Suppress("unused")
    /** Assign a specified role to a specified user */
    fun assignRoleToUser(loginName: String, roleId: String): SimpleResponse = trace("assignRoleToUser") {
        initialClient.put("/api/$REST_API_VERSION_PATH/user/$loginName/role/$roleId", "")
    }

    @Suppress("unused")
    /** Remove the specified role from a specified user */
    fun removeRoleFromUser(loginName: String, roleId: String): SimpleResponse = trace("removeRoleFromUser") {
        initialClient.delete("/api/$REST_API_VERSION_PATH/user/$loginName/role/$roleId")
    }

    @Suppress("unused")
    /** Get a summary of the user's permissions */
    fun getPermissionSummary(loginName: String): SimpleResponse = trace("getPermissionSummary") {
        initialClient.get("/api/$REST_API_VERSION_PATH/user/$loginName/permissionsummary")
    }

    @Suppress("unused")
    fun addPropertyToUser(loginName: String, property: Map<String, String>): SimpleResponse =
        trace("addPropertyToUser") {
            initialClient.post(
                "/api/$REST_API_VERSION_PATH/user/$loginName/property",
                createUserPropertyBody(property)
            )
        }

    @Suppress("unused")
    fun removePropertyFromUser(loginName: String, propertyKey: String): SimpleResponse =
        trace("removePropertyFromUser") {
            initialClient.delete("/api/$REST_API_VERSION_PATH/user/$loginName/property/$propertyKey")
        }

    @Suppress("unused")
    fun getUserProperties(loginName: String): SimpleResponse = trace("getUserProperties") {
        initialClient.get("/api/$REST_API_VERSION_PATH/user/$loginName/property")
    }

    @Suppress("unused")
    fun getUsersByPropertyKey(propertyKey: String, propertyValue: String): SimpleResponse =
        trace("getUsersByPropertyKey") {
            initialClient.get("/api/$REST_API_VERSION_PATH/user/findbyproperty/$propertyKey/$propertyValue")
        }

    @Suppress("unused")
    /** Create a new permission */
    fun createPermission(
        permissionString: String,
        permissionType: String,
        groupVisibility: String? = null,
        virtualNode: String? = null
    ): SimpleResponse = trace("createPermission") {
        initialClient.post(
            "/api/$REST_API_VERSION_PATH/permission",
            createPermissionBody(permissionString, permissionType, groupVisibility, virtualNode)
        )
    }

    @Suppress("unused")
    /** Create a set of permissions and optionally assigns them to existing roles */
    fun createBulkPermissions(
        permissionsToCreate: Set<Pair<String, String>>,
        roleIds: Set<String>
    ): SimpleResponse = trace("createBulkPermissions") {
        initialClient.post(
            "/api/$REST_API_VERSION_PATH/permission/bulk",
            createBulkPermissionsBody(permissionsToCreate, roleIds)
        )
    }

    @Suppress("unused")
    /** Get the permissions which satisfy the query */
    fun getPermissionByQuery(
        limit: Int,
        permissionType: String,
        permissionStringPrefix: String? = null
    ): SimpleResponse = trace("getPermissionByQuery") {
        val queries: List<String> = mutableListOf("limit=$limit", "permissiontype=$permissionType").apply {
            permissionStringPrefix?.let { add("permissionstringprefix=$it") }
        }
        val queryStr = queries.joinToString(prefix = "?", separator = "&")
        initialClient.get("/api/$REST_API_VERSION_PATH/permission$queryStr")
    }

    @Suppress("unused")
    /** Get the permission associated with a specific ID */
    fun getPermissionById(permissionId: String): SimpleResponse = trace("getPermissionById") {
        initialClient.get("/api/$REST_API_VERSION_PATH/permission/$permissionId")
    }

    @Suppress("unused")
    /** Add the specified permission to the specified role */
    fun assignPermissionToRole(roleId: String, permissionId: String): SimpleResponse = trace("assignPermissionToRole") {
        initialClient.put("/api/$REST_API_VERSION_PATH/role/$roleId/permission/$permissionId", "")
    }

    @Suppress("unused")
    /** Remove the specified permission from the specified role */
    fun removePermissionFromRole(roleId: String, permissionId: String): SimpleResponse = trace("removePermissionFromRole") {
        initialClient.delete("/api/$REST_API_VERSION_PATH/role/$roleId/permission/$permissionId")
    }

    @Suppress("unused")
    // This method is used to create a new RBAC group
    fun createRbacGroup(name: String, parentGroupId: String?): SimpleResponse  = trace("createRbacGroup") {
        initialClient.post("/api/$REST_API_VERSION_PATH/group", createRbacGroupBody(name, parentGroupId))
    }

    @Suppress("unused")
    // This method is used to retrieve an existing RBAC group
    fun getRbacGroup(groupId: String): SimpleResponse = trace("getRbacGroup") {
        initialClient.get("/api/$REST_API_VERSION_PATH/group/$groupId")
    }

    @Suppress("unused")
    // This method is used to delete an existing RBAC group
    fun deleteRbacGroup(groupId: String): SimpleResponse = trace("deleteRbacGroup") {
        initialClient.delete("/api/$REST_API_VERSION_PATH/group/$groupId")
    }

    @Suppress("unused")
    // This method is used to change the parent group of an existing RBAC group
    fun changeParentGroup(groupId: String, newParentGroupId: String?): SimpleResponse = trace("changeParentGroup") {
        initialClient.put("/api/$REST_API_VERSION_PATH/group/$groupId/parent/changeparentid/$newParentGroupId", "")
    }

    @Suppress("unused")
    // This method is used to add a role to an existing RBAC group
    fun addRoleToGroup(groupId: String, roleId: String): SimpleResponse = trace("addRoleToGroup") {
        initialClient.put("/api/$REST_API_VERSION_PATH/group/$groupId/role/$roleId", "")
    }

    @Suppress("unused")
    // This method is used to remove a role from an existing RBAC group
    fun removeRoleFromGroup(groupId: String, roleId: String): SimpleResponse = trace("removeRoleFromGroup") {
        initialClient.delete("/api/$REST_API_VERSION_PATH/group/$groupId/role/$roleId")
    }

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
            logger.info(
                "Sending flowStart, vNode: '$holdingIdentityShortHash', " +
                        "clientRequestId: '$clientRequestId', traceId: '$traceIdString'"
            )
            initialClient.post(
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
    fun getConfig(section: String): SimpleResponse =
        trace("getConfig") { initialClient.get("/api/$REST_API_VERSION_PATH/config/$section") }

    /** Update the cluster configuration for the specified section and versions with unescaped Json */
    fun putConfig(
        config: String,
        section: String,
        configVersion: String,
        schemaMajorVersion: String,
        schemaMinorVersion: String
    ): SimpleResponse = trace("putConfig") {
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

        initialClient.put("/api/$REST_API_VERSION_PATH/config", payload)
    }

    fun configureNetworkParticipant(
        holdingIdentityShortHash: String,
        sessionKeyId: String,
        sessionCertAlias: String?,
        tlsCertAlias: String,
    ): SimpleResponse = trace("configureNetworkParticipant") {
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
                    "p2pTlsCertificateChainAlias": "$tlsCertAlias",
                    "useClusterLevelTlsCertificateAndKey": true,
                    $sessionKeysSection
                }
            """.trimIndent()
        vNodeCreatorClient.put(
            "/api/$REST_API_VERSION_PATH/network/setup/$holdingIdentityShortHash",
            body = body
        )
    }

    fun doRotateCryptoWrappingKeys(tenantId: String) = trace("doRotateCryptoWrappingKeys") {
        initialClient.post("/api/$REST_API_VERSION_PATH/wrappingkey/rotation/${tenantId}", "")
    }

    fun getCryptoWrappingKeysRotationStatus(tenantId: String) = trace("getCryptoWrappingKeysRotationStatus") {
        initialClient.get("/api/$REST_API_VERSION_PATH/wrappingkey/rotation/${tenantId}")
    }
}

fun <T> cluster(
    initialize: ClusterBuilder.() -> T,
): T = DEFAULT_CLUSTER.cluster(initialize)

fun <T> ClusterInfo.cluster(
    initialize: ClusterBuilder.() -> T
): T = ClusterBuilder(this, restApiVersion.versionPath).let(initialize)
