package net.corda.applications.workers.smoketest.virtualnode.helpers

import java.net.URI
import java.nio.file.Paths

/**
 *  All functions return a [SimpleResponse] if not explicitly declared.
 *
 *  The caller needs to marshall the response body to json, and then query
 *  the json for the expected results.
 */
class ClusterBuilder {
    private var client: HttpsClient? = null

    fun endpoint(uri: URI, username: String, password: String) {
        client = UnirestHttpsClient(uri, username, password)
    }

    /** POST, but most useful for running flows */
    fun post(cmd: String, body: String) = client!!.post(cmd, body)

    fun put(cmd: String, body: String) = client!!.put(cmd, body)

    fun get(cmd: String) = client!!.get(cmd)

    private fun uploadCpiResource(cmd: String, resourceName: String, groupId: String): SimpleResponse {
        val fileName = Paths.get(resourceName).fileName.toString()
        return CpiLoader.get(resourceName, groupId).use {
            client!!.postMultiPart(cmd, emptyMap(), mapOf("upload" to HttpsClientFileUpload(it, fileName)))
        }
    }

    private fun uploadUnmodifiedResource(cmd: String, resourceName: String): SimpleResponse {
        val fileName = Paths.get(resourceName).fileName.toString()
        return CpiLoader.getRawResource(resourceName).use {
            client!!.postMultiPart(cmd, emptyMap(), mapOf("upload" to HttpsClientFileUpload(it, fileName)))
        }
    }

    /** Assumes the resource *is* a CPB */
    fun cpbUpload(resourceName: String) = uploadUnmodifiedResource("/api/v1/cpi/", resourceName)

    /** Assumes the resource is a CPB and converts it to CPI by adding a group policy file */
    fun cpiUpload(resourceName: String, groupId: String) = uploadCpiResource("/api/v1/cpi/", resourceName, groupId)

    fun updateVirtualNodeState(holdingIdHash: String, newState: String) = put(
        "/api/v1/maintenance/virtualnode/$holdingIdHash/state/$newState",
       ""
    )

    /** Assumes the resource is a CPB and converts it to CPI by adding a group policy file */
    fun forceCpiUpload(resourceName: String, groupId: String) =
        uploadCpiResource("/api/v1/maintenance/virtualnode/forcecpiupload/", resourceName, groupId)

    /** Return the status for the given request id */
    fun cpiStatus(id: String) = client!!.get("/api/v1/cpi/status/$id")

    /** List all CPIs in the system */
    fun cpiList() = client!!.get("/api/v1/cpi")

    private fun vNodeBody(cpiHash: String, x500Name: String) =
        """{ "cpiFileChecksum" : "$cpiHash", "x500Name" : "$x500Name"}"""

    private fun registerMemberBody() =
        """{ "action": "requestJoin", "context": { "corda.key.scheme" : "CORDA.ECDSA.SECP256R1" } }""".trimMargin()

    /** Create a virtual node */
    fun vNodeCreate(cpiHash: String, x500Name: String) =
        client!!.post("/api/v1/virtualnode", vNodeBody(cpiHash, x500Name))

    /** List all virtual nodes */
    fun vNodeList() = client!!.get("/api/v1/virtualnode")

    /**
     * Register a member to the network
     */
    fun registerMember(holdingId: String) =
        client!!.post("/api/v1/membership/$holdingId", registerMemberBody())

    fun addSoftHsmToVNode(holdingIdentityShortHash: String, category: String) =
        client!!.post("/api/v1/hsm/soft/$holdingIdentityShortHash/$category", body = "")

    fun createKey(holdingIdentityShortHash: String, alias: String, category: String, scheme: String) =
        client!!.post(
            "/api/v1/keys/$holdingIdentityShortHash",
            body = """{
                    "alias": "$alias",
                    "hsmCategory": "$category",
                    "scheme": "$scheme"
                }""".trimIndent()
        )

    fun getKey(holdingIdentityShortHash: String, keyId: String) =
        client!!.get("/api/v1/keys/$holdingIdentityShortHash/$keyId")

    /** Get status of a flow */
    fun flowStatus(holdingIdentityShortHash: String, clientRequestId: String) =
        client!!.get("/api/v1/flow/$holdingIdentityShortHash/$clientRequestId")

    /** Get status of multiple flows */
    fun multipleFlowStatus(holdingIdentityShortHash: String) =
        client!!.get("/api/v1/flow/$holdingIdentityShortHash")

    /** Get status of multiple flows */
    fun runnableFlowClasses(holdingIdentityShortHash: String) =
        client!!.get("/api/v1/flowclass/$holdingIdentityShortHash")

    /** Start a flow */
    fun flowStart(
        holdingIdentityShortHash: String,
        clientRequestId: String,
        flowClassName: String,
        requestData: String
    ): SimpleResponse {
        return client!!.post("/api/v1/flow/$holdingIdentityShortHash", flowStartBody(clientRequestId, flowClassName, requestData))
    }

    private fun flowStartBody(clientRequestId: String, flowClassName: String, requestData: String) =
        """{ "clientRequestId" : "$clientRequestId", "flowClassName" : "$flowClassName", "requestData" : 
            |"$requestData" }""".trimMargin()

}

fun <T> cluster(initialize: ClusterBuilder.() -> T):T = ClusterBuilder().let(initialize)
