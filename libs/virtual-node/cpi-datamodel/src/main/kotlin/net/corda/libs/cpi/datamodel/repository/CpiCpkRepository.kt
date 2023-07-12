package net.corda.libs.cpi.datamodel.repository

import javax.persistence.EntityManager
import net.corda.libs.cpi.datamodel.CpiCpkMetadata
import net.corda.libs.cpi.datamodel.CpiCpkIdentifier

interface CpiCpkRepository {
    fun exist(em: EntityManager, cpiCpkId: CpiCpkIdentifier): Boolean

    fun findById(em: EntityManager, cpiCpkId: CpiCpkIdentifier): CpiCpkMetadata?
}
