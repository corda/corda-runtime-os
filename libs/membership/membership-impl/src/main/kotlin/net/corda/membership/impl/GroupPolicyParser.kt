package net.corda.membership.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.membership.GroupPolicy
import net.corda.membership.exceptions.BadGroupPolicyException
import net.corda.membership.impl.MemberInfoExtension.Companion.SESSION_KEY
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.calculateHash
import net.corda.v5.membership.MemberInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

@Component(service = [GroupPolicyParser::class])
class GroupPolicyParser @Activate constructor(
    @Reference(service = KeyEncodingService::class)
    val keyEncodingService: KeyEncodingService,
    @Reference(service = LayeredPropertyMapFactory::class)
    val layeredPropertyMapFactory: LayeredPropertyMapFactory
) {
    companion object {
        private val logger = contextLogger()
        const val EMPTY_GROUP_POLICY = "GroupPolicy file is empty."
        const val NULL_GROUP_POLICY = "GroupPolicy file is null."
        const val FAILED_PARSING = "GroupPolicy file is incorrectly formatted and parsing failed."
        const val MGM_INFO_FAILURE = "Failed to build MGM MemberInfo from GroupPolicy file."
        const val MGM_INFO = "mgmInfo"
        const val PROTOCOL_PARAMETERS = "protocolParameters"
    }

    private val objectMapper = ObjectMapper()
    private val clock = UTCClock()

    /**
     * Parses a GroupPolicy from [String] to [GroupPolicy].
     *
     * @throws [BadGroupPolicyException] if the input string is null, blank, or cannot be parsed.
     */
    @Suppress("ThrowsCount")
    fun parse(groupPolicyJson: String?): GroupPolicy {
        return GroupPolicyImpl(
            when {
                groupPolicyJson == null -> {
                    logger.error(NULL_GROUP_POLICY)
                    throw BadGroupPolicyException(NULL_GROUP_POLICY)
                }
                groupPolicyJson.isBlank() -> {
                    logger.error(EMPTY_GROUP_POLICY)
                    throw BadGroupPolicyException(EMPTY_GROUP_POLICY)
                }
                else -> {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        objectMapper.readValue(groupPolicyJson, Map::class.java) as Map<String, Any?>
                    } catch (e: Exception) {
                        logger.error("$FAILED_PARSING Caused by: ${e.message}")
                        throw BadGroupPolicyException(FAILED_PARSING, e)
                    }
                }
            }
        )
    }

    /**
     * Constructs MGM [MemberInfo] from details specified in [GroupPolicy].
     */
    @Suppress("UNCHECKED_CAST", "SpreadOperator")
    fun getMgmInfo(groupPolicyJson: String): MemberInfo? {
        val groupPolicy = parse(groupPolicyJson)
        val mgmInfo = groupPolicy[MGM_INFO] as? Map<String, String> ?: return null
        try {
            val encodedSessionKey = mgmInfo[SESSION_KEY]
                ?: throw IllegalArgumentException("Session key must be specified in the group policy.")
            val sessionKey = keyEncodingService.decodePublicKey(encodedSessionKey)
            val now = clock.instant().toString()
            return MemberInfoImpl(
                memberProvidedContext = layeredPropertyMapFactory.create<MemberContextImpl>(
                    (mgmInfo + mapOf(
                        *convertKeys(listOf(encodedSessionKey)).toTypedArray(),
                        *generateKeyHashes(listOf(sessionKey)).toTypedArray(),
                        MemberInfoExtension.PARTY_OWNING_KEY to encodedSessionKey
                    )).toSortedMap()
                ),
                mgmProvidedContext = layeredPropertyMapFactory.create<MGMContextImpl>(
                    sortedMapOf(
                        MemberInfoExtension.CREATED_TIME to now,
                        MemberInfoExtension.MODIFIED_TIME to now,
                        MemberInfoExtension.STATUS to MemberInfoExtension.MEMBER_STATUS_ACTIVE,
                        MemberInfoExtension.IS_MGM to "true"
                    )
                )
            )
        } catch (e: Exception) {
            throw BadGroupPolicyException(MGM_INFO_FAILURE, e)
        }
    }

    /**
     * Converts keys from JSON format to the keys expected in [MemberInfo].
     */
    private fun convertKeys(
        keys: List<String>
    ): List<Pair<String, String>> {
        require(keys.isNotEmpty()) { "List of MGM keys cannot be empty." }
        return keys.mapIndexed { index, identityKey ->
            String.format(
                MemberInfoExtension.IDENTITY_KEYS_KEY,
                index
            ) to identityKey
        }
    }

    /**
     * Generates key hashes from MGM keys.
     */
    private fun generateKeyHashes(
        keys: List<PublicKey>
    ): List<Pair<String, String>> {
        return keys.mapIndexed { index, identityKey ->
            val hash = identityKey.calculateHash()
            String.format(
                MemberInfoExtension.IDENTITY_KEY_HASHES_KEY,
                index
            ) to hash.toString()
        }
    }
}
