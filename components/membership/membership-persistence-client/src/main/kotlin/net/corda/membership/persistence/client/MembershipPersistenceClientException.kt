package net.corda.membership.persistence.client

import net.corda.membership.lib.exceptions.MembershipPersistenceException

/**
 * An exception that indicate that the client had failed.
 */
open class MembershipPersistenceClientException(errorMsg: String) : MembershipPersistenceException(errorMsg)
