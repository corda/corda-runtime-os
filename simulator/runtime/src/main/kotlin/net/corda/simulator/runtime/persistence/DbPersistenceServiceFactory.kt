package net.corda.simulator.runtime.persistence

import net.corda.v5.base.types.MemberX500Name

/**
 * @see [PersistenceServiceFactory] for details.
 */
class DbPersistenceServiceFactory : PersistenceServiceFactory {
    override fun createPersistenceService(member: MemberX500Name): CloseablePersistenceService {
        return DbPersistenceService(member)
    }

}
