package net.corda.membership.lib

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.utilities.time.UTCClock
import org.slf4j.Logger

const val NOTARY_SERVICE_NAME_KEY = "corda.notary.service.%s.name"
const val NOTARY_SERVICE_PROTOCOL_KEY = "corda.notary.service.%s.flow.protocol.name"
const val NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY = "corda.notary.service.%s.flow.protocol.versions.%s"
const val NOTARY_SERVICE_KEYS_KEY = "corda.notary.service.%s.keys.%s"
const val NOTARY_SERVICE_KEYS_PREFIX = "corda.notary.service.%s.keys"
const val EPOCH_KEY = "corda.epoch"
const val MPV_KEY = "corda.minimumPlatformVersion"
const val MODIFIED_TIME_KEY = "corda.modifiedTime"
const val NOTARIES_KEY = "corda.notary.service"

private val clock = UTCClock()
private val notaryServiceRegex = NOTARY_SERVICE_NAME_KEY.format("([0-9]+)").toRegex()

@Suppress("LongParameterList")
fun updateExistingNotaryService(
    currentParameters: Map<String, String>,
    notaryDetails: MemberNotaryDetails,
    notaryServiceNumber: Int,
    currentProtocolVersions: Collection<Int>,
    keyEncodingService: KeyEncodingService,
    logger: Logger,
): Pair<Int?, KeyValuePairList?> {
    val notaryServiceName = notaryDetails.serviceName.toString()
    logger.info("Adding notary to group parameters under existing notary service '$notaryServiceName'.")
    notaryDetails.serviceProtocol?.let {
        require(currentParameters[String.format(NOTARY_SERVICE_PROTOCOL_KEY, notaryServiceNumber)].toString() == it) {
            throw MembershipPersistenceException("Cannot add notary to notary service " +
                    "'$notaryServiceName' - protocols do not match.")
        }
        require(notaryDetails.serviceProtocolVersions.isNotEmpty()) {
            throw MembershipPersistenceException("Cannot add notary to notary service '$notaryServiceName' - protocol" +
                    "  versions are missing.")
        }
    }
    val versionsRegex = NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY.format(notaryServiceNumber, "([0-9]+)").toRegex()
    val updatedVersions = notaryDetails.serviceProtocolVersions.let {
        val versionIntersection = if (currentProtocolVersions.isEmpty()) {
            it
        } else {
            it.intersect(currentProtocolVersions.toSet())
        }
        versionIntersection.toSortedSet().mapIndexed { index, version ->
            KeyValuePair(
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, notaryServiceNumber, index),
                version.toString()
            )
        }
    }
    val notaryKeys = currentParameters.entries
        .filter { it.key.startsWith(String.format(NOTARY_SERVICE_KEYS_PREFIX, notaryServiceNumber)) }
        .map { it.value }
    val startingIndex = notaryKeys.size
    val newKeys = notaryDetails.keys
        .map { keyEncodingService.encodeAsString(it.publicKey) }
        .filterNot { notaryKeys.contains(it) }
        .apply {
            if (isEmpty()) {
                logger.warn(
                    "Group parameters not updated. Notary has no notary keys or " +
                            "its notary keys are already listed under notary service '$notaryServiceName'."
                )
                return null to null
            }
        }.mapIndexed { index, key ->
            KeyValuePair(
                String.format(
                    NOTARY_SERVICE_KEYS_KEY,
                    notaryServiceNumber,
                    startingIndex + index
                ),
                key
            )
        }
    val newEpoch = currentParameters[EPOCH_KEY]!!.toInt() + 1
    val parametersWithUpdatedEpoch = with(currentParameters) {
        filterNot { listOf(EPOCH_KEY, MODIFIED_TIME_KEY).contains(it.key) || it.key.matches(versionsRegex) }
            .map { KeyValuePair(it.key, it.value) } + listOf(
            KeyValuePair(EPOCH_KEY, newEpoch.toString()),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
        )
    }
    return newEpoch to KeyValuePairList(parametersWithUpdatedEpoch + newKeys + updatedVersions)
}

fun addNewNotaryService(
    currentParameters: Map<String, String>,
    notaryDetails: MemberNotaryDetails,
    keyEncodingService: KeyEncodingService,
    logger: Logger,
): Pair<Int, KeyValuePairList> {
    val notaryServiceName = notaryDetails.serviceName.toString()
    logger.info("Adding notary to group parameters under new notary service '$notaryServiceName'.")
    requireNotNull(notaryDetails.serviceProtocol) {
        throw MembershipPersistenceException("Cannot add notary to group parameters - notary protocol must be" +
                " specified to create new notary service '$notaryServiceName'.")
    }
    require(notaryDetails.serviceProtocolVersions.isNotEmpty()) {
        throw MembershipPersistenceException("Cannot add notary to notary service '$notaryServiceName' - protocol versions are missing.")
    }
    val newNotaryServiceNumber = currentParameters
        .filter { notaryServiceRegex.matches(it.key) }.size
    val protocolVersions = notaryDetails.serviceProtocolVersions.mapIndexed { index, version ->
        KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, newNotaryServiceNumber, index), version.toString())
    }
    val newService = notaryDetails.keys
        .mapIndexed { index, key ->
            KeyValuePair(
                String.format(NOTARY_SERVICE_KEYS_KEY, newNotaryServiceNumber, index),
                keyEncodingService.encodeAsString(key.publicKey)
            )
        } + listOf(
        KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, newNotaryServiceNumber), notaryServiceName),
        KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, newNotaryServiceNumber), notaryDetails.serviceProtocol)
    ) + protocolVersions
    val newEpoch = currentParameters[EPOCH_KEY]!!.toInt() + 1
    val parametersWithUpdatedEpoch = with(currentParameters) {
        filterNot { listOf(EPOCH_KEY, MODIFIED_TIME_KEY).contains(it.key) }
            .map { KeyValuePair(it.key, it.value) } + listOf(
            KeyValuePair(EPOCH_KEY, newEpoch.toString()),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
        )
    }
    return newEpoch to KeyValuePairList(parametersWithUpdatedEpoch + newService)
}