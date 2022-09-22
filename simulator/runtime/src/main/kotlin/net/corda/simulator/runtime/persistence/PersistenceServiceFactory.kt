package net.corda.simulator.runtime.persistence

import net.corda.v5.base.types.MemberX500Name

interface PersistenceServiceFactory {
    fun createPersistenceService(member: MemberX500Name) : CloseablePersistenceService
}
