package net.corda.utxo.token.sync.integration.tests

import com.typesafe.config.ConfigFactory
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.db.testkit.TestDbConnectionManager
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.utxo.token.sync.converters.impl.DbRecordConverterImpl
import net.corda.utxo.token.sync.integration.tests.entities.UtxoTransactionComponentEntity
import net.corda.utxo.token.sync.integration.tests.entities.UtxoTransactionEntity
import net.corda.utxo.token.sync.integration.tests.entities.UtxoTransactionOutputEntity
import net.corda.utxo.token.sync.services.impl.UtxoTokenRepositoryImpl
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class WakeupEventTest {
    private val connectionId = UUID.randomUUID()
    private val dbConnectionManager: DbConnectionManager
    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }

    private companion object {
        private const val MIGRATION_FILE_LOCATION =
            "net/corda/db/schema/vnode-vault/migration/ledger-utxo-creation-v1.0.xml"
        private val configFactory = SmartConfigFactory.create(
            ConfigFactory.parseString(
                """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=key
            ${SmartConfigFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            )
        )
    }

    /**
     * Creates database and run db migration
     */
    init {
        // uncomment this to run the test against local Postgres
        // System.setProperty("postgresPort", "5432")
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf(MIGRATION_FILE_LOCATION),
                    DbSchema::class.java.classLoader
                )
            )
        )
        val config = configFactory.create(DbUtils.createConfig("valut_db"))
        val testDbManager = TestDbConnectionManager(EntityManagerFactoryFactoryImpl())
        dbConnectionManager = testDbManager
        dbConnectionManager.initialise(config)
        val dataSource = dbConnectionManager.createDatasource(connectionId)

        dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
    }

    @Test
    fun simpleTest() {
        try {
            dbConnectionManager.createDatasource(connectionId).connection.catalog
            val emf = dbConnectionManager.createEntityManagerFactory(
                connectionId,
                JpaEntitiesSet.create(
                    connectionId.toString(),
                    setOf(
                        UtxoTransactionEntity::class.java,
                        UtxoTransactionComponentEntity::class.java,
                        UtxoTransactionOutputEntity::class.java
                    )
                )
            )

            val ledgerTx = UtxoTransactionEntity("tx1", byteArrayOf(), "account", Instant.now())
            val component = UtxoTransactionComponentEntity(
                transactionId = "tx1",
                groupIdx = UtxoComponentGroup.OUTPUTS.ordinal,
                leafIdx = 0,
                data= byteArrayOf(),
                hash = "",
                created = Instant.now(),
            )

            val output = UtxoTransactionOutputEntity(
                transactionId = "tx1",
                groupIdx = UtxoComponentGroup.OUTPUTS.ordinal,
                leafIdx = 0,
                type = UtxoTransactionEntity::class.java.name,
                tokenType = UtxoTransactionEntity::class.java.name,
                tokenIssuerHash = getSecureHash(BOB_X500).toString(),
                tokenNotaryX500Name = BOB_X500_NAME.toString(),
                tokenSymbol = "GBP",
                tokenAmount = BigDecimal(100),
                tokenOwnerHash = getSecureHash(BOB_X500).toString(),
                tokenTag = "tag1",
                isConsumed = false,
                created = Instant.now(),
                lastModified = Instant.now()
            )

            val em = emf.createEntityManager()

            val currentTransaction = em.transaction
            currentTransaction.begin()
            em.persist(ledgerTx)
            em.persist(component)
            em.persist(output)
            currentTransaction.commit()


            var repo = UtxoTokenRepositoryImpl(DbRecordConverterImpl())

            val result = repo.getDistinctTokenPools(em)

            assertThat(result.size).isEqualTo(1)


        } catch (e: Exception) {
            println("error")
        }
    }

    private fun getSecureHash(name:String):SecureHash{
       return SecureHash(DigestAlgorithmName.SHA2_256.name, name.toByteArray())
    }
}