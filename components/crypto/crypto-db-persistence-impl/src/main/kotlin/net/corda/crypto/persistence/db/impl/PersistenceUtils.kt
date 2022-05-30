package net.corda.crypto.persistence.db.impl

import javax.persistence.EntityManager
import javax.persistence.EntityTransaction
import javax.persistence.PersistenceException


internal fun <R> EntityManager.doInTransaction(block: (EntityManager) -> R): R {
    val trx = beginTransaction()
    try {
        val result = block(this)
        trx.commit()
        return result
    } catch (e: PersistenceException) {
        trx.safelyRollback()
        throw e
    } catch (e: Throwable) {
        trx.safelyRollback()
        throw PersistenceException("Failed to execute in transaction.", e)
    }
}

internal fun EntityTransaction.safelyRollback() {
    try {
        rollback()
    } catch (e: Throwable) {
        // intentional
    }
}

internal fun EntityManager.beginTransaction(): EntityTransaction {
    val trx = transaction
    trx.begin()
    return trx
}