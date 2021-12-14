package net.corda.membership.grouppolicy.factory

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.membership.GroupPolicy
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger

class GroupPolicyFactory {
    companion object {
        private val logger = contextLogger()
    }

    fun createGroupPolicy(groupPolicyJson: String?): GroupPolicy {
        return GroupPolicyImpl(
            try {
                if (groupPolicyJson.isNullOrBlank()) {
                    logger.info("Group policy file is empty.")
                    emptyMap()
                } else {
                    ConfigFactory
                        .parseString(groupPolicyJson)
                        .root()
                        .unwrapped()
                }
            } catch (e: ConfigException.Parse) {
                logger.error("Group policy file failed to parse. Check the file format.")
                throw CordaRuntimeException("GroupPolicy file is incorrectly formatted and parsing failed.", e)
            }
        )
    }
}