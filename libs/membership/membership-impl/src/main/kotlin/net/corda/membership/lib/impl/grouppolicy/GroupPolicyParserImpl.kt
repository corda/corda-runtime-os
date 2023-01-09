package net.corda.membership.lib.impl.grouppolicy

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.membership.lib.MemberInfoExtension.Companion.CREATION_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.grouppolicy.MemberGroupPolicy
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [GroupPolicyParser::class])
class GroupPolicyParserImpl @Activate constructor(
    @Reference(service = MemberInfoFactory::class)
    val memberInfoFactory: MemberInfoFactory
) : GroupPolicyParser {
    companion object {
        private val logger = contextLogger()
        const val EMPTY_GROUP_POLICY = "GroupPolicy file is empty."
        const val NULL_GROUP_POLICY = "GroupPolicy file is null."
        const val FAILED_PARSING = "GroupPolicy file is incorrectly formatted and parsing failed."
    }

    private val objectMapper = ObjectMapper()
    private val clock = UTCClock()

    private val mgmVersions = mapOf(
        1 to { holdingIdentity: HoldingIdentity,
               root: JsonNode,
               groupPolicyPropertiesQuery: () -> LayeredPropertyMap? ->
            net.corda.membership.lib.impl.grouppolicy.v1.MGMGroupPolicyImpl(
                holdingIdentity,
                root,
                groupPolicyPropertiesQuery
            )
        }
    )

    private val memberVersions = mapOf(
        1 to { root: JsonNode ->
            net.corda.membership.lib.impl.grouppolicy.v1.MemberGroupPolicyImpl(root)
        }
    )

    @Suppress("ThrowsCount")
    override fun parse(
        holdingIdentity: HoldingIdentity,
        groupPolicy: String?,
        groupPolicyPropertiesQuery: () -> LayeredPropertyMap?
    ): GroupPolicy {
        val node = when {
            groupPolicy == null -> {
                logger.error(NULL_GROUP_POLICY)
                throw BadGroupPolicyException(NULL_GROUP_POLICY)
            }
            groupPolicy.isBlank() -> {
                logger.error(EMPTY_GROUP_POLICY)
                throw BadGroupPolicyException(EMPTY_GROUP_POLICY)
            }
            else -> {
                try {
                    objectMapper.readTree(groupPolicy)
                } catch (e: Exception) {
                    logger.error("$FAILED_PARSING Caused by: ${e.message}")
                    throw BadGroupPolicyException(FAILED_PARSING, e)
                }
            }
        }
        val version = node.getFileFormatVersion()

        return if (MGM_DEFAULT_GROUP_ID == node.getGroupId()) {
            mgmVersions[version]?.invoke(holdingIdentity, node, groupPolicyPropertiesQuery)
        } else {
            memberVersions[version]?.invoke(node)
        }
            ?: throw BadGroupPolicyException(
                "No supported version of the group policy file available for version $version"
            )
    }

    override fun parseMember(groupPolicy: String): MemberGroupPolicy? {
        val node = when {
            groupPolicy.isBlank() -> {
                logger.error(EMPTY_GROUP_POLICY)
                throw BadGroupPolicyException(EMPTY_GROUP_POLICY)
            }
            else -> {
                try {
                    objectMapper.readTree(groupPolicy)
                } catch (e: Exception) {
                    logger.error("$FAILED_PARSING Caused by: ${e.message}")
                    throw BadGroupPolicyException(FAILED_PARSING, e)
                }
            }
        }
        if (MGM_DEFAULT_GROUP_ID == node.getGroupId()) {
            return null
        }
        val version = node.getFileFormatVersion()
        return memberVersions[version]?.invoke(node) ?: throw BadGroupPolicyException(
            "No supported version of the group policy file available for version $version"
        )
    }

    private fun JsonNode.getFileFormatVersion() = this[FILE_FORMAT_VERSION]?.let {
        if (it.isInt) {
            it.intValue()
        } else {
            throw BadGroupPolicyException("File format version is not an integer value.")
        }
    }
        ?: throw BadGroupPolicyException(
            "Could not find $FILE_FORMAT_VERSION at the root level of the group policy file."
        )

    private fun JsonNode.getGroupId() = this[GROUP_ID]?.let {
        if (it.isTextual) {
            it.textValue()
        } else {
            throw BadGroupPolicyException("Group ID is not a text value.")
        }
    } ?: throw BadGroupPolicyException("Could not find $GROUP_ID at the root level of the group policy file.")

    @Suppress("SpreadOperator")
    override fun getMgmInfo(
        holdingIdentity: HoldingIdentity,
        groupPolicy: String
    ): MemberInfo? {
        val parsedGroupPolicy = try {
            parse(holdingIdentity, groupPolicy) {
                logger.debug("Tried to query for MGM group policy, which is inaccessible for member.")
                null
            }
        } catch (e: BadGroupPolicyException) {
            logger.error("Unable to parse group policy file.", e)
            null
        }
        return parsedGroupPolicy?.mgmInfo?.let {
            val now = clock.instant().toString()
            memberInfoFactory.create(
                it.toSortedMap(),
                sortedMapOf(
                    CREATION_TIME to now,
                    MODIFIED_TIME to now,
                    STATUS to MEMBER_STATUS_ACTIVE,
                    IS_MGM to "true"
                )
            )
        }
    }
}
