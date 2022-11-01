package net.corda.ledger.persistence.processor.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.SerializationServiceImpl
import net.corda.internal.serialization.amqp.DefaultDescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.consensual.data.transaction.ConsensualSignedTransactionContainer
import net.corda.ledger.persistence.processor.tests.datamodel.ConsensualCpkEntity
import net.corda.ledger.persistence.processor.tests.datamodel.ConsensualLedgerEntities
import net.corda.ledger.persistence.processor.tests.datamodel.ConsensualTransactionComponentEntity
import net.corda.ledger.persistence.processor.tests.datamodel.ConsensualTransactionEntity
import net.corda.ledger.persistence.processor.tests.datamodel.ConsensualTransactionSignatureEntity
import net.corda.ledger.persistence.processor.tests.datamodel.ConsensualTransactionStatusEntity
import net.corda.ledger.persistence.consensual.ConsensualLedgerRepository
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.SerializationContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.toHexString
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.DigitalSignature
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.persistence.EntityManagerFactory
import kotlin.random.Random

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsensualLedgerRepositoryTest {
    @RegisterExtension
    private val lifecycle = EachTestLifecycle()
    @InjectService
    lateinit var digestService: DigestService
    @InjectService
    lateinit var merkleTreeProvider: MerkleTreeProvider
    @InjectService
    lateinit var jsonMarshallingService: JsonMarshallingService

    private lateinit var serializationService: SerializationService
    private lateinit var emptySandboxGroup: SandboxGroup
    private lateinit var repository: ConsensualLedgerRepository
    private val emConfig = DbUtils.getEntityManagerConfiguration("ledger_db_for_test")
    private val entityManagerFactory: EntityManagerFactory

    companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/vnode-vault/db.changelog-master.xml"
        private val seedSequence = AtomicInteger((0..Int.MAX_VALUE/2).random())
    }

    init {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf(MIGRATION_FILE_LOCATION),
                    DbSchema::class.java.classLoader
                )
            )
        )
        emConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
        entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            ConsensualLedgerEntities.classes.toList(),
            emConfig
        )
    }

    @BeforeAll
    fun setup(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            val sandboxCreationService = setup.fetchService<SandboxCreationService>(timeout = 1500)
            val publicKeySerializer = setup.fetchService<InternalCustomSerializer<PublicKey>>(
                "(component.name=net.corda.crypto.impl.serialization.PublicKeySerializer)",
                1500
            )
            emptySandboxGroup = sandboxCreationService.createSandboxGroup(emptyList())
            serializationService = getTestSerializationService(emptySandboxGroup) {
                it.register(publicKeySerializer, it)
            }
            repository = ConsensualLedgerRepository(merkleTreeProvider, digestService, jsonMarshallingService, serializationService)
            setup.withCleanup {
                sandboxCreationService.unloadSandboxGroup(emptySandboxGroup)
            }
        }
    }

    private fun getTestSerializationService(
        sandboxGroup: SandboxGroup,
        registerMoreSerializers: (it: SerializerFactory) -> Unit,
    ) : SerializationService {
        val serializationContext = SerializationContextImpl(
            preferredSerializationVersion = amqpMagic,
            properties = mutableMapOf(),
            objectReferencesEnabled = false,
            useCase = SerializationContext.UseCase.Testing,
            encoding = null,
            sandboxGroup = sandboxGroup
        )
        val factory = SerializerFactoryBuilder.build(
            sandboxGroup,
            descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry(),
            allowEvolution = false
        ).also {
            registerCustomSerializers(it)
            registerMoreSerializers(it)
        }
        return SerializationServiceImpl(
            SerializationOutput(factory),
            DeserializationInput(factory),
            serializationContext)
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        emConfig.close()
        entityManagerFactory.close()
    }

    @Test
    fun `can read signed transaction`() {
        val account = "Account"
        val signedTransaction = createSignedTransaction()
        val cpks = signedTransaction.wireTransaction.metadata.getCpkMetadata()
        val existingCpks = cpks.take(2)
        entityManagerFactory.transaction { em ->
            val createdTs = Instant.now()
            val dbExistingCpks = existingCpks.mapIndexed { i, cpk ->
                ConsensualCpkEntity(cpk.fileChecksum, cpk.name, cpk.signerSummaryHash!!, cpk.version, "file$i".toByteArray(), createdTs)
            }.onEach(em::persist)

            ConsensualTransactionEntity(
                signedTransaction.id.toHexString(),
                signedTransaction.wireTransaction.privacySalt.bytes,
                account,
                Instant.now()
            ).apply {
                components.addAll(
                    signedTransaction.wireTransaction.componentGroupLists.flatMapIndexed { groupIndex, componentGroup ->
                        componentGroup.mapIndexed { leafIndex: Int, component ->
                            ConsensualTransactionComponentEntity(
                                this,
                                groupIndex,
                                leafIndex,
                                component,
                                MessageDigest.getInstance("SHA-256").digest(component).toHexString(),
                                createdTs
                            )
                        }
                    }
                )
                statuses.addAll(listOf(
                    ConsensualTransactionStatusEntity(this, "V", Instant.now())
                ))
                signatures.addAll(
                    signedTransaction.signatures.mapIndexed { index, signature ->
                        ConsensualTransactionSignatureEntity(
                            this,
                            index,
                            serializationService.serialize(signature).bytes,
                            MessageDigest.getInstance("SHA-256").digest(signature.by.encoded).toHexString(),
                            createdTs
                        )
                    }
                )
                this.cpks.addAll(dbExistingCpks)
                em.persist(this)
            }
        }

        val dbSignedTransaction = entityManagerFactory.createEntityManager().transaction { em ->
            repository.findTransaction(em, signedTransaction.id.toHexString())
        }

        assertThat(dbSignedTransaction).isEqualTo(signedTransaction)
    }

    @Test
    fun `can persist signed transaction`() {
        val account = "Account"
        val transactionStatus = "V"
        val signedTransaction = createSignedTransaction()

        // Persist transaction
        entityManagerFactory.createEntityManager().transaction { em ->
            repository.persistTransaction(em, signedTransaction, transactionStatus, account)
        }

        // Verify persisted data
        entityManagerFactory.transaction { em ->
            val dbTransaction = em.find(ConsensualTransactionEntity::class.java, signedTransaction.id.toHexString())

            assertThat(dbTransaction).isNotNull
            assertThat(dbTransaction.privacySalt).isEqualTo(signedTransaction.wireTransaction.privacySalt.bytes)
            assertThat(dbTransaction.accountId).isEqualTo(account)
            assertThat(dbTransaction.created).isNotNull
            val createdTs = dbTransaction.created

            val componentGroupLists = signedTransaction.wireTransaction.componentGroupLists
            assertThat(dbTransaction.components).isNotNull
            assertThat(dbTransaction.components.size).isEqualTo(componentGroupLists.size)
            dbTransaction.components
                .sortedWith(compareBy<ConsensualTransactionComponentEntity> { it.groupIndex }.thenBy { it.leafIndex })
                .groupBy { it.groupIndex }.values
                .zip(componentGroupLists)
                .forEachIndexed { groupIndex, (dbComponentGroup, componentGroup) ->
                    assertThat(dbComponentGroup.size).isEqualTo(componentGroup.size)
                    dbComponentGroup.zip(componentGroup)
                        .forEachIndexed { leafIndex, (dbComponent, component) ->
                            assertThat(dbComponent.groupIndex).isEqualTo(groupIndex)
                            assertThat(dbComponent.leafIndex).isEqualTo(leafIndex)
                            assertThat(dbComponent.data).isEqualTo(component)
                            assertThat(dbComponent.hash).isEqualTo(
                                MessageDigest.getInstance("SHA-256").digest(component).toHexString())
                            assertThat(dbComponent.created).isEqualTo(createdTs)
                        }
                }

            assertThat(dbTransaction.statuses).isNotNull
            assertThat(dbTransaction.statuses.size).isEqualTo(1)
            val dbStatus = dbTransaction.statuses.first()
            assertThat(dbStatus.status).isEqualTo(transactionStatus)
            assertThat(dbStatus.created).isEqualTo(createdTs)

            val signatures = signedTransaction.signatures
            assertThat(dbTransaction.signatures).isNotNull
            assertThat(dbTransaction.signatures.size).isEqualTo(signatures.size)
            dbTransaction.signatures
                .sortedBy { it.index }
                .zip(signatures)
                .forEachIndexed { index, (dbSignature, signature) ->
                    assertThat(dbSignature.index).isEqualTo(index)
                    assertThat(dbSignature.signature).isEqualTo(serializationService.serialize(signature).bytes)
                    assertThat(dbSignature.publicKeyHash).isEqualTo(
                        MessageDigest.getInstance("SHA-256").digest(signature.by.encoded).toHexString())
                    assertThat(dbSignature.created).isEqualTo(createdTs)
                }
        }
    }

    @Test
    fun `can persist links between signed transaction and existing CPKs`() {
        val account = "Account"
        val signedTransaction = createSignedTransaction()
        val cpks = signedTransaction.wireTransaction.metadata.getCpkMetadata()
        val existingCpks = cpks.take(2)
        entityManagerFactory.transaction { em ->
            existingCpks.mapIndexed { i, cpk ->
                ConsensualCpkEntity(cpk.fileChecksum, cpk.name, cpk.signerSummaryHash!!, cpk.version, "file$i".toByteArray(), Instant.now())
            }.forEach(em::persist)

            ConsensualTransactionEntity(
                signedTransaction.id.toHexString(),
                signedTransaction.wireTransaction.privacySalt.bytes,
                account,
                Instant.now()
            ).apply(em::persist)
        }

        // Persist transaction CPKs
        val persistedCpkCount = entityManagerFactory.createEntityManager().transaction { em ->
            repository.persistTransactionCpk(em, signedTransaction)
        }

        // Verify persisted data
        assertThat(persistedCpkCount).isEqualTo(existingCpks.size)
        entityManagerFactory.transaction { em ->
            val dbTransaction = em.find(ConsensualTransactionEntity::class.java, signedTransaction.id.toHexString())

            assertThat(dbTransaction.cpks).isNotNull
            assertThat(dbTransaction.cpks.size).isEqualTo(existingCpks.size)
            dbTransaction.cpks
                .sortedBy { it.name }
                .zip(existingCpks)
                .forEachIndexed { index, (dbCpk, cpk) ->
                    assertThat(dbCpk.fileChecksum).isEqualTo(cpk.fileChecksum)
                    assertThat(dbCpk.name).isEqualTo(cpk.name)
                    assertThat(dbCpk.signerSummaryHash).isEqualTo(cpk.signerSummaryHash)
                    assertThat(dbCpk.version).isEqualTo(cpk.version)
                    assertThat(dbCpk.data).isEqualTo("file$index".toByteArray())
                    assertThat(dbCpk.created).isNotNull
                }
        }
    }

    @Test
    fun `can find file checksums of CPKs linked to transaction`() {
        val account = "Account"
        val signedTransaction = createSignedTransaction()
        val cpks = signedTransaction.wireTransaction.metadata.getCpkMetadata()
        val existingCpks = cpks.take(2)
        entityManagerFactory.transaction { em ->
            val dbExistingCpks = existingCpks.mapIndexed { i, cpk ->
                ConsensualCpkEntity(cpk.fileChecksum, cpk.name, cpk.signerSummaryHash!!, cpk.version, "file$i".toByteArray(), Instant.now())
            }.onEach(em::persist)

            ConsensualTransactionEntity(
                signedTransaction.id.toHexString(),
                signedTransaction.wireTransaction.privacySalt.bytes,
                account,
                Instant.now()
            ).apply {
                this.cpks.addAll(dbExistingCpks)
                em.persist(this)
            }
        }

        val cpkChecksums = entityManagerFactory.createEntityManager().transaction { em ->
            repository.findTransactionCpkChecksums(em, signedTransaction)
        }

        assertThat(cpkChecksums).isEqualTo(existingCpks.map { it.fileChecksum }.toSet())
    }

    private fun createSignedTransaction(seed: String = seedSequence.incrementAndGet().toString()): ConsensualSignedTransactionContainer {
        val cpks = listOf(
            CordaPackageSummary("$seed-cpk1", "signerSummaryHash1", "1.0", "$seed-fileChecksum1"),
            CordaPackageSummary("$seed-cpk2", "signerSummaryHash2", "2.0", "$seed-fileChecksum2"),
            CordaPackageSummary("$seed-cpk3", "signerSummaryHash3", "3.0", "$seed-fileChecksum3"),
        )
        val transactionMetaData = TransactionMetaData(
            LinkedHashMap<String, Any>().apply {
                put(TransactionMetaData.DIGEST_SETTINGS_KEY, WireTransactionDigestSettings.defaultValues)
                put(TransactionMetaData.CPK_METADATA_KEY, cpks)
            }
        )
        val componentGroupLists: List<List<ByteArray>> = listOf(
            listOf(jsonMarshallingService.format(transactionMetaData).toByteArray(Charsets.UTF_8)),
            listOf("group2_component1".toByteArray()),
            listOf("group3_component1".toByteArray())
        )
        val privacySalt = PrivacySaltImpl(Random.nextBytes(32))
        val wireTransaction = WireTransaction(
            merkleTreeProvider,
            digestService,
            jsonMarshallingService,
            privacySalt,
            componentGroupLists
        )
        val publicKey = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair().public
        val signatures = listOf(
            DigitalSignatureAndMetadata(
                DigitalSignature.WithKey(
                    publicKey,
                    "signature".toByteArray(),
                    mapOf("contextKey1" to "contextValue1")),
                DigitalSignatureMetadata(
                    Instant.now(),
                    mapOf("propertyKey1" to "propertyValue1")
                )
            )
        )
        return ConsensualSignedTransactionContainer(wireTransaction, signatures)
    }
}