package net.corda.crypto.softhsm.impl

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.CryptoRepository
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import java.time.Instant
import javax.persistence.EntityManagerFactory

class V1CryptoRepositoryImpl(private val entityManagerFactory: EntityManagerFactory) :
    CryptoRepository {
    override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
        entityManagerFactory.createEntityManager().use {
            it.transaction { em ->
                em.persist(
                    WrappingKeyEntity(
                        alias = alias,
                        created = Instant.now(),
                        encodingVersion = key.encodingVersion,
                        algorithmName = key.algorithmName,
                        keyMaterial = key.keyMaterial,
                    )
                )
            }
        }
    }

    override fun findWrappingKey(alias: String): WrappingKeyInfo? =
        entityManagerFactory.createEntityManager().use { em ->
            em.find(WrappingKeyEntity::class.java, alias)?.let { rec ->
                WrappingKeyInfo(
                    encodingVersion = rec.encodingVersion,
                    algorithmName = rec.algorithmName,
                    keyMaterial = rec.keyMaterial,
                )
            }
        }

    override fun close() = entityManagerFactory.close()
}