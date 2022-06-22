package net.corda.membership.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.membership.impl.MemberInfoExtension.Companion.CREATED_TIME
import net.corda.membership.impl.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.impl.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.impl.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.impl.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.GroupPolicy
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.MemberInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [GroupPolicyParser::class])
class GroupPolicyParser @Activate constructor(
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
            val now = clock.instant().toString()
            return MemberInfoImpl(
                memberProvidedContext = layeredPropertyMapFactory.create<MemberContextImpl>(
                    (mgmInfo + mapOf(GROUP_ID to groupPolicy.groupId)).toSortedMap()
                ),
                mgmProvidedContext = layeredPropertyMapFactory.create<MGMContextImpl>(
                    sortedMapOf(
                        CREATED_TIME to now,
                        MODIFIED_TIME to now,
                        STATUS to MEMBER_STATUS_ACTIVE,
                        IS_MGM to "true"
                    )
                )
            )
        } catch (e: Exception) {
            throw BadGroupPolicyException(MGM_INFO_FAILURE, e)
        }
    }
}
