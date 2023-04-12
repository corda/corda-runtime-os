package net.corda.e2etest.utilities

import com.fasterxml.jackson.module.kotlin.contains
import net.corda.rest.ResponseCode
import net.corda.test.util.eventually
import net.corda.utilities.seconds
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import java.time.Duration

/**
 * Transform a Corda Package Bundle (CPB) into a Corda Package Installer (CPI) by adding a group policy file and upload
 * the resulting CPI to the system if it doesn't already exist.
 */
fun ClusterInfo.conditionallyUploadCordaPackage(
    name: String,
    cpb: String,
    groupPolicy: String
) = conditionallyUploadCordaPackage(name) {
    cpiUpload(cpb, groupPolicy, name)
}

fun ClusterInfo.conditionallyUploadCpiSigningCertificate() = cluster {
    if (!hasCertificateChain(CODE_SIGNER_CERT_USAGE, CODE_SIGNER_CERT_ALIAS)) {
        assertWithRetry {
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
    name: String,
    cpb: String,
    groupId: String,
    staticMemberNames: List<String>
) = DEFAULT_CLUSTER.conditionallyUploadCordaPackage(name, cpb, groupId, staticMemberNames)

fun ClusterInfo.conditionallyUploadCordaPackage(
    name: String,
    cpb: String,
    groupId: String,
    staticMemberNames: List<String>
) = conditionallyUploadCordaPackage(name) {
    cpiUpload(cpb, groupId, staticMemberNames, name)
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

        assertWithRetry {
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

    val vNodesJson = assertWithRetry {
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

        val holdingId = createVNodeRequest["requestId"].textValue()

        // Wait for the vNode creation to propagate through the system before moving on
        eventually(duration = Duration.ofSeconds(30)) {
            assertThat(getVNode(holdingId).code).isNotEqualTo(404)
        }

        holdingId
    }
}

fun ClusterInfo.getExistingCpi(
    cpiName: String
) = cluster {
    cpiList()
        .toJson().apply {
            assertThat(contains("cpis"))
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
        command { createKey(tenantId, alias, category, scheme) }
        condition { it.code == 200 }
        failMessage("Failed to create key for holding id '$tenantId'")
    }.toJson()
    assertWithRetry {
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
    val result = getKey(tenantId, category, alias, ids)
    result.code == ResponseCode.OK.statusCode && result.toJson().fieldNames().hasNext()
}
