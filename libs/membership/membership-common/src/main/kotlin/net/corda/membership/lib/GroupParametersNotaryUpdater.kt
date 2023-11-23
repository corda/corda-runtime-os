package net.corda.membership.lib

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.membership.lib.exceptions.InvalidGroupParametersUpdateException
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import org.slf4j.LoggerFactory

class GroupParametersNotaryUpdater(
    private val keyEncodingService: KeyEncodingService,
    private val clock: Clock = UTCClock()
) {
    companion object {
        const val NOTARIES_KEY = "corda.notary.service."
        private const val NOTARY_SERVICE_KEY_PREFIX = "corda.notary.service.%s"
        const val NOTARY_SERVICE_NAME_KEY = "$NOTARY_SERVICE_KEY_PREFIX.name"
        const val NOTARY_SERVICE_BACKCHAIN_REQUIRED = "$NOTARY_SERVICE_KEY_PREFIX.backchain.required"
        const val NOTARY_SERVICE_PROTOCOL_KEY = "$NOTARY_SERVICE_KEY_PREFIX.flow.protocol.name"
        const val NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY = "$NOTARY_SERVICE_KEY_PREFIX.flow.protocol.version.%s"
        const val NOTARY_SERVICE_KEYS_PREFIX = "$NOTARY_SERVICE_KEY_PREFIX.keys"
        const val NOTARY_SERVICE_KEYS_KEY = "$NOTARY_SERVICE_KEYS_PREFIX.%s"

        const val EPOCH_KEY = "corda.epoch"
        const val MODIFIED_TIME_KEY = "corda.modifiedTime"
        const val MPV_KEY = "corda.minimum.platform.version"
        val notaryServiceRegex = NOTARY_SERVICE_NAME_KEY.format("([0-9]+)").toRegex()
    }
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun addNewNotaryService(
        currentParameters: Map<String, String>,
        notaryDetails: MemberNotaryDetails,
    ): Pair<Int, KeyValuePairList> {
        val notaryServiceName = notaryDetails.serviceName.toString()
        logger.info("Adding notary to group parameters under new notary service '$notaryServiceName'.")
        requireNotNull(notaryDetails.serviceProtocol) {
            throw InvalidGroupParametersUpdateException("Cannot add notary to group parameters - notary protocol must be" +
                    " specified to create new notary service '$notaryServiceName'."
            )
        }
        require(notaryDetails.serviceProtocolVersions.isNotEmpty()) {
            throw InvalidGroupParametersUpdateException(
                "Cannot add notary to notary service '$notaryServiceName' - protocol versions are missing."
            )
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
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, newNotaryServiceNumber), notaryDetails.serviceProtocol),
            KeyValuePair(
                String.format(NOTARY_SERVICE_BACKCHAIN_REQUIRED, newNotaryServiceNumber),
                notaryDetails.backchainRequired.toString()
            )
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

    fun updateExistingNotaryService(
        currentParameters: Map<String, String>,
        notaryDetails: MemberNotaryDetails,
        notaryServiceNumber: Int,
        currentProtocolVersions: Collection<Int>
    ): Pair<Int?, KeyValuePairList?> {
        val notaryServiceName = notaryDetails.serviceName.toString()
        logger.info("Adding notary to group parameters under existing notary service '$notaryServiceName'.")
        notaryDetails.serviceProtocol?.let {
            require(currentParameters[String.format(NOTARY_SERVICE_PROTOCOL_KEY, notaryServiceNumber)].toString() == it) {
                throw InvalidGroupParametersUpdateException("Cannot add notary to notary service " +
                        "'$notaryServiceName' - protocols do not match.")
            }
            require(notaryDetails.serviceProtocolVersions.isNotEmpty()) {
                throw InvalidGroupParametersUpdateException("Cannot add notary to notary service '$notaryServiceName' - protocol" +
                        "  versions are missing.")
            }
        }
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
        val versionsRegex = NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY.format(notaryServiceNumber, "([0-9]+)").toRegex()
        return newEpoch to KeyValuePairList(currentParameters.filterNot { it.key.matches(versionsRegex) }
            .updateEpochAndModifiedTime(newEpoch) + newKeys + updatedVersions)
    }

    fun removeNotaryFromExistingNotaryService(
        currentParameters: Map<String, String>,
        notaryDetails: MemberNotaryDetails,
        notaryServiceNumber: Int,
        otherNotaryDetails: List<MemberNotaryDetails>
    ): Pair<Int?, KeyValuePairList?> {
        val notaryServiceName = notaryDetails.serviceName.toString()
        logger.info("Removing notary from group parameters under notary service '$notaryServiceName'.")
        notaryDetails.serviceProtocol?.let {
            require(currentParameters[String.format(NOTARY_SERVICE_PROTOCOL_KEY, notaryServiceNumber)].toString() == it) {
                throw InvalidGroupParametersUpdateException("Cannot remove notary from notary service " +
                        "'$notaryServiceName' - protocols do not match.")
            }
            require(notaryDetails.serviceProtocolVersions.isNotEmpty()) {
                throw InvalidGroupParametersUpdateException("Cannot remove notary from notary service '$notaryServiceName' - protocol" +
                        "  versions are missing.")
            }
        }
        val versionIntersection = otherNotaryDetails.map { it.serviceProtocolVersions }.reduce { acc, it -> acc.intersect(it.toSet()) }
            .toSortedSet()

        val updatedVersions = versionIntersection.mapIndexed { index, version ->
                KeyValuePair(
                    String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, notaryServiceNumber, index),
                    version.toString()
                )
            }
        val updatedKeys = otherNotaryDetails.flatMap { otherNotary ->
            otherNotary.keys.map { keyEncodingService.encodeAsString(it.publicKey) }
        }.toSet().mapIndexed { index, key ->
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, notaryServiceNumber, index), key)
        }
        val versionsRegex = NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY.format(notaryServiceNumber, "([0-9]+)").toRegex()
        val newEpoch = currentParameters[EPOCH_KEY]!!.toInt() + 1
        val parametersWithUpdatedEpoch = currentParameters.filterNot {
            it.key.matches(versionsRegex) || it.key.startsWith(String.format(NOTARY_SERVICE_KEYS_PREFIX, notaryServiceNumber))
        }.updateEpochAndModifiedTime(newEpoch)
        return newEpoch to KeyValuePairList(parametersWithUpdatedEpoch + updatedKeys + updatedVersions)
    }

    fun removeNotaryService(currentParameters: Map<String, String>, notaryServiceNumber: Int): Pair<Int, KeyValuePairList> {
        val notaryServiceName = currentParameters[String.format(NOTARY_SERVICE_NAME_KEY, notaryServiceNumber)]
        logger.info("Removing notary from group parameters under notary service '$notaryServiceName'.")
        val newEpoch = currentParameters[EPOCH_KEY]!!.toInt() + 1
        return newEpoch to KeyValuePairList(currentParameters.removeNotaryService(notaryServiceNumber).updateEpochAndModifiedTime(newEpoch))
    }

    private fun Map<String, String>.updateEpochAndModifiedTime(newEpoch: Int): List<KeyValuePair> {
        return with(this) {
            filterNot { listOf(EPOCH_KEY, MODIFIED_TIME_KEY).contains(it.key) }
                .map { KeyValuePair(it.key, it.value) } + listOf(
                KeyValuePair(EPOCH_KEY, newEpoch.toString()),
                KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
            )
        }
    }

    private fun Map<String, String>.removeNotaryService(notaryServiceNumber: Int): Map<String, String> {
        val numberOfNotaryServices = this.filter { notaryServiceRegex.matches(it.key) }.size
        val keyPrefix = NOTARY_SERVICE_KEY_PREFIX.format(notaryServiceNumber)
        val listWithNotaryServiceRemoved = this.filterNot { it.key.contains(keyPrefix) }.toMutableMap()
        //Make the Notary services numbers contiguous
        for (i in notaryServiceNumber + 1 until numberOfNotaryServices) {
            val currentKeyPrefix = NOTARY_SERVICE_KEY_PREFIX.format(i)
            val newKeyPrefix = NOTARY_SERVICE_KEY_PREFIX.format(i - 1)
            val keysToUpdate = listWithNotaryServiceRemoved.keys.filter { it.startsWith(currentKeyPrefix) }
            for (key in keysToUpdate) {
                listWithNotaryServiceRemoved.remove(key)?.let { value ->
                    listWithNotaryServiceRemoved[key.replace(currentKeyPrefix, newKeyPrefix)] = value
                }
            }
        }
        return listWithNotaryServiceRemoved
    }

}