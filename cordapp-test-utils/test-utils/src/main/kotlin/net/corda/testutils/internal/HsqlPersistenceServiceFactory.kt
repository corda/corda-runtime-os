package net.corda.testutils.internal

import net.corda.testutils.services.CloseablePersistenceService
import net.corda.testutils.services.DbPersistenceService
import net.corda.v5.base.types.MemberX500Name

class HsqlPersistenceServiceFactory : PersistenceServiceFactory {
    override fun createPersistenceService(member: MemberX500Name): CloseablePersistenceService {
        return DbPersistenceService(member)
    }

}
