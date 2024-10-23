package net.corda.uniqueness.backingstore.impl

import net.corda.ledger.libs.uniqueness.backingstore.impl.JPABackingStoreEntities

/**
 * Creates a new (test) uniqueness database using the specified database information.
 */
object JPABackingStoreTestUtilities {
    fun getJPABackingStoreEntities(): Set<Class<*>> {
        return JPABackingStoreEntities.classes
    }
}
