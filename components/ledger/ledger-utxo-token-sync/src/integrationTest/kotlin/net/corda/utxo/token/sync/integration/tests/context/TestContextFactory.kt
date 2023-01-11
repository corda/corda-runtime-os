package net.corda.utxo.token.sync.integration.tests.context

import com.typesafe.config.ConfigFactory
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.utxo.token.sync.integration.tests.entities.UtxoRelevantTransactionState
import net.corda.utxo.token.sync.integration.tests.entities.UtxoTransactionComponentEntity
import net.corda.utxo.token.sync.integration.tests.entities.UtxoTransactionEntity
import net.corda.utxo.token.sync.integration.tests.entities.UtxoTransactionOutputEntity
import net.corda.utxo.token.sync.integration.tests.fakes.TestDbConnectionManager
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.EntityManagerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestContextFactory {

    private val configFactory = SmartConfigFactory.create(
        ConfigFactory.parseString(
            """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=key
            ${SmartConfigFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
        )
    )

    private val dbConfig = configFactory.create(DbUtils.createConfig("valut_db"))
    private val dbConnectionManager = TestDbConnectionManager(EntityManagerFactoryFactoryImpl())
    private var entityManagerFactory = mutableMapOf<ShortHash, EntityManagerFactory>()
    private lateinit var virtualNodes: List<VirtualNodeInfo>

    fun addVirtualNodes(vnodes: List<VirtualNodeInfo>) {
        virtualNodes = vnodes
    }

    fun createDb() {
        dbConnectionManager.initialise(dbConfig)

        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf("net/corda/db/schema/vnode-vault/migration/ledger-utxo-creation-v1.0.xml"),
                    DbSchema::class.java.classLoader
                )
            )
        )

        virtualNodes.forEach { createDb(it, dbChange) }
    }

    fun addTransaction(vNode: VirtualNodeInfo, txId: String) {
        val ledgerTx = UtxoTransactionEntity(txId, byteArrayOf(), "account", Instant.now())
        getEntityManagerFactory(vNode.holdingIdentity).createEntityManager().transaction {
            it.persist(ledgerTx)
        }
    }

    fun addTokenAsOutputRecord(
        vNode: VirtualNodeInfo,
        txId: String,
        leafIdx: Int,
        tokenType: String,
        tokenIssuerHash: String,
        tokenNotaryX500Name: String,
        tokenSymbol: String,
        lastModified: Instant
    ) {

        val component = UtxoTransactionComponentEntity(
            transactionId = txId,
            groupIdx = UtxoComponentGroup.OUTPUTS.ordinal,
            leafIdx = leafIdx,
            data = byteArrayOf(),
            hash = "",
            created = lastModified,
        )

        val output = UtxoTransactionOutputEntity(
            transactionId = txId,
            groupIdx = UtxoComponentGroup.OUTPUTS.ordinal,
            leafIdx = leafIdx,
            type = UtxoTransactionEntity::class.java.name,
            tokenType = tokenType,
            tokenIssuerHash = tokenIssuerHash,
            tokenNotaryX500Name = tokenNotaryX500Name,
            tokenSymbol = tokenSymbol,
            tokenAmount = BigDecimal(100),
            tokenOwnerHash = "o1",
            tokenTag = "t1",
            created = lastModified,
        )

        val relevancy = UtxoRelevantTransactionState(
            transactionId = txId,
            groupIdx = UtxoComponentGroup.OUTPUTS.ordinal,
            leafIdx = leafIdx,
            consumed = false,
            created = lastModified,
        )

        getEntityManagerFactory(vNode.holdingIdentity).transaction {
            it.persist(component)
            it.persist(output)
            it.persist(relevancy)
        }
    }

    fun createTestContext(overrideConfig: Map<String, Any> = mapOf()): TestContext {
        return TestContext(virtualNodes, dbConnectionManager, overrideConfig)
    }

    private fun getSecureHash(name: String): SecureHash {
        return SecureHash(DigestAlgorithmName.SHA2_256.name, name.toByteArray())
    }

    private fun createDb(vNode: VirtualNodeInfo, classLoaderChangeLog: ClassloaderChangeLog) {
        // Create a vault schema for the vnode
        val dataSource = dbConnectionManager.getOrCreateDataSource(
            vNode.vaultDmlConnectionId,
            vNode.holdingIdentity.shortHash.toString()
        )

        dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, classLoaderChangeLog)
        }

        entityManagerFactory[vNode.holdingIdentity.shortHash] = dbConnectionManager.createEntityManagerFactory(
            vNode.vaultDmlConnectionId,
            JpaEntitiesSet.create(
                vNode.vaultDmlConnectionId.toString(),
                setOf(
                    UtxoTransactionEntity::class.java,
                    UtxoTransactionComponentEntity::class.java,
                    UtxoTransactionOutputEntity::class.java,
                    UtxoRelevantTransactionState::class.java
                )
            )
        )
    }

    private fun getEntityManagerFactory(holdingId: HoldingIdentity): EntityManagerFactory {
        return checkNotNull(entityManagerFactory[holdingId.shortHash]) { "No manager found for ${holdingId}" }
    }
}
