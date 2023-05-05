package net.corda.membership.impl.persistence.service

import net.corda.membership.lib.exceptions.MembershipPersistenceException

/**
 * An exception that indicate that the request can be retried if it was sent via the MEMBERSHIP_DB_ASYNC_TOPIC topic.
 */
internal class RecoverableException(message: String) : MembershipPersistenceException(message)
