package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpiCpk
import net.corda.libs.cpi.datamodel.CpiCpkIdentifier
import javax.persistence.EntityManager

interface CpiCpkRepository {
    fun exists(em: EntityManager, id: CpiCpkIdentifier): Boolean
    fun findById(em: EntityManager, id: CpiCpkIdentifier): CpiCpk?
}