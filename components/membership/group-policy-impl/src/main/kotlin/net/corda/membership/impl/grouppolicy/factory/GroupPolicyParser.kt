package net.corda.membership.impl.grouppolicy.factory

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.membership.GroupPolicy
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger

class GroupPolicyParser {
    companion object {
        private val logger = contextLogger()
        const val EMPTY_GROUP_POLICY = "GroupPolicy file is empty."
        const val FAILED_PARSING = "GroupPolicy file is incorrectly formatted and parsing failed."
    }

    fun parse(groupPolicyJson: String?): GroupPolicy {
        return GroupPolicyImpl(
            try {
                if (groupPolicyJson.isNullOrBlank()) {
                    logger.error(EMPTY_GROUP_POLICY)
                    throw IllegalGroupPolicyFormat(EMPTY_GROUP_POLICY)
                } else {
                    ConfigFactory
                        .parseString(groupPolicyJson)
                        .root()
                        .unwrapped()
                }
            } catch (e: ConfigException.Parse) {
                logger.error(FAILED_PARSING)
                throw IllegalGroupPolicyFormat(FAILED_PARSING, e)
            }
        )
    }
}

class IllegalGroupPolicyFormat(message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)