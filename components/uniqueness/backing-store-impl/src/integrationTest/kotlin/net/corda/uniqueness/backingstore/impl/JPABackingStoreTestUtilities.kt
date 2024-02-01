package net.corda.uniqueness.backingstore.impl
/**
 * Creates a new (test) uniqueness database using the specified database information.
 */
object JPABackingStoreTestUtilities {
    fun getJPABackingStoreEntities(): Set<Class<*>> {
        return JPABackingStoreEntities.classes
    }
}
