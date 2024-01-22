@file:Suppress("TooManyFunctions")

package net.corda.e2etest.utilities

import com.fasterxml.jackson.module.kotlin.contains
import net.corda.rest.ResponseCode
import net.corda.test.util.eventually
import net.corda.utilities.seconds
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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

val signingCertLock = ReentrantLock()
fun ClusterInfo.conditionallyUploadCpiSigningCertificate() = cluster {
    signingCertLock.withLock {
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
) = DEFAULT_CLUSTER.conditionallyUploadCordaPackage(
    cpiName,
    cpbResourceName,
    groupId,
    staticMemberNames,
    customGroupParameters
)

fun ClusterInfo.conditionallyUploadCordaPackage(
    cpiName: String,
    cpbResourceName: String,
    groupId: String,
    staticMemberNames: List<String>,
    customGroupParameters: Map<String, Any> = emptyMap(),
) = conditionallyUploadCordaPackage(cpiName) {
    cpiUpload(cpbResourceName, groupId, staticMemberNames, cpiName, customGroupParameters = customGroupParameters)
}

private val uploading = ConcurrentHashMap<Pair<String, String>, Unit?>()
fun ClusterInfo.conditionallyUploadCordaPackage(
    name: String,
    cpiUpload: ClusterBuilder.() -> SimpleResponse
) = uploading.compute(Pair(this.id, name)) { _, _ ->
    cluster {
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
}

fun getOrCreateVirtualNodeFor(
    x500: String,
    cpiName: String
) = DEFAULT_CLUSTER.getOrCreateVirtualNodeFor(x500, cpiName)

val vNodeCreationSemaphore = Semaphore(2)
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

    vNodeCreationSemaphore.runWith {
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

private val keyExistsLock = ReentrantLock()
fun ClusterInfo.whenNoKeyExists(
    tenantId: String,
    alias: String? = null,
    category: String? = null,
    ids: List<String>? = null,
    block: () -> Unit
) = keyExistsLock.withLock {
    cluster {
        val result = assertWithRetryIgnoringExceptions {
            command { getKey(tenantId, category, alias, ids) }
            condition { it.code == ResponseCode.OK.statusCode }
            failMessage("Failed to get keys for tenant id '$tenantId', category '$category', alias '$alias' and IDs: $ids")
        }

        if (result.code != ResponseCode.OK.statusCode || !result.toJson().fieldNames().hasNext()) {
            block()
        }
    }
}

/**
 * This method triggers rotation of keys for crypto unmanaged wrapping keys.
 * It takes 3 input parameters, the old and new KeyAlias (in string type) and the status code (Int type)
 *   @param oldKeyAlias Old unmanaged root key alias that needs to be rotated.
 *   @param newKeyAlias New unmanaged root key alias.
 *   @param expectedHttpStatusCode Status code that should be displayed when the API is hit,
 *   helps to validate both positive or negative scenarios.
 */
fun ClusterInfo.rotateCryptoUnmanagedWrappingKeys(
    oldKeyAlias: String,
    newKeyAlias: String,
    expectedHttpStatusCode: Int
) = cluster {
    assertWithRetry {
        command { doRotateCryptoUnmanagedWrappingKeys(oldKeyAlias, newKeyAlias) }
        condition { it.code == expectedHttpStatusCode }
    }
}

/**
 * This method fetch the status of keys for unmanaged wrapping key rotation.
 * It takes 2 input parameters, the keyAlias (in String type) and the status code (in Int type)
 *  @param keyAlias The root key alias of which the status of the last key rotation will be shown.
 *  @param expectedHttpStatusCode Status code that should be displayed when the API is hit,
 *  helps to validate both positive or negative scenarios.
 */
fun ClusterInfo.getStatusForUnmanagedWrappingKeysRotation(
    keyAlias: String,
    expectedHttpStatusCode: Int
) = cluster {
    assertWithRetry {
        command { getCryptoUnmanagedWrappingKeysRotationStatus(keyAlias) }
        condition { it.code == expectedHttpStatusCode }
    }
}

/**
 * This method fetch the protocol version for unmanaged key Rotation.
 */
fun ClusterInfo.getProtocolVersionForUnmanagedKeyRotation(
) = cluster {
    assertWithRetry {
        command { getWrappingKeysProtocolVersion() }
        condition { it.code == ResponseCode.OK.statusCode }
    }
}

private fun <T> Semaphore.runWith(block: () -> T): T {
    this.acquire()
    try {
        return block()
    } finally {
        this.release()
    }
}