package net.corda.crypto.persistence.v1schema

import net.corda.crypto.persistence.CryptoRepository
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class V1CryptoRepositoryImpl(private val entityManagerFactory: EntityManagerFactory) :
    CryptoRepository {
    override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
        entityManagerFactory.createEntityManager().use {
            it.transaction { em ->
                em.persist(
                    V1WrappingKeyEntity(
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
            em.find(V1WrappingKeyEntity::class.java, alias)?.let { rec ->
                WrappingKeyInfo(
                    encodingVersion = rec.encodingVersion,
                    algorithmName = rec.algorithmName,
                    keyMaterial = rec.keyMaterial,
                )
            }
        }

    override fun close() = entityManagerFactory.close()
}