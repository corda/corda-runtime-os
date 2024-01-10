package net.corda.crypto.softhsm.impl

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.persistence.EntityManagerFactory

class WrappingRepositoryImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val tenantId: String,
) : WrappingRepository {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun close() = entityManagerFactory.close()

    override fun saveKeyWithId(alias: String, key: WrappingKeyInfo, id: UUID?): WrappingKeyInfo =
        entityManagerFactory.createEntityManager().use {
            return it.transaction {
                it.merge(
                    WrappingKeyEntity(
                        id = id?:UUID.randomUUID(),
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
            }.toDto().also {
                logger.info("Storing wrapping key with alias $alias in tenant $tenantId")
            }
        }

    override fun saveKey(alias: String, key: WrappingKeyInfo): WrappingKeyInfo =
        saveKeyWithId(alias, key, null)

    override fun findKey(alias: String): WrappingKeyInfo? = findKeyAndId(alias)?.second
    override fun findKeyAndId(alias: String): Pair<UUID, WrappingKeyInfo>? =
        entityManagerFactory.createEntityManager().use { it ->
            it.createQuery(
                "FROM ${WrappingKeyEntity::class.simpleName} AS k WHERE k.alias = :alias",
                WrappingKeyEntity::class.java
            ).setParameter("alias", alias).setMaxResults(1).resultList.singleOrNull()?.let {dao ->
                Pair(dao.id, dao.toDto())
            }
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
        