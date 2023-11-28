package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpiCpkIdentifier
import net.corda.libs.cpi.datamodel.CpiCpkMetadata
import javax.persistence.EntityManager

interface CpiCpkRepository {
    fun exist(em: EntityManager, cpiCpkId: CpiCpkIdentifier): Boolean

    fun findById(em: EntityManager, cpiCpkId: CpiCpkIdentifier): CpiCpkMetadata?
}
