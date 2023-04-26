package net.corda.membership.impl.persistence.service

import net.corda.membership.lib.exceptions.MembershipPersistenceException

internal class RecoverableException(message: String) : MembershipPersistenceException(message)
