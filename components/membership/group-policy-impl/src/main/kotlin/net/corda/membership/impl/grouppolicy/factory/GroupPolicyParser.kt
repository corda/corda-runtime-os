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
                    throw CordaRuntimeException(EMPTY_GROUP_POLICY)
                } else {
                    ConfigFactory
                        .parseString(groupPolicyJson)
                        .root()
                        .unwrapped()
                }

                return events.mapNotNull { event ->
                    event.value?.message?.takeIf {
                        it is AuthenticatedMessage && it.header.subsystem == FLOW_SESSION_SUBSYSTEM
                    }?.let {
                        Record(FLOW_MAPPER_EVENT_TOPIC, event.key, it.payload)
                    }
                }

                val outputEvents = mutableListOf<Record<*, *>>()
                events
                    .map{it.value?.message}
                    .filterNotNull()
                    .filter{it is AuthenticatedMessage && it.header.subsystem == FLOW_SESSION_SUBSYSTEM}
                    .forEach {
                        outputEvents.add(Record(FLOW_MAPPER_EVENT_TOPIC, it.key, message.payload))
                    }
                return outputEvents

            } catch (e: ConfigException.Parse) {
                logger.error(FAILED_PARSING)
                throw CordaRuntimeException(FAILED_PARSING, e)
            }
        )
    }
}