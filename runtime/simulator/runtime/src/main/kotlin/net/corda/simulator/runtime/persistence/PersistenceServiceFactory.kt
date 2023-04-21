package net.corda.simulator.runtime.persistence

import net.corda.v5.base.types.MemberX500Name

/**
 * Creates a new [net.corda.v5.application.persistence.PersistenceService] for the given member.
 */
interface PersistenceServiceFactory {

    /**
     * @param member The member for whom to create a [net.corda.v5.application.persistence.PersistenceService].
     * @return A new [net.corda.v5.application.persistence.PersistenceService].
     */
    fun createPersistenceService(member: MemberX500Name) : CloseablePersistenceService
}
