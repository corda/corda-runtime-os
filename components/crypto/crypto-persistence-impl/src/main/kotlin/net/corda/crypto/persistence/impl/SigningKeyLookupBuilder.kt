package net.corda.crypto.persistence.impl

import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.SigningKeyOrderBy
import javax.persistence.EntityManager
import javax.persistence.TypedQuery
import javax.persistence.criteria.Predicate
import kotlin.reflect.KProperty

class SigningKeyLookupBuilder(private val entityManager: EntityManager) {
    private val cb = entityManager.criteriaBuilder
    private val cr = cb.createQuery(SigningKeyEntity::class.java)
    private val root = cr.from(SigningKeyEntity::class.java)
    private val predicates = mutableListOf<Predicate>()

    fun <T> equal(property: KProperty<T?>, value: T?) {
        if (value != null) {
            predicates.add(cb.equal(root.get<T>(property.name), value))
        }
    }

    fun <T : Comparable<T>> greaterThanOrEqualTo(property: KProperty<T?>, value: T?) {
        if (value != null) {
            predicates.add(
                cb.greaterThanOrEqualTo(root.get(property.name), value)
            )
        }
    }

    fun <T : Comparable<T>> lessThanOrEqualTo(property: KProperty<T?>, value: T?) {
        if (value != null) {
            predicates.add(
                cb.lessThanOrEqualTo(root.get(property.name), value)
            )
        }
    }

    @Suppress("SpreadOperator", "ComplexMethod")
    fun build(skip: Int, take: Int, orderBy: SigningKeyOrderBy): TypedQuery<SigningKeyEntity> {
        cr.where(cb.and(*predicates.toTypedArray()))
        when (orderBy) {
            SigningKeyOrderBy.NONE -> Unit
            SigningKeyOrderBy.ID -> ascOrderBy(SigningKeyEntity::keyId)
            SigningKeyOrderBy.TIMESTAMP -> ascOrderBy(SigningKeyEntity::timestamp)
            SigningKeyOrderBy.CATEGORY -> ascOrderBy(SigningKeyEntity::category)
            SigningKeyOrderBy.SCHEME_CODE_NAME -> ascOrderBy(SigningKeyEntity::schemeCodeName)
            SigningKeyOrderBy.ALIAS -> ascOrderBy(SigningKeyEntity::alias)
            SigningKeyOrderBy.MASTER_KEY_ALIAS -> ascOrderBy(SigningKeyEntity::masterKeyAlias)
            SigningKeyOrderBy.EXTERNAL_ID -> ascOrderBy(SigningKeyEntity::externalId)
            SigningKeyOrderBy.TIMESTAMP_DESC -> descOrderBy(SigningKeyEntity::timestamp)
            SigningKeyOrderBy.CATEGORY_DESC -> descOrderBy(SigningKeyEntity::category)
            SigningKeyOrderBy.SCHEME_CODE_NAME_DESC -> descOrderBy(SigningKeyEntity::schemeCodeName)
            SigningKeyOrderBy.ALIAS_DESC -> descOrderBy(SigningKeyEntity::alias)
            SigningKeyOrderBy.MASTER_KEY_ALIAS_DESC -> descOrderBy(SigningKeyEntity::masterKeyAlias)
            SigningKeyOrderBy.EXTERNAL_ID_DESC -> descOrderBy(SigningKeyEntity::externalId)
            SigningKeyOrderBy.ID_DESC -> descOrderBy(SigningKeyEntity::keyId)
        }
        return entityManager.createQuery(cr)
            .setFirstResult(skip)
            .setMaxResults(take)
    }

    private fun <T> ascOrderBy(property: KProperty<T>) {
        cr.orderBy(cb.asc(root.get<T>(property.name)))
    }

    private fun <T> descOrderBy(property: KProperty<T>) {
        cr.orderBy(cb.desc(root.get<T>(property.name)))
    }
}