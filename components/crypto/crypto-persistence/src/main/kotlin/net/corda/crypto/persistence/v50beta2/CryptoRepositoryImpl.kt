package net.corda.crypto.persistence.v50beta2

import net.corda.crypto.persistence.CryptoRepository
import net.corda.crypto.persistence.WrappingKeyInfo
import java.time.Instant
import javax.persistence.EntityManagerFactory

class CryptoRepositoryImpl(private val entityManagerFactory: EntityManagerFactory) : CryptoRepository {
    fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
        entityManagerFactory().transaction { em ->
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

    fun findWrappingKey(alias: String): WrappingKeyInfo? = entityManagerFactory().use { em ->
        em.find(WrappingKeyEntity::class.java, alias)?.let { rec ->
            WrappingKeyInfo(
                encodingVersion = rec.encodingVersion,
                algorithmName = rec.algorithmName,
                keyMaterial = rec.keyMaterial
            )
        }
    }

}