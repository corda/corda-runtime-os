package net.corda.cordapptestutils.internal.persistence

import net.corda.v5.base.types.MemberX500Name

class HsqlPersistenceServiceFactory : PersistenceServiceFactory {
    override fun createPersistenceService(member: MemberX500Name): CloseablePersistenceService {
        return DbPersistenceService(member)
    }

}
