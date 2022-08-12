package net.corda.testutils.internal

import net.corda.testutils.services.CloseablePersistenceService
import net.corda.v5.base.types.MemberX500Name

interface PersistenceServiceFactory {
    fun createPersistenceService(member: MemberX500Name) : CloseablePersistenceService
}
