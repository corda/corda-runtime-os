package net.corda.membership.lib

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.libs.packaging.hash
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.GroupParameters
import org.slf4j.Logger

const val NOTARY_SERVICE_NAME_KEY = "corda.notary.service.%s.name"
const val NOTARY_SERVICE_PLUGIN_KEY = "corda.notary.service.%s.plugin"
const val NOTARY_SERVICE_KEYS_KEY = "corda.notary.service.%s.keys.%s"
const val NOTARY_SERVICE_KEYS_PREFIX = "corda.notary.service.%s.keys"
const val EPOCH_KEY = "corda.epoch"
const val MPV_KEY = "corda.minimumPlatformVersion"
const val MODIFIED_TIME_KEY = "corda.modifiedTime"
const val NOTARIES_KEY = "corda.notary.service"

val notaryServiceRegex = NOTARY_SERVICE_NAME_KEY.format("([0-9]+)").toRegex()

@Suppress("LongParameterList")
fun updateExistingNotaryService(
    currentParameters: Map<String, String>,
    notaryDetails: MemberNotaryDetails,
    notaryServiceNumber: Int,
    keyEncodingService: KeyEncodingService,
    logger: Logger,
    clock: Clock = UTCClock()
): Pair<Int?, KeyValuePairList?> {
    val notaryServiceName = notaryDetails.serviceName.toString()
    logger.info("Adding notary to group parameters under existing notary service '$notaryServiceName'.")
    notaryDetails.servicePlugin?.let {
        require(currentParameters[String.format(NOTARY_SERVICE_PLUGIN_KEY, notaryServiceNumber)].toString() == it) {
            throw MembershipPersistenceException(
                "Cannot add notary to notary service " +
                        "'$notaryServiceName' - plugin types do not match."
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
        filterNot { listOf(EPOCH_KEY, MODIFIED_TIME_KEY).contains(it.key) }
            .map { KeyValuePair(it.key, it.value) } + listOf(
            KeyValuePair(EPOCH_KEY, newEpoch.toString()),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
        )
    }
    return newEpoch to KeyValuePairList(parametersWithUpdatedEpoch + newKeys)
}

fun addNewNotaryService(
    currentParameters: Map<String, String>,
    notaryDetails: MemberNotaryDetails,
    keyEncodingService: KeyEncodingService,
    logger: Logger,
    clock: Clock = UTCClock()
): Pair<Int, KeyValuePairList> {
    val notaryServiceName = notaryDetails.serviceName.toString()
    logger.info("Adding notary to group parameters under new notary service '$notaryServiceName'.")
    requireNotNull(notaryDetails.servicePlugin) {
        throw MembershipPersistenceException(
            "Cannot add notary to group parameters - notary plugin must be" +
                    " specified to create new notary service '$notaryServiceName'."
        )
    }
    val newNotaryServiceNumber = currentParameters
        .filter { notaryServiceRegex.matches(it.key) }.size
    val newService = notaryDetails.keys
        .mapIndexed { index, key ->
            KeyValuePair(
                String.format(NOTARY_SERVICE_KEYS_KEY, newNotaryServiceNumber, index),
                keyEncodingService.encodeAsString(key.publicKey)
            )
        } + listOf(
        KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, newNotaryServiceNumber), notaryServiceName),
        KeyValuePair(String.format(NOTARY_SERVICE_PLUGIN_KEY, newNotaryServiceNumber), notaryDetails.servicePlugin)
    )
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

/**
 * Returns the serialised bytes representing the group parameters. The group parameters are always stored serialised
 * internally to support signing operations.
 */
val GroupParameters.bytes: ByteArray?
    get() = when (this) {
        is InternalGroupParameters -> bytes
        else -> null
    }

/**
 * Returns the MGM's signature over group parameters if available. In dynamic networks,
 * this should also be provided to members by the MGM.
 * The MGM's view of group parameters is not signed since it signs on distribution and the parameters
 * in static networks are not signed since there is no MGM.
 */
val GroupParameters.signature: DigitalSignature.WithKey?
    get() = when (this) {
        is SignedGroupParameters -> signature
        else -> null
    }

/**
 * Returns the [SecureHash] of the group parameters sorted alphabetically by key.
 * Sorting the properties is essential to ensure a consistent hash.
 */
fun GroupParameters.hash(): SecureHash = when (this) {
    is InternalGroupParameters -> bytes.hash(DigestAlgorithmName.SHA2_256)
    else -> throw CordaRuntimeException(
        "Group parameters implementation must implement " +
                "${InternalGroupParameters::class.java.simpleName} to give the hash function access to the underlying" +
                "byte array for the group parameters."
    )
}
