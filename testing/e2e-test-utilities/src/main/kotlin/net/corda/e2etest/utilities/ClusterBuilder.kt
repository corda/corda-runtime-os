package net.corda.e2etest.utilities

import java.io.File
import java.io.FileNotFoundException
import java.net.URI
import java.nio.file.Paths

/**
 *  All functions return a [SimpleResponse] if not explicitly declared.
 *
 *  The caller needs to marshall the response body to json, and then query
 *  the json for the expected results.
 */
@Suppress("TooManyFunctions")
class ClusterBuilder {
    private var client: HttpsClient? = null

    fun endpoint(uri: URI, username: String, password: String) {
        client = UnirestHttpsClient(uri, username, password)
    }

    fun configure(config: ClusterConfig) {
        endpoint(config.restUri, config.restUser, config.restPassword)
    }

    /** POST, but most useful for running flows */
    fun post(cmd: String, body: String) = client!!.post(cmd, body)

    fun put(cmd: String, body: String) = client!!.put(cmd, body)

    fun get(cmd: String) = client!!.get(cmd)

    fun delete(cmd: String) = client!!.delete(cmd)

    private fun uploadCpiResource(
        cmd: String,
        resourceName: String,
        groupPolicy: String,
        cpiName: String,
        cpiVersion: String
    ): SimpleResponse {
        val fileName = Paths.get(resourceName).fileName.toString()
        return CpiLoader.get(resourceName, groupPolicy, cpiName, cpiVersion).use {
            client!!.postMultiPart(cmd, emptyMap(), mapOf("upload" to HttpsClientFileUpload(it, fileName)))
        }
    }

    private fun uploadUnmodifiedResource(cmd: String, resourceName: String): SimpleResponse {
        val fileName = Paths.get(resourceName).fileName.toString()
        return CpiLoader.getRawResource(resourceName).use {
            client!!.postMultiPart(cmd, emptyMap(), mapOf("upload" to HttpsClientFileUpload(it, fileName)))
        }
    }

    private fun uploadCertificateResource(cmd: String, resourceName: String, alias: String): SimpleResponse {
        val fileName = Paths.get(resourceName).fileName.toString()
        return getInputStream(resourceName).use {
            client!!.putMultiPart(
                cmd,
                mapOf("alias" to alias),
                mapOf("certificate" to HttpsClientFileUpload(it, fileName))
            )
        }
    }

    private fun uploadCertificateResource(cmd: String, certificate: File, alias: String): SimpleResponse {
        return certificate.inputStream().use {
            client!!.putMultiPart(
                cmd,
                mapOf("alias" to alias),
                mapOf("certificate" to HttpsClientFileUpload(it, certificate.name))
            )
        }
    }

    private fun getInputStream(resourceName: String) =
        this::class.java.getResource(resourceName)?.openStream()
            ?: throw FileNotFoundException("No such resource: '$resourceName'")

    fun importCertificate(resourceName: String, usage: String, alias: String) =
        uploadCertificateResource("/api/v1/certificates/cluster/$usage", resourceName, alias)

    fun importCertificate(file: File, usage: String, alias: String) =
        uploadCertificateResource("/api/v1/certificates/cluster/$usage", file, alias)

    /** Assumes the resource *is* a CPB */
    fun cpbUpload(resourceName: String) = uploadUnmodifiedResource("/api/v1/cpi/", resourceName)

    /** Assumes the resource is a CPB and converts it to CPI by adding a group policy file */
    fun cpiUpload(
        resourceName: String,
        groupId: String,
        staticMemberNames: List<String>,
        cpiName: String,
        cpiVersion: String = "1.0.0.0-SNAPSHOT"
    ) = cpiUpload(
        resourceName,
        getDefaultStaticNetworkGroupPolicy(groupId, staticMemberNames),
        cpiName,
        cpiVersion
    )

    fun cpiUpload(
        resourceName: String,
        groupPolicy: String,
        cpiName: String,
        cpiVersion: String = "1.0.0.0-SNAPSHOT"
    ) = uploadCpiResource("/api/v1/cpi/", resourceName, groupPolicy, cpiName, cpiVersion)

    fun updateVirtualNodeState(holdingIdHash: String, newState: String) =
        put("/api/v1/virtualnode/$holdingIdHash/state/$newState", "")

    /** Assumes the resource is a CPB and converts it to CPI by adding a group policy file */
    fun forceCpiUpload(
        resourceName: String,
        groupId: String,
        staticMemberNames: List<String>,
        cpiName: String,
        cpiVersion: String = "1.0.0.0-SNAPSHOT"
    ) =
        uploadCpiResource(
            "/api/v1/maintenance/virtualnode/forcecpiupload/",
            resourceName,
            getDefaultStaticNetworkGroupPolicy(groupId, staticMemberNames),
            cpiName,
            cpiVersion
        )

    /** Assumes the resource is a CPB and converts it to CPI by adding a group policy file */
    fun syncVirtualNode(virtualNodeShortId: String) =
        post("/api/v1/maintenance/virtualnode/$virtualNodeShortId/vault-schema/force-resync", "")

    /** Return the status for the given request id */
    fun cpiStatus(id: String) = client!!.get("/api/v1/cpi/status/$id")

    /** List all CPIs in the system */
    fun cpiList() = client!!.get("/api/v1/cpi")

    private fun vNodeBody(cpiHash: String, x500Name: String) =
        """{ "cpiFileChecksum" : "$cpiHash", "x500Name" : "$x500Name"}"""

    private fun registerMemberBody() =
        """{ "action": "requestJoin", "context": { "corda.key.scheme" : "CORDA.ECDSA.SECP256R1" } }""".trimMargin()

    // TODO CORE-7248 Review once plugin loading logic is added
    private fun registerNotaryBody() =
        """{ 
            |  "action": "requestJoin",
            |  "context": { 
            |    "corda.key.scheme" : "CORDA.ECDSA.SECP256R1", 
            |    "corda.roles.0" : "notary",
            |    "corda.notary.service.name" : "O=MyNotaryService, L=London, C=GB",
            |    "corda.notary.service.plugin" : "net.corda.notary.NonValidatingNotary"
            |   } 
            | }""".trimMargin()

    /** Create a virtual node */
    fun vNodeCreate(cpiHash: String, x500Name: String) =
        post("/api/v1/virtualnode", vNodeBody(cpiHash, x500Name))

    /** Trigger upgrade of a virtual node's CPI to the given  */
    fun vNodeUpgrade(virtualNodeShortHash: String, targetCpiFileChecksum: String) =
        put("/api/v1/virtualnode/$virtualNodeShortHash/cpi/$targetCpiFileChecksum", "")

    /** List all virtual nodes */
    fun vNodeList() = client!!.get("/api/v1/virtualnode")

    /** List all virtual nodes */
    fun getVNode(holdingIdentityShortHash: String) = client!!.get("/api/v1/virtualnode/$holdingIdentityShortHash")

    /**
     * Register a member to the network
     */
    fun registerStaticMember(holdingIdShortHash: String, isNotary: Boolean = false) =
        register(
            holdingIdShortHash,
            if (isNotary) registerNotaryBody() else registerMemberBody()
        )

    fun register(holdingIdShortHash: String, registrationContext: String) =
        post(
            "/api/v1/membership/$holdingIdShortHash",
            registrationContext
        )

    fun getRegistrationStatus(holdingIdShortHash: String) =
        get("/api/v1/membership/$holdingIdShortHash")

    fun getRegistrationStatus(holdingIdShortHash: String, registrationId: String) =
        get("/api/v1/membership/$holdingIdShortHash/$registrationId")

    fun addSoftHsmToVNode(holdingIdentityShortHash: String, category: String) =
        post("/api/v1/hsm/soft/$holdingIdentityShortHash/$category", body = "")

    fun createKey(holdingIdentityShortHash: String, alias: String, category: String, scheme: String) =
        post("/api/v1/keys/$holdingIdentityShortHash/alias/$alias/category/$category/scheme/$scheme", body = "")

    fun getKey(tenantId: String, keyId: String) =
        get("/api/v1/keys/$tenantId/$keyId")

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
        return get("/api/v1/keys/$tenantId$queryStr")
    }

    /** Get status of a flow */
    fun flowStatus(holdingIdentityShortHash: String, clientRequestId: String) =
        get("/api/v1/flow/$holdingIdentityShortHash/$clientRequestId")

    /** Get status of multiple flows */
    fun multipleFlowStatus(holdingIdentityShortHash: String) =
        get("/api/v1/flow/$holdingIdentityShortHash")

    /** Get status of multiple flows */
    fun runnableFlowClasses(holdingIdentityShortHash: String) =
        get("/api/v1/flowclass/$holdingIdentityShortHash")

    /** Get all RBAC roles */
    fun getRbacRoles() = get("/api/v1/role")

    /** Start a flow */
    fun flowStart(
        holdingIdentityShortHash: String,
        clientRequestId: String,
        flowClassName: String,
        requestData: String
    ): SimpleResponse {
        return post(
            "/api/v1/flow/$holdingIdentityShortHash",
            flowStartBody(clientRequestId, flowClassName, requestData)
        )
    }

    private fun flowStartBody(clientRequestId: String, flowClassName: String, requestData: String) =
        """{ "clientRequestId" : "$clientRequestId", "flowClassName" : "$flowClassName", "requestBody" : 
            |"$requestData" }
        """.trimMargin()

    /** Get cluster configuration for the specified section */
    fun getConfig(section: String) = get("/api/v1/config/$section")

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

        return put("/api/v1/config", payload)
    }

    fun configureNetworkParticipant(
        holdingIdentityShortHash: String,
        sessionKeyId: String
    ) =
        put(
            "/api/v1/network/setup/$holdingIdentityShortHash",
            body = """
                {
                    "p2pTlsCertificateChainAlias": "p2p-tls-cert",
                    "useClusterLevelTlsCertificateAndKey": true,
                    "sessionKeyId": "$sessionKeyId"
                }
            """.trimIndent()
        )
}

fun <T> cluster(initialize: ClusterBuilder.() -> T): T = ClusterBuilder().let(initialize)

fun <T> cluster(
    clusterConfig: ClusterConfig,
    initialize: ClusterBuilder.() -> T
): T = ClusterBuilder().apply { configure(clusterConfig) }.let(initialize)