package net.corda.membership.impl

import net.corda.membership.GroupPolicy
import net.corda.membership.impl.GroupPolicyExtension.Companion.GROUP_ID
import net.corda.membership.impl.GroupPolicyExtension.Companion.REGISTRATION_PROTOCOL_KEY
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger

class GroupPolicyImpl(private val map: Map<String, Any>) : GroupPolicy, Map<String, Any> by map {

    companion object {
        private val logger = contextLogger()

        private const val PROVIDER_STRING_MISSING_ERROR =
            "Group policy is missing registration protocol configuration. Missing key: $REGISTRATION_PROTOCOL_KEY"

    }
    override val groupId: String
        get() = map[GROUP_ID].toString()

    override val registrationProtocol: String
        get() {
            val protocolName = get(REGISTRATION_PROTOCOL_KEY) as String?
            if(protocolName.isNullOrBlank()) {
                logger.error(PROVIDER_STRING_MISSING_ERROR)
                throw CordaRuntimeException(PROVIDER_STRING_MISSING_ERROR)
            }
            return protocolName
        }
}
