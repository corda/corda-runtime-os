package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.packaging.core.CpiMetadata
import java.util.stream.Stream
import javax.persistence.EntityManager

class CpiMetadataRepositoryImpl: CpiMetadataRepository {
    override fun findAll(entityManager: EntityManager): Stream<CpiMetadata> {
        val criteriaBuilder = entityManager.criteriaBuilder!!
        val query = entityManager.criteriaBuilder!!.createQuery(CpiMetadataEntity::class.java)!!
        val root = query.from(CpiMetadataEntity::class.java)
        query.select(root)

        // Todo: Double check the comment below before merging the code
        // Based on the comment "Joining the other tables to ensure all data is fetched eagerly"
        // Do we still wat to fetch eagerly? If yes, how do you do it?
        return entityManager.createQuery(query).resultStream.map { it.toCpiMetadata() }
    }
}