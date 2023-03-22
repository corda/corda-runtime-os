package net.corda.crypto.softhsm.impl

import java.time.Instant
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import javax.persistence.EntityManagerFactory

class WrappingRepositoryImpl(
    private val entityManagerFactory: EntityManagerFactory,
) : WrappingRepository {

    override fun close() = entityManagerFactory.close()

    override fun saveKey(alias: String, key: WrappingKeyInfo) {
        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(
                WrappingKeyEntity(
                    alias = alias,
                    created = Instant.now(),
                    encodingVersion = key.encodingVersion,
                    algorithmName = key.algorithmName,
                    keyMaterial = key.keyMaterial
                )
            )
        }
    }

    override fun findKey(alias: String): WrappingKeyInfo? =
        entityManagerFactory.createEntityManager().use { em ->
            em.find(WrappingKeyEntity::class.java, alias)?.let { rec ->
                WrappingKeyInfo(
                    encodingVersion = rec.encodingVersion,
                    algorithmName = rec.algorithmName,
                    keyMaterial = rec.keyMaterial
                )
            }
        }
}