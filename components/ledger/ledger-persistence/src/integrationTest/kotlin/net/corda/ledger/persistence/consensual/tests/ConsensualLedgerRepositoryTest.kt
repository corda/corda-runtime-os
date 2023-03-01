package net.corda.ledger.persistence.consensual.tests

import net.corda.common.json.validation.JsonValidator
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.testkit.DbUtils
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.testkit.getPrivacySalt
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.common.testkit.transactionMetadataExample
import net.corda.ledger.consensual.data.transaction.ConsensualComponentGroup
import net.corda.ledger.persistence.consensual.ConsensualPersistenceService
import net.corda.ledger.persistence.consensual.ConsensualRepository
import net.corda.ledger.persistence.consensual.ConsensualTransactionReader
import net.corda.ledger.persistence.consensual.impl.ConsensualPersistenceServiceImpl
import net.corda.ledger.persistence.consensual.tests.datamodel.ConsensualEntityFactory
import net.corda.ledger.persistence.consensual.tests.datamodel.field
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
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.PrivacySalt
import org.assertj.core.api.Assertions.assertThat
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
import java.util.concurrent.atomic.AtomicInteger
import javax.persistence.EntityManagerFactory

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class ConsensualLedgerRepositoryTest {
    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var wireTransactionFactory: WireTransactionFactory
    private lateinit var digestService: DigestService
    private lateinit var jsonMarshallingService: JsonMarshallingService
    private lateinit var jsonValidator: JsonValidator
    private lateinit var serializationService: SerializationService
    private lateinit var entityManagerFactory: EntityManagerFactory
    private lateinit var repository: ConsensualRepository
    private lateinit var persistenceService: ConsensualPersistenceService

    companion object {
        private const val TESTING_DATAMODEL_CPB = "/META-INF/testing-datamodel.cpb"
        private const val TIMEOUT_MILLIS = 10000L

        // Truncating to millis as on Windows builds the micros are lost after fetching the data from Postgres
        private val TEST_CLOCK: Clock = TestClock(Instant.now().truncatedTo(ChronoUnit.MILLIS))
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
            digestService = ctx.getSandboxSingletonService()
            jsonMarshallingService = ctx.getSandboxSingletonService()
            jsonValidator = ctx.getSandboxSingletonService()
            serializationService = ctx.getSerializationService()
            entityManagerFactory = ctx.getEntityManagerFactory()
            repository = ctx.getSandboxSingletonService()
            persistenceService = ConsensualPersistenceServiceImpl(
                entityManagerFactory.createEntityManager(),
                repository,
                digestService,
                TEST_CLOCK
            )
        }
    }

    @Test
    fun `can read signed transaction`() {
        val account = "Account"
        // truncating to millis as on windows builds the micros are lost after fetching the data from Postgres
        val createdTs = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val signedTransaction = createSignedTransaction(createdTs)
        val cpks = signedTransaction.wireTransaction.metadata.getCpkMetadata()
        val existingCpks = cpks.take(2)
        val entityFactory = ConsensualEntityFactory(entityManagerFactory)
        entityManagerFactory.transaction { em ->
            val dbExistingCpks = existingCpks.mapIndexed { i, cpk ->
                entityFactory.createConsensualCpkEntity(
                    cpk.fileChecksum,
                    cpk.name,
                    cpk.signerSummaryHash!!,
                    cpk.version,
                    "file$i".toByteArray(),
                    createdTs
                )
            }.onEach(em::persist)

            entityFactory.createConsensualTransactionEntity(
                signedTransaction.id.toString(),
                signedTransaction.wireTransaction.privacySalt.bytes,
                account,
                createdTs
            ).also { transaction ->
                transaction.field<MutableCollection<Any>>("components").addAll(
                    signedTransaction.wireTransaction.componentGroupLists.flatMapIndexed { groupIndex, componentGroup ->
                        componentGroup.mapIndexed { leafIndex: Int, component ->
                            entityFactory.createConsensualTransactionComponentEntity(
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
                transaction.field<MutableCollection<Any>>("statuses").addAll(
                    listOf(
                        entityFactory.createConsensualTransactionStatusEntity(transaction, "V", createdTs)
                    )
                )
                transaction.field<MutableCollection<Any>>("signatures").addAll(
                    signedTransaction.signatures.mapIndexed { index, signature ->
                        entityFactory.createConsensualTransactionSignatureEntity(
                            transaction,
                            index,
                            serializationService.serialize(signature).bytes,
                            digest("SHA-256", signature.by.encoded).toString(),
                            createdTs
                        )
                    }
                )
                transaction.field<MutableCollection<Any>>("cpks").addAll(dbExistingCpks)
                em.persist(transaction)
            }
        }

        val dbSignedTransaction = entityManagerFactory.transaction { em ->
            repository.findTransaction(em, signedTransaction.id.toString())
        }

        assertThat(dbSignedTransaction).isEqualTo(signedTransaction)
    }

    @Test
    fun `can persist signed transaction`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")
        val account = "Account"
        val transactionStatus = TransactionStatus.VERIFIED
        val signedTransaction = createSignedTransaction(Instant.now())

        // Persist transaction
        val transaction = TestConsensualTransactionReader(signedTransaction, account, transactionStatus)
        persistenceService.persistTransaction(transaction)

        val entityFactory = ConsensualEntityFactory(entityManagerFactory)

        // Verify persisted data
        entityManagerFactory.transaction { em ->
            val dbTransaction = em.find(entityFactory.consensualTransaction, signedTransaction.id.toString())

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

            val txStatuses = dbTransaction.field<Collection<Any>?>("statuses")
            assertThat(txStatuses)
                .isNotNull
                .hasSize(1)
            val dbStatus = txStatuses!!.first()
            assertThat(dbStatus.field<String>("status")).isEqualTo(transactionStatus.value)
            assertThat(dbStatus.field<Instant>("updated")).isEqualTo(txCreatedTs)

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
        }
    }

    @Test
    fun `can persist links between signed transaction and existing CPKs`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")
        val account = "Account"
        // truncating to millis as on windows builds the micros are lost after fetching the data from Postgres
        val createdTs = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val signedTransaction = createSignedTransaction(createdTs)
        val cpks = signedTransaction.wireTransaction.metadata.getCpkMetadata()
        val existingCpks = cpks.take(2)
        val entityFactory = ConsensualEntityFactory(entityManagerFactory)
        entityManagerFactory.transaction { em ->
            existingCpks.mapIndexed { i, cpk ->
                entityFactory.createConsensualCpkEntity(
                    cpk.fileChecksum,
                    cpk.name,
                    cpk.signerSummaryHash!!,
                    cpk.version,
                    "file$i".toByteArray(),
                    createdTs
                )
            }.forEach(em::persist)

            entityFactory.createConsensualTransactionEntity(
                signedTransaction.id.toString(),
                signedTransaction.wireTransaction.privacySalt.bytes,
                account,
                createdTs
            ).apply(em::persist)
        }

        // Persist transaction CPKs
        val persistedCpkCount = entityManagerFactory.createEntityManager().transaction { em ->
            repository.persistTransactionCpk(
                em,
                signedTransaction.id.toString(),
                signedTransaction.wireTransaction.metadata.getCpkMetadata()
            )
        }

        // Verify persisted data
        assertThat(persistedCpkCount).isEqualTo(existingCpks.size)
        entityManagerFactory.transaction { em ->
            val dbTransaction = em.find(entityFactory.consensualTransaction, signedTransaction.id.toString())

            val txCpks = dbTransaction.field<Collection<Any>?>("cpks")
            assertThat(txCpks).isNotNull
                .hasSameSizeAs(existingCpks)
            txCpks!!
                .sortedBy { it.field<String>("name") }
                .zip(existingCpks)
                .forEachIndexed { index, (dbCpk, cpk) ->
                    assertThat(dbCpk.field<String>("fileChecksum")).isEqualTo(cpk.fileChecksum)
                    assertThat(dbCpk.field<String>("name")).isEqualTo(cpk.name)
                    assertThat(dbCpk.field<String>("signerSummaryHash")).isEqualTo(cpk.signerSummaryHash)
                    assertThat(dbCpk.field<String>("version")).isEqualTo(cpk.version)
                    assertThat(dbCpk.field<ByteArray>("data")).isEqualTo("file$index".toByteArray())
                    assertThat(dbCpk.field<Instant>("created")).isEqualTo(createdTs)
                }
        }
    }

    @Test
    fun `can find file checksums of CPKs linked to transaction`() {
        val account = "Account"
        val signedTransaction = createSignedTransaction(Instant.now())
        val cpks = signedTransaction.wireTransaction.metadata.getCpkMetadata()
        val existingCpks = cpks.take(2)
        val entityFactory = ConsensualEntityFactory(entityManagerFactory)
        entityManagerFactory.transaction { em ->
            val dbExistingCpks = existingCpks.mapIndexed { i, cpk ->
                entityFactory.createConsensualCpkEntity(
                    cpk.fileChecksum,
                    cpk.name,
                    cpk.signerSummaryHash!!,
                    cpk.version,
                    "file$i".toByteArray(),
                    Instant.now()
                )
            }.onEach(em::persist)

            entityFactory.createConsensualTransactionEntity(
                signedTransaction.id.toString(),
                signedTransaction.wireTransaction.privacySalt.bytes,
                account,
                Instant.now()
            ).also { transaction ->
                transaction.field<MutableCollection<Any>>("cpks").addAll(dbExistingCpks)
                em.persist(transaction)
            }
        }

        val cpkChecksums = entityManagerFactory.transaction { em ->
            repository.findTransactionCpkChecksums(
                em,
                signedTransaction.wireTransaction.metadata.cpkMetadata
            )
        }

        assertThat(cpkChecksums).isEqualTo(existingCpks.mapTo(LinkedHashSet(), CordaPackageSummary::getFileChecksum))
    }

    private fun createSignedTransaction(
        createdTs: Instant,
        seed: String = seedSequence.incrementAndGet().toString()
    ): SignedTransactionContainer {
        val transactionMetadata = transactionMetadataExample(
            cpkPackageSeed = seed,
            numberOfComponentGroups = ConsensualComponentGroup.values().size
        )
        val componentGroupLists: List<List<ByteArray>> = listOf(
            listOf(jsonValidator.canonicalize(jsonMarshallingService.format(transactionMetadata)).toByteArray()),
            listOf("group2_component1".toByteArray()),
            listOf("group3_component1".toByteArray())
        )
        val wireTransaction = wireTransactionFactory.create(
            componentGroupLists,
            getPrivacySalt()
        )
        val publicKey = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair().public
        val signatures = listOf(getSignatureWithMetadataExample(publicKey, createdTs))
        return SignedTransactionContainer(wireTransaction, signatures)
    }

    private class TestConsensualTransactionReader(
        val transactionContainer: SignedTransactionContainer,
        override val account: String,
        override val status: TransactionStatus
    ) : ConsensualTransactionReader {
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
    }

    private fun digest(algorithm: String, data: ByteArray) =
        SecureHash(algorithm, MessageDigest.getInstance(algorithm).digest(data))
}
