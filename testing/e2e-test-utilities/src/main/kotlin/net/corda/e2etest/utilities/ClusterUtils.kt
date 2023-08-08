package net.corda.e2etest.utilities

import com.fasterxml.jackson.module.kotlin.contains
import java.time.Duration
import net.corda.rest.ResponseCode
import net.corda.test.util.eventually
import net.corda.utilities.seconds
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat

/**
 * Transform a Corda Package Bundle (CPB) into a Corda Package Installer (CPI) by adding a group policy file and upload
 * the resulting CPI to the system if it doesn't already exist.
 */
fun ClusterInfo.conditionallyUploadCordaPackage(
    cpiName: String,
    cpbResourceName: String?,
    groupPolicy: String
) = conditionallyUploadCordaPackage(cpiName) {
    cpiUpload(cpbResourceName, groupPolicy, cpiName)
}

fun ClusterInfo.conditionallyUploadCpiSigningCertificate() = cluster {
    val hasCertificateChain = assertWithRetryIgnoringExceptions {
        interval(1.seconds)
        command { getCertificateChain(CODE_SIGNER_CERT_USAGE, CODE_SIGNER_CERT_ALIAS) }
        condition {
            it.code == ResponseCode.RESOURCE_NOT_FOUND.statusCode ||
                    it.code == ResponseCode.OK.statusCode
        }
    }.let {
        it.code != ResponseCode.RESOURCE_NOT_FOUND.statusCode
    }
    if (!hasCertificateChain) {
        assertWithRetryIgnoringExceptions {
            // Certificate upload can be slow in the combined worker, especially after it has just started up.
            timeout(30.seconds)
            interval(2.seconds)
            command { importCertificate(CODE_SIGNER_CERT, CODE_SIGNER_CERT_USAGE, CODE_SIGNER_CERT_ALIAS) }
            condition { it.code == ResponseCode.NO_CONTENT.statusCode }
        }
    }
}

/**
 * Transform a Corda Package Bundle (CPB) into a Corda Package Installer (CPI) by adding the default static group policy
 * file used by smoke tests and upload the resulting CPI to the system if it doesn't already exist.
 */
fun conditionallyUploadCordaPackage(
    cpiName: String,
    cpbResourceName: String,
    groupId: String,
    staticMemberNames: List<String>,
    customGroupParameters: Map<String, Any> = emptyMap(),
) = DEFAULT_CLUSTER.conditionallyUploadCordaPackage(cpiName, cpbResourceName, groupId, staticMemberNames, customGroupParameters)

fun ClusterInfo.conditionallyUploadCordaPackage(
    cpiName: String,
    cpbResourceName: String,
    groupId: String,
    staticMemberNames: List<String>,
    customGroupParameters: Map<String, Any> = emptyMap(),
) = conditionallyUploadCordaPackage(cpiName) {
    cpiUpload(cpbResourceName, groupId, staticMemberNames, cpiName, customGroupParameters = customGroupParameters)
}

fun ClusterInfo.conditionallyUploadCordaPackage(
    name: String,
    cpiUpload: ClusterBuilder.() -> SimpleResponse
) = cluster {
    if (getExistingCpi(name) == null) {
        val responseStatusId = cpiUpload().run {
            assertThat(code).isEqualTo(ResponseCode.OK.statusCode)
            assertThat(toJson()["id"].textValue()).isNotEmpty
            toJson()["id"].textValue()
        }

        assertWithRetryIgnoringExceptions {
            timeout(Duration.ofSeconds(100))
            interval(Duration.ofSeconds(2))
            command { cpiStatus(responseStatusId) }
            condition {
                it.code == ResponseCode.OK.statusCode
                        && it.toJson()["status"].textValue() == ResponseCode.OK.toString()
            }
        }
    }
}

fun getOrCreateVirtualNodeFor(
    x500: String,
    cpiName: String
) = DEFAULT_CLUSTER.getOrCreateVirtualNodeFor(x500, cpiName)

fun ClusterInfo.getOrCreateVirtualNodeFor(
    x500: String,
    cpiName: String
): String = cluster {
    // be a bit patient for the CPI info to get there in case it has just been uploaded
    val json = eventually(
        duration = Duration.ofSeconds(30)
    ) {
        getExistingCpi(cpiName).apply {
            assertThat(this).isNotNull
        }!!
    }
    val hash = truncateLongHash(json["cpiFileChecksum"].textValue())

    val vNodesJson = assertWithRetryIgnoringExceptions {
        command { vNodeList() }
        condition { it.code == 200 }
        failMessage("Failed to retrieve virtual nodes")
    }.toJson()

    val normalizedX500 = MemberX500Name.parse(x500).toString()

    if (vNodesJson.findValuesAsText("x500Name").contains(normalizedX500)) {
        vNodeList().toJson()["virtualNodes"].toList().first {
            it["holdingIdentity"]["x500Name"].textValue() == normalizedX500
        }["holdingIdentity"]["shortHash"].textValue()
    } else {
        val createVNodeRequest = assertWithRetry {
            command { vNodeCreate(hash, x500) }
            condition { it.code == 202 }
            failMessage("Failed to create the virtual node for '$x500'")
        }.toJson()

        val requestId = createVNodeRequest["requestId"].textValue()
        awaitVirtualNodeOperationStatusCheck(requestId)
    }
}

fun ClusterInfo.getExistingCpi(
    cpiName: String
) = cluster {
    assertWithRetryIgnoringExceptions {
        command { cpiList() }
        condition { it.code == ResponseCode.OK.statusCode }
        failMessage("Failed to list CPIs")
    }.toJson().apply {
            assertThat(contains("cpis")).isTrue
        }["cpis"]
        .toList()
        .firstOrNull {
            it["id"]["cpiName"].textValue() == cpiName
        }
}

fun ClusterInfo.addSoftHsmFor(
    holdingId: String,
    category: String
) = cluster {
    assertWithRetry {
        timeout(Duration.ofSeconds(5))
        command { addSoftHsmToVNode(holdingId, category) }
        condition { it.code == 200 }
        failMessage("Failed to add SoftHSM for holding id '$holdingId'")
    }
}

fun ClusterInfo.createKeyFor(
    tenantId: String,
    alias: String,
    category: String,
    scheme: String
): String = cluster {
    val keyId = assertWithRetry {
        interval(1.seconds)
        command { createKey(tenantId, alias, category, scheme) }
        condition {
            // Allow 409 also in case an error occurred when creating but creation was successful.
            it.code == 200 || it.code == 409
        }
        failMessage("Failed to create key for holding id '$tenantId'")
    }.toJson()
    assertWithRetryIgnoringExceptions {
        interval(1.seconds)
        command { getKey(tenantId, keyId["id"].textValue()) }
        condition { it.code == 200 }
        failMessage("Failed to get key for holding id '$tenantId' and key id '$keyId'")
    }
    keyId["id"].textValue()
}

fun ClusterInfo.keyExists(
    tenantId: String,
    alias: String? = null,
    category: String? = null,
    ids: List<String>? = null
): Boolean = cluster {
    val result = assertWithRetryIgnoringExceptions {
        command { getKey(tenantId, category, alias, ids) }
        condition { it.code == ResponseCode.OK.statusCode }
        failMessage("Failed to get keys for tenant id '$tenantId', category '$category', alias '$alias' and IDs: $ids")
    }

    result.code == ResponseCode.OK.statusCode && result.toJson().fieldNames().hasNext()
}
