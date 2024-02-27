package net.corda.membership.impl.registration

import net.corda.virtualnode.toCorda
import org.slf4j.Logger

/**
 * A common logger for use across registration services/handlers to ensure consistent logging to aid in better analysis of logs.
 */
class RegistrationLogger(private val logger: Logger) {

    private companion object {
        const val REGISTRATION_ID = "registration-id"
        const val MEMBER_HOLDING_ID = "member-holding-id"
        const val MEMBER_NAME = "member-name"
        const val MGM_HOLDING_ID = "mgm-holding-id"
        const val MGM_NAME = "mgm-name"
        const val GROUP_ID = "group-id"
    }

    private val loggingContext: MutableMap<String, String> = mutableMapOf()

    fun setRegistrationId(registrationId: String): RegistrationLogger {
        loggingContext[REGISTRATION_ID] = registrationId
        return this
    }

    fun setMember(holdingId: net.corda.data.identity.HoldingIdentity): RegistrationLogger {
        return setMember(holdingId.toCorda())
    }

    fun setMember(holdingId: net.corda.virtualnode.HoldingIdentity): RegistrationLogger {
        loggingContext[MEMBER_HOLDING_ID] = holdingId.shortHash.value
        loggingContext[MEMBER_NAME] = holdingId.x500Name.toString()
        if (!loggingContext.containsKey(GROUP_ID)) {
            loggingContext[GROUP_ID] = holdingId.groupId
        }
        return this
    }

    fun setMgm(holdingId: net.corda.data.identity.HoldingIdentity): RegistrationLogger {
        return setMgm(holdingId.toCorda())
    }

    fun setMgm(holdingId: net.corda.virtualnode.HoldingIdentity): RegistrationLogger {
        loggingContext[MGM_HOLDING_ID] = holdingId.shortHash.value
        loggingContext[MGM_NAME] = holdingId.x500Name.toString()
        if (!loggingContext.containsKey(GROUP_ID)) {
            loggingContext[GROUP_ID] = holdingId.groupId
        }
        return this
    }

    fun info(msg: String) {
        logger.info(concatContext(msg))
    }

    fun info(msg: String, e: Throwable) {
        logger.info(concatContext(msg), e)
    }

    fun warn(msg: String) {
        logger.warn(concatContext(msg))
    }

    fun warn(msg: String, e: Throwable) {
        logger.warn(concatContext(msg), e)
    }

    fun error(msg: String) {
        logger.error(concatContext(msg))
    }

    fun error(msg: String, e: Throwable) {
        logger.error(concatContext(msg), e)
    }

    private fun concatContext(msg: String): String {
        return msg.trim() + " $loggingContext"
    }
}
