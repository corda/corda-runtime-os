package net.corda.uniqueness.backingstore.impl

import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.TestDbInfo
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.impl.JpaEntitiesRegistryImpl
import javax.persistence.EntityManagerFactory

/**
 * Creates a new (test) uniqueness database using the specified database information.
 */
object JPABackingStoreTestUtilities {
    fun setupUniquenessDatabase(dbInfo: TestDbInfo): EntityManagerFactory {
        return DatabaseInstaller(
            EntityManagerFactoryFactoryImpl(),
            LiquibaseSchemaMigratorImpl(),
            JpaEntitiesRegistryImpl()
        ).setupDatabase(
            dbInfo,
            "vnode-uniqueness",
            JPABackingStoreEntities.classes
        )
    }
}
