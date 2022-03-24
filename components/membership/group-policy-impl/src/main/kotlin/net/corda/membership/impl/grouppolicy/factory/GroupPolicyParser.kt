package net.corda.membership.impl.grouppolicy.factory

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.membership.GroupPolicy
import net.corda.membership.exceptions.BadGroupPolicyException
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.v5.base.util.contextLogger

class GroupPolicyParser {
    companion object {
        private val logger = contextLogger()
        const val EMPTY_GROUP_POLICY = "GroupPolicy file is empty."
        const val NULL_GROUP_POLICY = "GroupPolicy file is null."
        const val FAILED_PARSING = "GroupPolicy file is incorrectly formatted and parsing failed."
    }

    private val objectMapper = ObjectMapper()

    /**
     * Parses a GroupPolicy from [String] to [GroupPolicy].
     *
     * @throws [BadGroupPolicyException] if the input string is null, blank, or cannot be parsed.
     */
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
                        objectMapper.readValue(groupPolicyJson, Map::class.java) as Map<String, Any>
                    } catch (e: Exception) {
                        logger.error("$FAILED_PARSING Caused by: ${e.message}")
                        throw BadGroupPolicyException(FAILED_PARSING, e)
                    }
                }
            }
        )
    }
}