/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package net.corda.cpi.utils

import net.corda.data.packaging.CPIIdentifier
import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import javax.security.auth.x500.X500Principal

// TODO: Review this, using ~ as the separator
fun CPI.Identifier.toSerializedString(): String {
    return "$name~$version~${signerSummaryHash?.toString() ?: ""}~${identity?.name ?: ""}~${identity?.groupId ?: ""}"
}

fun CPI.Identifier.Companion.newInstance(src: String): CPI.Identifier {
    val params = src.split("~")
    val name = params[0]
    val version = params[1]
    val signerSummaryHash = if (params[2].isEmpty()) null else SecureHash.create(params[2])
    val identityName = if (params[3].isEmpty()) null else X500Principal(params[3])
    val identityGroup = if (params[4].isEmpty()) null else params[4]
    return if (identityName != null && identityGroup != null) {
               newInstance(name, version, signerSummaryHash, CPI.Identity.newInstance(identityName, identityGroup))
           } else newInstance(name, version, signerSummaryHash)
}

const val RPC_CPI_GROUP_NAME = "rpcCPIGroup"
const val RPC_CPI_CLIENT_NAME = "rpcCPIClient"
const val RPC_CPI_TOPIC_NAME = "rpcCPITopicName"

const val CPI_PUBLISHER_CLIENT_ID = "cpiPublisher"

const val CPI_SUBSCRIPTION_GROUP_NAME = "CPISubscriptionGroup"
const val CPI_LIST_TOPIC_NAME = "CPIListTopicName"

const val CPX_FILE_FINDER_PATTERN = "*.{cpi,cpb}"
const val CPX_FILE_FINDER_ROOT_DIR_CONFIG_PATH = "CPIDirectory"
const val CPX_KAFKA_FILE_CACHE_ROOT_DIR_CONFIG_PATH = "CPIKafkaCacheFilePath"

