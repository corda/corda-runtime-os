package net.corda.crypto.persistence.impl.tests.infra

import com.typesafe.config.ConfigRenderOptions
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.TestDbInfo
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.orm.utils.transaction
import net.corda.test.util.identity.createTestHoldingIdentity
import org.osgi.framework.FrameworkUtil
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory

object CryptoDBSetup {
    val vNodeHoldingIdentity = createTestHoldingIdentity(
        "CN=Alice, O=Alice Corp, L=LDN, C=GB",
        UUID.randomUUID().toString()
    )

    var connectionIds: Map<String, UUID> = mapOf()
        private set

    val clusterDb = TestDbInfo.createConfig()

    val cryptoDb = TestDbInfo(
        name = CordaDb.Crypto.persistenceUnitName
    )

    val vnodeDb = TestDbInfo(
        name = VirtualNodeDbType.CRYPTO.getConnectionName(vNodeHoldingIdentity.shortHash),
        schemaName = "vnode_crypto"
    )

    fun connectionId(name: String): UUID =
        connectionIds.getValue(name)

    fun setup() {
        val bundleContext = FrameworkUtil.getBundle(this::class.java.classLoader).get().bundleContext
        val databaseInstaller = DatabaseInstaller(
            bundleContext.getComponent(),
            bundleContext.getComponent(),
            bundleContext.getComponent()
        )
        val configEmf = databaseInstaller.setupClusterDatabase(
            clusterDb,
            "config",
            ConfigurationEntities.classes
        )
        databaseInstaller.setupDatabase(
            cryptoDb,
            "crypto",
            CryptoEntities.classes
        ).close()
        databaseInstaller.setupDatabase(
            vnodeDb,
            "vnode-crypto",
            CryptoEntities.classes
        ).close()
        connectionIds = addDbConnectionConfigs(configEmf, cryptoDb, vnodeDb)
        configEmf.close()
    }

    private fun addDbConnectionConfigs(
        configEmf: EntityManagerFactory,
        vararg dbs: TestDbInfo
    ): Map<String, UUID> {
        val ids = mutableMapOf<String, UUID>()
        dbs.forEach { db ->
            val configAsString = db.config.root().render(ConfigRenderOptions.concise())
            configEmf.transaction {
                val existing = it.createQuery(
                    """
                        SELECT c FROM DbConnectionConfig c WHERE c.name=:name AND c.privilege=:privilege
                    """.trimIndent(), DbConnectionConfig::class.java
                )
                    .setParameter("name", db.name)
                    .setParameter("privilege", DbPrivilege.DML)
                    .resultList
                ids[db.name] = if (existing.isEmpty()) {
                    val record = DbConnectionConfig(
                        UUID.randomUUID(),
                        db.name,
                        DbPrivilege.DML,
                        Instant.now(),
                        "sa",
                        "Test ${db.name}",
                        configAsString
                    )
                    it.persist(record)
                    record.id
                } else {
                    existing.first().id
                }
            }
        }
        return ids
    }
}
