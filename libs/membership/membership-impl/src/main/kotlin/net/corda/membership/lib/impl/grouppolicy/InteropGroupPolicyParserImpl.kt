package net.corda.membership.lib.impl.grouppolicy

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.*
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID
import net.corda.membership.lib.impl.grouppolicy.v1.InteropGroupPolicyImpl
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [InteropGroupPolicyParser::class])
class InteropGroupPolicyParserImpl @Activate constructor(
    @Reference(service = MemberInfoFactory::class)
    val memberInfoFactory: MemberInfoFactory
) : InteropGroupPolicyParser {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val EMPTY_GROUP_POLICY = "GroupPolicy file is empty."
        const val NULL_GROUP_POLICY = "GroupPolicy file is null."
        const val FAILED_PARSING = "GroupPolicy file is incorrectly formatted and parsing failed."
    }

    private val objectMapper = ObjectMapper()
    private val clock = UTCClock()

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

        return memberVersions[version]?.invoke(node)
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

    /**
     * Returns the implementation of the interop group policy, holding identity to be used in later implementations
     */
    override fun get(holdingIdentity: HoldingIdentity): InteropGroupPolicy {
        return InteropGroupPolicyImpl()
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
}
