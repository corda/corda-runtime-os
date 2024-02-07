package repository

import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.core.utils.transaction
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import org.junit.jupiter.api.AfterAll
import java.util.UUID
import javax.persistence.EntityManagerFactory

@Suppress("warnings")
abstract class CryptoRepositoryTest {
    private val dbs: List<Pair<EntityManagerConfiguration, EntityManagerFactory>>

    private companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/crypto/db.changelog-master.xml"
        private const val MIGRATION_FILE_LOCATION_VNODE = "net/corda/db/schema/vnode-crypto/db.changelog-master.xml"
    }

    /**
     * Creates an in-memory database, applies the relevant migration scripts, and initialises
     * the entityManagerFactories.
     */
    init {
        val schemaUniqueId = UUID.randomUUID().toString().split("-").get(0)
        dbs = mapOf(
            "cluster" to MIGRATION_FILE_LOCATION,
            "vnode" to MIGRATION_FILE_LOCATION_VNODE,
            )
            .map { (schemaName, migrationFile) ->
                val dbChange = ClassloaderChangeLog(
                    linkedSetOf(
                        ClassloaderChangeLog.ChangeLogResourceFiles(
                            DbSchema::class.java.packageName,
                            listOf(migrationFile),
                            DbSchema::class.java.classLoader
                        )
                    )
                )
                val uniqueSchemaName = "${schemaName}_${schemaUniqueId}"
                val dbConfig =
                    DbUtils.getEntityManagerConfiguration(
                        inMemoryDbName = "${this::class.java.simpleName}-$uniqueSchemaName",
                        dbUser = "user-$uniqueSchemaName",
                        dbPassword = "123",
                        schemaName = uniqueSchemaName,
                        createSchema = true
                    )
                if (!DbUtils.isInMemory) {
                    dbConfig.dataSource.connection.transaction { connection ->
                        connection.prepareStatement("SET search_path TO \"$uniqueSchemaName\";").execute()
                    }
                }
                dbConfig.dataSource.connection.transaction { connection ->
                    LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange, uniqueSchemaName)
                }
                val entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
                    this::class.java.simpleName,
                    CryptoEntities.classes.toList(),
                    dbConfig
                )

                Pair(dbConfig, entityManagerFactory)
            }
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        dbs.forEach {
            it.first.close()
            it.second.close()
        }
    }

    @Suppress("Unused")
    private fun emfs(): List<EntityManagerFactory> = dbs.map { it.second }
}
