package net.corda.ledger.persistence.utxo.tests

import net.corda.common.json.validation.JsonValidator
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.testkit.DbUtils
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.TransactionStatus.*
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.testkit.transactionMetadataExample
import net.corda.ledger.persistence.consensual.tests.datamodel.field
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.ledger.persistence.utxo.impl.UtxoPersistenceServiceImpl
import net.corda.ledger.persistence.utxo.impl.UtxoRepositoryImpl
import net.corda.ledger.persistence.utxo.tests.datamodel.UtxoEntityFactory
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.orm.utils.transaction
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.test.util.time.TestClock
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.utilities.time.Clock
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.PrivacySalt
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.persistence.EntityManagerFactory
import kotlin.random.Random

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class UtxoPersistenceServiceImplTest {
    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var persistenceService: UtxoPersistenceService
    private lateinit var jsonMarshallingService: JsonMarshallingService
    private lateinit var jsonValidator: JsonValidator
    private lateinit var wireTransactionFactory: WireTransactionFactory
    private lateinit var digestService: DigestService
    private lateinit var serializationService: SerializationService
    private lateinit var entityManagerFactory: EntityManagerFactory
    private val emConfig = DbUtils.getEntityManagerConfiguration("ledger_db_for_test")

    companion object {
        // Truncating to millis as on Windows builds the micros are lost after fetching the data from Postgres
        private val TEST_CLOCK: Clock = TestClock(Instant.now().truncatedTo(ChronoUnit.MILLIS))
        private const val TESTING_DATAMODEL_CPB = "/META-INF/testing-datamodel.cpb"
        private const val TIMEOUT_MILLIS = 10000L
        private val seedSequence = AtomicInteger((0..Int.MAX_VALUE / 2).random())
    }

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            val virtualNode = setup.fetchService<VirtualNodeService>(TIMEOUT_MILLIS)
            val virtualNodeInfo = virtualNode.load(TESTING_DATAMODEL_CPB)
            val ctx = virtualNode.entitySandboxService.get(virtualNodeInfo.holdingIdentity)
            wireTransactionFactory = ctx.getSandboxSingletonService()
            jsonMarshallingService = ctx.getSandboxSingletonService()
            jsonValidator = ctx.getSandboxSingletonService()
            digestService = ctx.getSandboxSingletonService()
            serializationService = ctx.getSerializationService()
            entityManagerFactory = ctx.getEntityManagerFactory()
            val repository = UtxoRepositoryImpl(digestService, serializationService, wireTransactionFactory)
            persistenceService = UtxoPersistenceServiceImpl(
                entityManagerFactory.createEntityManager(),
                repository,
                digestService,
                TEST_CLOCK
            )
        }
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        emConfig.close()
    }

    @Test
    fun `find signed transaction that matches input status`() {
        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transaction = persistTransactionViaEntity(entityFactory)

        val dbSignedTransaction = persistenceService.findTransaction(transaction.id.toString(), UNVERIFIED)

        assertThat(dbSignedTransaction).isEqualTo(transaction)
    }

    @Test
    fun `find signed transaction with different status returns null`() {
        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transaction = persistTransactionViaEntity(entityFactory)

        val dbSignedTransaction = persistenceService.findTransaction(transaction.id.toString(), VERIFIED)

        assertThat(dbSignedTransaction).isNull()
    }

    @Test
    fun `update transaction status`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")
        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transaction = persistTransactionViaEntity(entityFactory)

        assertTransactionStatus(transaction.id.toString(), UNVERIFIED, entityFactory)

        persistenceService.updateStatus(transaction.id.toString(), VERIFIED)

        assertTransactionStatus(transaction.id.toString(), VERIFIED, entityFactory)
    }

    @Test
    fun `update transaction status does not affect other transactions`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")
        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transaction1 = persistTransactionViaEntity(entityFactory)
        val transaction2 = persistTransactionViaEntity(entityFactory)

        assertTransactionStatus(transaction1.id.toString(), UNVERIFIED, entityFactory)
        assertTransactionStatus(transaction2.id.toString(), UNVERIFIED, entityFactory)

        persistenceService.updateStatus(transaction1.id.toString(), VERIFIED)

        assertTransactionStatus(transaction1.id.toString(), VERIFIED, entityFactory)
        assertTransactionStatus(transaction2.id.toString(), UNVERIFIED, entityFactory)
    }

    @Test
    fun `persist signed transaction`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")
        val account = "Account"
        val transactionStatus = VERIFIED
        val signedTransaction = createSignedTransaction(Instant.now())

        // Persist transaction
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            account,
            transactionStatus
        )
        persistenceService.persistTransaction(transactionReader)

        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        // Verify persisted data
        entityManagerFactory.transaction { em ->
            val dbTransaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())

            assertThat(dbTransaction).isNotNull
            val txPrivacySalt = dbTransaction.field<ByteArray>("privacySalt")
            val txAccountId = dbTransaction.field<String>("accountId")
            val txCreatedTs = dbTransaction.field<Instant?>("created")

            assertThat(txPrivacySalt).isEqualTo(signedTransaction.wireTransaction.privacySalt.bytes)
            assertThat(txAccountId).isEqualTo(account)
            assertThat(txCreatedTs).isNotNull

            val componentGroupLists = signedTransaction.wireTransaction.componentGroupLists
            val txComponents = dbTransaction.field<Collection<Any>?>("components")
            assertThat(txComponents).isNotNull
                .hasSameSizeAs(componentGroupLists.filter { it.isNotEmpty() })
            txComponents!!
                .sortedWith(compareBy<Any> { it.field<Int>("groupIndex") }.thenBy { it.field<Int>("leafIndex") })
                .groupBy { it.field<Int>("groupIndex") }.values
                .zip(componentGroupLists)
                .forEachIndexed { groupIndex, (dbComponentGroup, componentGroup) ->
                    assertThat(dbComponentGroup).hasSameSizeAs(componentGroup)
                    dbComponentGroup.zip(componentGroup)
                        .forEachIndexed { leafIndex, (dbComponent, component) ->
                            assertThat(dbComponent.field<Int>("groupIndex")).isEqualTo(groupIndex)
                            assertThat(dbComponent.field<Int>("leafIndex")).isEqualTo(leafIndex)
                            assertThat(dbComponent.field<ByteArray>("data")).isEqualTo(component)
                            assertThat(dbComponent.field<String>("hash")).isEqualTo(
                                digest("SHA-256", component).toString()
                            )
                            assertThat(dbComponent.field<Instant>("created")).isEqualTo(txCreatedTs)
                        }
                }

            val signatures = signedTransaction.signatures
            val txSignatures = dbTransaction.field<Collection<Any>?>("signatures")
            assertThat(txSignatures)
                .isNotNull
                .hasSameSizeAs(signatures)
            txSignatures!!
                .sortedBy { it.field<Int>("index") }
                .zip(signatures)
                .forEachIndexed { index, (dbSignature, signature) ->
                    assertThat(dbSignature.field<Int>("index")).isEqualTo(index)
                    assertThat(dbSignature.field<ByteArray>("signature")).isEqualTo(
                        serializationService.serialize(
                            signature
                        ).bytes
                    )
                    assertThat(dbSignature.field<String>("publicKeyHash")).isEqualTo(
                        digest("SHA-256", signature.by.encoded).toString()
                    )
                    assertThat(dbSignature.field<Instant>("created")).isEqualTo(txCreatedTs)
                }

            val txStatuses = dbTransaction.field<Collection<Any>?>("statuses")
            assertThat(txStatuses)
                .isNotNull
                .hasSize(1)
            val dbStatus = txStatuses!!.first()
            assertThat(dbStatus.field<String>("status")).isEqualTo(transactionStatus.value)
            assertThat(dbStatus.field<Instant>("updated")).isEqualTo(txCreatedTs)
        }
    }

    private fun persistTransactionViaEntity(entityFactory: UtxoEntityFactory): SignedTransactionContainer {
        val account = "Account"
        val createdTs = TEST_CLOCK.instant()
        val signedTransaction = createSignedTransaction(createdTs)
        entityManagerFactory.transaction { em ->
            entityFactory.createUtxoTransactionEntity(
                signedTransaction.id.toString(),
                signedTransaction.wireTransaction.privacySalt.bytes,
                account,
                createdTs
            ).also { transaction ->
                transaction.field<MutableCollection<Any>>("components").addAll(
                    signedTransaction.wireTransaction.componentGroupLists.flatMapIndexed { groupIndex, componentGroup ->
                        componentGroup.mapIndexed { leafIndex: Int, component ->
                            entityFactory.createUtxoTransactionComponentEntity(
                                transaction,
                                groupIndex,
                                leafIndex,
                                component,
                                digest("SHA-256", component).toString(),
                                createdTs
                            )
                        }
                    }
                )
                transaction.field<MutableCollection<Any>>("signatures").addAll(
                    signedTransaction.signatures.mapIndexed { index, signature ->
                        entityFactory.createUtxoTransactionSignatureEntity(
                            transaction,
                            index,
                            serializationService.serialize(signature).bytes,
                            digest("SHA-256", signature.by.encoded).toString(),
                            createdTs
                        )
                    }
                )
                transaction.field<MutableCollection<Any>>("statuses").addAll(
                    listOf(
                        entityFactory.createUtxoTransactionStatusEntity(transaction, UNVERIFIED.value, createdTs)
                    )
                )
                em.persist(transaction)
            }
        }
        return signedTransaction
    }

    private fun assertTransactionStatus(transactionId: String, status: TransactionStatus, entityFactory: UtxoEntityFactory) {
        entityManagerFactory.transaction { em ->
            val dbTransaction = em.find(entityFactory.utxoTransaction, transactionId)
            val statuses = dbTransaction.field<Collection<Any>?>("statuses")
            assertThat(statuses)
                .isNotNull
                .hasSize(1)
            assertThat(statuses?.single()?.field<String>("status")).isEqualTo(status.value)
        }
    }

    private fun createSignedTransaction(
        createdTs: Instant,
        seed: String = seedSequence.incrementAndGet().toString()
    ): SignedTransactionContainer {
        val cpks = listOf(
            CordaPackageSummaryImpl(
                "$seed-cpk1",
                "signerSummaryHash1",
                "1.0",
                "$seed-fileChecksum1"
            ),
            CordaPackageSummaryImpl(
                "$seed-cpk2",
                "signerSummaryHash2",
                "2.0",
                "$seed-fileChecksum2"
            ),
            CordaPackageSummaryImpl(
                "$seed-cpk3",
                "signerSummaryHash3",
                "3.0",
                "$seed-fileChecksum3"
            )
        )
        val transactionMetadata = transactionMetadataExample(
            cpkMetadata = cpks,
            numberOfComponentGroups = UtxoComponentGroup.values().size
        )
        val componentGroupLists: List<List<ByteArray>> = listOf(
            listOf(jsonValidator.canonicalize(jsonMarshallingService.format(transactionMetadata)).toByteArray()),
            listOf("group2_component1".toByteArray()),
            listOf("group3_component1".toByteArray())
        )
        val privacySalt = PrivacySaltImpl(Random.nextBytes(32))
        val wireTransaction = wireTransactionFactory.create(
            componentGroupLists,
            privacySalt
        )
        val publicKey = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair().public
        val signatures = listOf(
            DigitalSignatureAndMetadata(
                DigitalSignature.WithKey(
                    publicKey,
                    "signature".toByteArray(),
                    mapOf("contextKey1" to "contextValue1")
                ),
                DigitalSignatureMetadata(
                    createdTs,
                    mapOf("propertyKey1" to "propertyValue1")
                )
            )
        )
        return SignedTransactionContainer(wireTransaction, signatures)
    }

    private class TestUtxoTransactionReader(
        val transactionContainer: SignedTransactionContainer,
        override val account: String,
        override val status: TransactionStatus
    ): UtxoTransactionReader {
        override val id: SecureHash
            get() = transactionContainer.id
        override val privacySalt: PrivacySalt
            get() = transactionContainer.wireTransaction.privacySalt
        override val rawGroupLists: List<List<ByteArray>>
            get() = transactionContainer.wireTransaction.componentGroupLists
        override val signatures: List<DigitalSignatureAndMetadata>
            get() = transactionContainer.signatures
        override val cpkMetadata: List<CordaPackageSummary>
            get() = transactionContainer.wireTransaction.metadata.getCpkMetadata()

        override fun getProducedStates(): List<StateAndRef<ContractState>> {
            TODO("Not yet implemented")
        }

        override fun getConsumedStates(): List<StateAndRef<ContractState>> {
            TODO("Not yet implemented")
        }
    }

    private fun digest(algorithm: String, data: ByteArray) =
        SecureHash(algorithm, MessageDigest.getInstance(algorithm).digest(data))
}
