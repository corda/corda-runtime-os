package net.corda.crypto.softhsm.impl

import java.time.Instant
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneOffset
import javax.persistence.EntityManagerFactory

class WrappingRepositoryImpl(
    private val entityManagerFactory: EntityManagerFactory,
) : WrappingRepository {

    override fun close() = entityManagerFactory.close()

    override fun saveKey(alias: String, key: WrappingKeyInfo): WrappingKeyInfo = entityManagerFactory.createEntityManager().use {
        return it.transaction {
            it.merge(
                WrappingKeyEntity(
                    id = UUID.randomUUID(),
                    generation = key.generation,
                    alias = alias,
                    created = Instant.now(),
                    rotationDate = LocalDate.parse("9999-12-31").atStartOfDay().toInstant(ZoneOffset.UTC),
                    encodingVersion = key.encodingVersion,
                    algorithmName = key.algorithmName,
                    keyMaterial = key.keyMaterial,
                    isParentKeyManaged = false,
                    parentKeyReference = key.parentKeyAlias,
                )
            )
        }.toDto()
    }

    override fun findKey(alias: String): WrappingKeyInfo? =
        entityManagerFactory.createEntityManager().use {
            it.createQuery(
                "FROM ${WrappingKeyEntity::class.simpleName} AS k WHERE k.alias = :alias",
                WrappingKeyEntity::class.java
            ).setParameter("alias", alias).setMaxResults(1).resultList.singleOrNull()?.toDto()
        }
}

// NOTE: this should be on the entity object directly, but this means this repo (and the DTOs) need
//   to move to the crypto-persistence-model module first.
fun WrappingKeyEntity.toDto() =
    WrappingKeyInfo(
        encodingVersion = this.encodingVersion,
        algorithmName = this.algorithmName,
        keyMaterial = this.keyMaterial,
        generation = this.generation,
        parentKeyAlias = this.parentKeyReference
    )
        