package net.corda.virtualnode.write.db.impl.tests.writer

import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePair
import net.corda.data.crypto.SecureHash
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.packaging.CpiIdentifier
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.data.virtualnode.VirtualNodeCreateResponse
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.VirtualNodeAlreadyExistsException
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.test.util.dsl.entities.cpx.cpkDbChangeLog
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.DbConnectionImpl
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDb
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactory
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriterProcessor
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility

/** Tests of [VirtualNodeWriterProcessor]. */
class VirtualNodeWriterProcessorTests {
    companion object {
        const val CPI_ID_SHORT_HASH = "dummy_cpi_id_short_hash"

        private const val dummyGroupPolicy = "{\"groupId\": \"efc27942-9d2a-4f72-ac39-320d38743173\"}"
        private const val dummyGroupPolicyWithMGMInfo = "{\"groupId\": \"da1623ea-e6d4-4314-84f8-6e3b84a869cd\"}"
        private const val mgmGroupPolicy = "{\"groupId\": \"$MGM_DEFAULT_GROUP_ID\"}"
    }

    private val groupId = "f3676687-ab69-4ca1-a17b-ab20b7bc6d03"
    private val x500Name = "OU=LLC, O=Bob, L=Dublin, C=IE"
    private val holdingIdentity = createTestHoldingIdentity(x500Name, groupId)
    private val mgmName = MemberX500Name.parse("CN=Corda Network MGM, OU=MGM, O=Corda Network, L=London, C=GB")
    private val mgmHoldingIdentity = HoldingIdentity(mgmName, groupId)

    private val secureHash = SecureHash(
        "SHA-256",
        ByteBuffer.wrap("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000".toByteArray())
    )
    private val cpiIdentifier = CpiIdentifier("dummy_name", "dummy_version", secureHash)
    private val summaryHash = net.corda.v5.crypto.SecureHash.parse("SHA-256:0000000000000000")
    private val cpiId = net.corda.libs.packaging.core.CpiIdentifier("dummy_name", "dummy_version", summaryHash)
    private val cpiMetaData =
        CpiMetadataLite(cpiId, CPI_ID_SHORT_HASH, groupId, dummyGroupPolicy, "", "", emptySet())
    private val cpiMetaDataWithMGM =
        CpiMetadataLite(cpiId, CPI_ID_SHORT_HASH, groupId, dummyGroupPolicyWithMGMInfo, "", "", emptySet())
    private val connectionId = UUID.randomUUID().toString()

    /** Use the test clock so we can control the Instant that is written into timestamps such that
     * the actual timestamp == expected timestamp in the tests.
     */
    private val clock = TestClock(Instant.now())

    private val vnodeInfo = VirtualNodeInfo(
            holdingIdentity.toAvro(),
            cpiIdentifier,
            connectionId,
            connectionId,
            connectionId,
            connectionId,
            connectionId,
            connectionId,
            null,
            OperationalStatus.ACTIVE.name,
            OperationalStatus.ACTIVE.name,
            OperationalStatus.ACTIVE.name,
            OperationalStatus.ACTIVE.name,
            null,
            -1,
            clock.instant()
        )

    private val vnodeCreationReq =
        VirtualNodeCreateRequest(
            vnodeInfo.holdingIdentity.x500Name,
            CPI_ID_SHORT_HASH,
            "dummy_vault_ddl_config",
            "dummy_vault_dml_config",
            "dummy_crypto_ddl_config",
            "dummy_crypto_dml_config",
            "dummy_uniqueness_ddl_config",
            "dummy_uniqueness_dml_config",
            "update_actor"
        )

    private val em = mock<EntityManager> {
        on { transaction }.doReturn(mock())
    }

    private val emf = mock<EntityManagerFactory> {
        on { createEntityManager() }.doReturn(em)
    }

    private val dataSource = mock<CloseableDataSource> {
        on { connection }.doReturn(mock())
    }

    private val connectionManager = mock<DbConnectionManager> {
        on { getDataSource(any()) }.doReturn(dataSource)
        on { getClusterEntityManagerFactory() }.doReturn(emf)
        on { putConnection(any(), any(), any(), any(), any(), any()) }.doReturn(UUID.fromString(connectionId))
    }

    private val dbConnection = mock<DbConnectionImpl> {
        on { name }.doReturn("connection")
        on { privilege }.doReturn(DbPrivilege.DDL)
        on { config }.doReturn(mock())
        on { description }.doReturn("description")
        on { getUser() }.doReturn("user")
        on { getPassword() }.doReturn("password")
    }

    private val dbConnectionMap = mapOf(
        DbPrivilege.DDL to dbConnection,
        DbPrivilege.DML to dbConnection,
    )

    private val vaultDb = mock<VirtualNodeDb>().apply {
        whenever(isPlatformManagedDb).thenReturn(true)
        whenever(dbConnections).thenReturn(dbConnectionMap)
        whenever(dbType).thenReturn(VirtualNodeDbType.VAULT)
    }

    private val cryptoDb = mock<VirtualNodeDb>().apply {
        whenever(isPlatformManagedDb).thenReturn(true)
        whenever(dbConnections).thenReturn(dbConnectionMap)
        whenever(dbType).thenReturn(VirtualNodeDbType.CRYPTO)
    }

    private val uniquenessDb = mock<VirtualNodeDb>().apply {
        whenever(isPlatformManagedDb).thenReturn(true)
        whenever(dbConnections).thenReturn(dbConnectionMap)
        whenever(dbType).thenReturn(VirtualNodeDbType.UNIQUENESS)
    }

    private val vNodeFactory = mock<VirtualNodeDbFactory> {
        on { createVNodeDbs(any(), any()) }.doReturn(
            mapOf(
                VirtualNodeDbType.VAULT to vaultDb,
                VirtualNodeDbType.CRYPTO to cryptoDb,
                VirtualNodeDbType.UNIQUENESS to uniquenessDb
            )
        )
    }

    private val vNodeRepo = mock<VirtualNodeEntityRepository> {
        on { getCpiMetadataByChecksum(any()) }.doReturn(cpiMetaData)
    }
    private val mgmMemberContextKey = "member-context-key"
    private val mgmMgmContextKey = "mgm-context-key"
    private val mgmMemberContextValue = "member-context-value"
    private val mgmMgmContextValue = "mgm-context-value"
    private val mgmMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
        on { entries } doReturn mapOf(mgmMemberContextKey to mgmMemberContextValue).entries
    }
    private val mgmMgmContext: MGMContext = mock {
        on { entries } doReturn mapOf(mgmMgmContextKey to mgmMgmContextValue).entries
    }
    private val mgmMemberInfo: MemberInfo = mock {
        on { name } doReturn mgmName
        on { memberProvidedContext } doReturn mgmMemberContext
        on { mgmProvidedContext } doReturn mgmMgmContext
    }
    private val groupPolicyParser: GroupPolicyParser = mock {
        on { getMgmInfo(any(), eq(dummyGroupPolicy)) } doReturn null
        on { getMgmInfo(any(), eq(dummyGroupPolicyWithMGMInfo)) } doReturn mgmMemberInfo
        on { getMgmInfo(any(), eq(mgmGroupPolicy)) } doReturn mgmMemberInfo
    }

    private val publisherError = CordaMessageAPIIntermittentException("Error.")

    /** Returns a mock [Publisher]. */
    private fun getPublisher() = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }

    /** Returns a mock [Publisher] that throws an error whenever it tries to publish. */
    private fun getErroringPublisher() = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(
            listOf(CompletableFuture.supplyAsync { throw publisherError })
        )
    }

    /** Calls [processor].`onNext` for the given [req], and returns the result of the future. */
    private fun processRequest(
        processor: VirtualNodeWriterProcessor,
        req: VirtualNodeManagementRequest
    ): VirtualNodeManagementResponse {

        val respFuture = CompletableFuture<VirtualNodeManagementResponse>()
        processor.onNext(req, respFuture)
        return respFuture.get()
    }

    private fun virtualNodeRepositoryMock(): VirtualNodeRepository = mock()
    private fun holdingIdentityRepositoryMock(): HoldingIdentityRepository = mock()
    private fun migrationUtilityMock(): MigrationUtility = mock<MigrationUtility>()

    private fun cpkDbChangeLogRepositoryMock(changeLog: List<CpkDbChangeLog> = emptyList()): CpkDbChangeLogRepository = mock{
        on { findByCpiId(any(), any()) } doReturn changeLog
    }

    @Test
    fun `publishes correct virtual node info to Kafka`() {
        val expectedRecord = Record(VIRTUAL_NODE_INFO_TOPIC, vnodeInfo.holdingIdentity, vnodeInfo)

        val publisher = getPublisher()
        val processor = VirtualNodeWriterProcessor(
            publisher,
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
            clock,
            changeLogsRepository = cpkDbChangeLogRepositoryMock(),
            holdingIdentityRepository = holdingIdentityRepositoryMock(),
            virtualNodeRepository = virtualNodeRepositoryMock(),
            migrationUtility = migrationUtilityMock()
        )
        processRequest(processor, VirtualNodeManagementRequest(clock.instant(), vnodeCreationReq))

        verify(publisher).publish(listOf(expectedRecord))
    }

    @Test
    fun `runs empty CPI DB Migrations`() {
        val changelog = cpkDbChangeLog {
            fileChecksum(net.corda.v5.crypto.SecureHash("SHA1","alpha".toByteArray()))
            filePath("stuff.xml")
        }

        val vaultDb = mock<VirtualNodeDb>().apply { whenever(dbType).thenReturn(VirtualNodeDbType.VAULT) }
        val cryptoDb = mock<VirtualNodeDb>().apply { whenever(dbType).thenReturn(VirtualNodeDbType.CRYPTO) }

        whenever(vNodeFactory.createVNodeDbs(any(), any())).thenReturn(
            mapOf(
                VirtualNodeDbType.VAULT to vaultDb,
                VirtualNodeDbType.CRYPTO to cryptoDb
            )
        )

        val processor = VirtualNodeWriterProcessor(
            getPublisher(),
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
            clock,
            changeLogsRepository = cpkDbChangeLogRepositoryMock(listOf(changelog)),
            holdingIdentityRepository = holdingIdentityRepositoryMock(),
            virtualNodeRepository = virtualNodeRepositoryMock(),
            migrationUtility = migrationUtilityMock()
        )

        processRequest(processor, VirtualNodeManagementRequest(clock.instant(), vnodeCreationReq))

        verify(vaultDb).runCpiMigrations(any(), eq(changelog.fileChecksum.toString()))
    }

    @Test
    fun `publishes MGM member info to Kafka`() {
        val publisher = getPublisher()
        val vNodeRepo = mock<VirtualNodeEntityRepository> {
            on { getCpiMetadataByChecksum(any()) }.doReturn(cpiMetaDataWithMGM)
        }
        val processor = VirtualNodeWriterProcessor(
            publisher,
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
            clock,
            changeLogsRepository = cpkDbChangeLogRepositoryMock(listOf()),
            holdingIdentityRepository = holdingIdentityRepositoryMock(),
            virtualNodeRepository = virtualNodeRepositoryMock(),
            migrationUtility = migrationUtilityMock()
        )
        processRequest(processor, VirtualNodeManagementRequest(clock.instant(), vnodeCreationReq))

        val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
        // called twice for publishing virtual node info and MGM info
        verify(publisher, times(2)).publish(capturedPublishedList.capture())
        val publishedVirtualNodeList = capturedPublishedList.firstValue
        assertSoftly {
            it.assertThat(publishedVirtualNodeList.size).isEqualTo(1)
            val publishedVirtualNode = publishedVirtualNodeList.first()
            it.assertThat(publishedVirtualNode.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)
            it.assertThat((publishedVirtualNode.value as VirtualNodeInfo).holdingIdentity.toCorda())
                .isEqualTo(holdingIdentity)
        }
        val publishedMgmInfoList = capturedPublishedList.secondValue
        assertSoftly {
            it.assertThat(publishedMgmInfoList.size).isEqualTo(1)
            val publishedMgmInfo = publishedMgmInfoList.first()
            it.assertThat(publishedMgmInfo.topic).isEqualTo(Schemas.Membership.MEMBER_LIST_TOPIC)
            val expectedRecordKey = "${holdingIdentity.shortHash}-${mgmHoldingIdentity.shortHash}"
            it.assertThat(publishedMgmInfo.key).isEqualTo(expectedRecordKey)
            val persistentMemberPublished = publishedMgmInfo.value as PersistentMemberInfo
            it.assertThat(persistentMemberPublished.memberContext.items)
                .hasSize(1)
                .containsExactly(KeyValuePair(mgmMemberContextKey, mgmMemberContextValue))
            it.assertThat(persistentMemberPublished.mgmContext.items)
                .hasSize(1)
                .containsExactly(KeyValuePair(mgmMgmContextKey, mgmMgmContextValue))
            it.assertThat(persistentMemberPublished.viewOwningMember.toCorda())
                .isEqualTo(holdingIdentity)
        }
    }

    @Test
    fun `skips MGM member info publishing to Kafka without error if MGM information is not present in group policy`() {
        val publisher = getPublisher()
        val vNodeRepo = mock<VirtualNodeEntityRepository> {
            on { getCpiMetadataByChecksum(any()) }.doReturn(cpiMetaData)
        }
        val processor = VirtualNodeWriterProcessor(
            publisher,
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
            clock,
            changeLogsRepository = cpkDbChangeLogRepositoryMock(listOf()),
            holdingIdentityRepository = holdingIdentityRepositoryMock(),
            virtualNodeRepository = virtualNodeRepositoryMock(),
            migrationUtility = migrationUtilityMock()
        )
        processRequest(processor, VirtualNodeManagementRequest(clock.instant(), vnodeCreationReq))

        val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
        // called once for publishing virtual node info
        verify(publisher, times(1)).publish(capturedPublishedList.capture())
        val publishedVirtualNodeList = capturedPublishedList.firstValue
        assertSoftly {
            it.assertThat(publishedVirtualNodeList.size).isEqualTo(1)
            val publishedVirtualNode = publishedVirtualNodeList.first()
            it.assertThat(publishedVirtualNode.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)
            it.assertThat((publishedVirtualNode.value as VirtualNodeInfo).holdingIdentity.toCorda())
                .isEqualTo(holdingIdentity)
        }
    }

    @Test
    fun `generates group ID during MGM virtual node creation`() {
        val mgmVnodeCreationReq =
            VirtualNodeCreateRequest(
                mgmName.toString(),
                CPI_ID_SHORT_HASH,
                "dummy_vault_ddl_config",
                "dummy_vault_dml_config",
                "dummy_crypto_ddl_config",
                "dummy_crypto_dml_config",
                "dummy_uniqueness_ddl_config",
                "dummy_uniqueness_dml_config",
                "update_actor"
            )
        val vNodeRepo = mock<VirtualNodeEntityRepository> {
            on { getCpiMetadataByChecksum(any()) }.doReturn(
                CpiMetadataLite(
                    cpiId,
                    CPI_ID_SHORT_HASH,
                    MGM_DEFAULT_GROUP_ID,
                    mgmGroupPolicy,
                    "",
                    "",
                    emptySet()
                )
            )
        }

        val publisher = getPublisher()
        val processor = VirtualNodeWriterProcessor(
            publisher,
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
            clock,
            changeLogsRepository = cpkDbChangeLogRepositoryMock(listOf()),
            holdingIdentityRepository = holdingIdentityRepositoryMock(),
            virtualNodeRepository = virtualNodeRepositoryMock(),
            migrationUtility = migrationUtilityMock()
        )

        processRequest(processor, VirtualNodeManagementRequest(clock.instant(), mgmVnodeCreationReq))

        val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
        verify(publisher, times(2)).publish(capturedPublishedList.capture())
        val publishedVirtualNodeList = capturedPublishedList.firstValue
        assertSoftly {
            val publishedVirtualNode = publishedVirtualNodeList.first()
            with((publishedVirtualNode.value as VirtualNodeInfo).holdingIdentity.toCorda()) {
                it.assertThat(x500Name).isEqualTo(mgmName)
                it.assertThat(groupId).isNotEqualTo(MGM_DEFAULT_GROUP_ID)
            }
        }
    }

    @Test
    fun `sends RPC success response after publishing virtual node info to Kafka`() {
        val expectedResp = VirtualNodeManagementResponse(
            clock.instant(),
            VirtualNodeCreateResponse(
                vnodeCreationReq.x500Name,
                vnodeInfo.cpiIdentifier,
                vnodeCreationReq.cpiFileChecksum,
                vnodeInfo.holdingIdentity.groupId,
                vnodeInfo.holdingIdentity,
                holdingIdentity.shortHash.value,
                connectionId,
                connectionId,
                connectionId,
                connectionId,
                connectionId,
                connectionId,
                null,
                OperationalStatus.ACTIVE.name,
                OperationalStatus.ACTIVE.name,
                OperationalStatus.ACTIVE.name,
                OperationalStatus.ACTIVE.name
            )
        )

        val processor = VirtualNodeWriterProcessor(
            getPublisher(),
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
            clock,
            changeLogsRepository = cpkDbChangeLogRepositoryMock(listOf()),
            holdingIdentityRepository = holdingIdentityRepositoryMock(),
            virtualNodeRepository = virtualNodeRepositoryMock(),
            migrationUtility = migrationUtilityMock()
        )
        val resp = processRequest(processor, VirtualNodeManagementRequest(clock.instant(), vnodeCreationReq))

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if fails to publish virtual node info to Kafka`() {
        // consider eliminating these 2 instances and just test expected values directrly in the asserts at
        // the bottom of this function
        val expectedEnvelope = ExceptionEnvelope(
            VirtualNodeWriteServiceException::class.java.name,
            ""
        )

        val processor = VirtualNodeWriterProcessor(
            getErroringPublisher(),
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
            clock,
            changeLogsRepository = cpkDbChangeLogRepositoryMock(listOf()),
            holdingIdentityRepository = holdingIdentityRepositoryMock(),
            virtualNodeRepository = virtualNodeRepositoryMock(),
            migrationUtility = migrationUtilityMock()
        )
        val resp = processRequest(
            processor,
            VirtualNodeManagementRequest(clock.instant(), vnodeCreationReq)
        ).responseType as VirtualNodeManagementResponseFailure

        assertEquals(expectedEnvelope.errorType, resp.exception.errorType)
        assertTrue(resp.exception.errorMessage.contains("written to the database, but couldn't be published"))
    }

    private fun testInvalidRequest(request: VirtualNodeCreateRequest, expectedEnvelope: ExceptionEnvelope) {
        val expectedResp = VirtualNodeManagementResponse(
            clock.instant(),
            VirtualNodeManagementResponseFailure(
                expectedEnvelope
            )
        )

        val processor = VirtualNodeWriterProcessor(
            getPublisher(),
            connectionManager,
            vNodeRepo,
            vNodeFactory,
            groupPolicyParser,
            clock,
            changeLogsRepository = cpkDbChangeLogRepositoryMock(listOf()),
            holdingIdentityRepository = holdingIdentityRepositoryMock(),
            virtualNodeRepository = virtualNodeRepositoryMock(),
            migrationUtility = migrationUtilityMock()
        )
        val resp = processRequest(processor, VirtualNodeManagementRequest(clock.instant(), request))

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if CPI checksum is null`() {
        val request = VirtualNodeCreateRequest(
            vnodeInfo.holdingIdentity.x500Name,
            null,
            "dummy_vault_ddl_config",
            "dummy_vault_dml_config",
            "dummy_crypto_ddl_config",
            "dummy_crypto_dml_config",
            "dummy_uniqueness_ddl_config",
            "dummy_uniqueness_dml_config",
            "update_actor"
        )
        val expectedEnvelope = ExceptionEnvelope(
            IllegalArgumentException::class.java.name,
            "CPI file checksum value is missing"
        )

        testInvalidRequest(request, expectedEnvelope)
    }

    @Test
    fun `sends RPC failure response if CPI checksum is blank`() {
        val request = VirtualNodeCreateRequest(
            vnodeInfo.holdingIdentity.x500Name,
            "",
            "dummy_vault_ddl_config",
            "dummy_vault_dml_config",
            "dummy_crypto_ddl_config",
            "dummy_crypto_dml_config",
            "dummy_uniqueness_ddl_config",
            "dummy_uniqueness_dml_config",
            "update_actor"
        )
        val expectedEnvelope = ExceptionEnvelope(
            IllegalArgumentException::class.java.name,
            "CPI file checksum value is missing"
        )

        testInvalidRequest(request, expectedEnvelope)
    }

    @Test
    fun `sends RPC failure response if Vault DDL connection is provided but Vault DML connection is not`() {
        val request = VirtualNodeCreateRequest(
            vnodeInfo.holdingIdentity.x500Name,
            CPI_ID_SHORT_HASH,
            "dummy_vault_ddl_config",
            "",
            "dummy_crypto_ddl_config",
            "dummy_crypto_dml_config",
            "dummy_uniqueness_ddl_config",
            "dummy_uniqueness_dml_config",
            "update_actor"
        )
        val expectedEnvelope = ExceptionEnvelope(
            IllegalArgumentException::class.java.name,
            "If Vault DDL connection is provided, Vault DML connection needs to be provided as well."
        )

        testInvalidRequest(request, expectedEnvelope)
    }

    @Test
    fun `sends RPC failure response if Crypto DDL connection is provided, Crypto DML connection is not`() {
        val request = VirtualNodeCreateRequest(
            vnodeInfo.holdingIdentity.x500Name,
            CPI_ID_SHORT_HASH,
            "dummy_vault_ddl_config",
            "dummy_vault_dml_config",
            "dummy_crypto_ddl_config",
            null,
            "dummy_uniqueness_ddl_config",
            "dummy_uniqueness_dml_config",
            "update_actor"
        )
        val expectedEnvelope = ExceptionEnvelope(
            IllegalArgumentException::class.java.name,
            "If Crypto DDL connection is provided, Crypto DML connection needs to be provided as well."
        )

        testInvalidRequest(request, expectedEnvelope)
    }

    @Test
    fun `sends RPC failure response if Uniqueness DDL connection is provided, Uniqueness DML connection is not`() {
        val request = VirtualNodeCreateRequest(
            vnodeInfo.holdingIdentity.x500Name,
            CPI_ID_SHORT_HASH,
            "dummy_vault_ddl_config",
            "dummy_vault_dml_config",
            "dummy_crypto_ddl_config",
            "dummy_crypto_dml_config",
            "dummy_uniqueness_ddl_config",
            null,
            "update_actor"
        )
        val expectedEnvelope = ExceptionEnvelope(
            IllegalArgumentException::class.java.name,
            "If Uniqueness DDL connection is provided, Uniqueness DML connection needs to be provided as well."
        )

        testInvalidRequest(request, expectedEnvelope)
    }

    @Test
    fun `sends RPC failure response if X500 name is null`() {
        val request = VirtualNodeCreateRequest(
            null,
            CPI_ID_SHORT_HASH,
            "dummy_vault_ddl_config",
            "dummy_vault_dml_config",
            "dummy_crypto_ddl_config",
            "dummy_crypto_dml_config",
            "dummy_uniqueness_ddl_config",
            "dummy_uniqueness_dml_config",
            "update_actor"
        )
        val expectedEnvelope = ExceptionEnvelope(
            IllegalArgumentException::class.java.name,
            "X500 name \"${request.x500Name}\" could not be parsed. Cause: x500Name must not be null"
        )

        testInvalidRequest(request, expectedEnvelope)
    }

    @Test
    fun `sends RPC failure response if X500 name can't be parsed`() {
        val request = VirtualNodeCreateRequest(
            "invalid x500 name",
            CPI_ID_SHORT_HASH,
            "dummy_vault_ddl_config",
            "dummy_vault_dml_config",
            "dummy_crypto_ddl_config",
            "dummy_crypto_dml_config",
            "dummy_uniqueness_ddl_config",
            "dummy_uniqueness_dml_config",
            "update_actor"
        )
        val expectedEnvelope = ExceptionEnvelope(
            IllegalArgumentException::class.java.name,
            "X500 name \"${request.x500Name}\" could not be parsed. Cause: improperly specified input name: invalid x500 name"
        )

        testInvalidRequest(request, expectedEnvelope)
    }

    @Test
    fun `sends RPC failure response if the CPI with the given ID is not stored on the node`() {
        val expectedEnvelope = ExceptionEnvelope(
            CpiNotFoundException::class.java.name,
            "CPI with file checksum ${vnodeCreationReq.cpiFileChecksum} was not found."
        )
        val expectedResp = VirtualNodeManagementResponse(
            clock.instant(),
            VirtualNodeManagementResponseFailure(
                expectedEnvelope
            )
        )

        val entityRepository = mock<VirtualNodeEntityRepository>().apply {
            whenever(getCpiMetadataByChecksum(any())).thenReturn(null)
        }
        val processor = VirtualNodeWriterProcessor(
            getPublisher(),
            connectionManager,
            entityRepository,
            vNodeFactory,
            groupPolicyParser,
            clock,
            changeLogsRepository = cpkDbChangeLogRepositoryMock(listOf()),
            holdingIdentityRepository = holdingIdentityRepositoryMock(),
            virtualNodeRepository = virtualNodeRepositoryMock(),
            migrationUtility = migrationUtilityMock()
        )
        val resp = processRequest(processor, VirtualNodeManagementRequest(clock.instant(), vnodeCreationReq))

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if the virtual node already exists`() {
        val expectedEnvelope = ExceptionEnvelope(
            VirtualNodeAlreadyExistsException::class.java.name,
            "Virtual node for CPI with file checksum ${vnodeCreationReq.cpiFileChecksum}" +
                    " and x500Name ${vnodeCreationReq.x500Name} already exists."
        )
        val expectedResp = VirtualNodeManagementResponse(
            clock.instant(),
            VirtualNodeManagementResponseFailure(
                expectedEnvelope
            )
        )

        val entityRepository = mock<VirtualNodeEntityRepository>().apply {
            whenever(getCpiMetadataByChecksum(any())).thenReturn(cpiMetaData)
        }
        val vnodeRepo = mock<VirtualNodeRepository> {
            on { find(any(), any()) }.doReturn(mock())
        }
        val processor = VirtualNodeWriterProcessor(
            getPublisher(),
            connectionManager,
            entityRepository,
            vNodeFactory,
            groupPolicyParser,
            clock,
            changeLogsRepository = cpkDbChangeLogRepositoryMock(listOf()),
            holdingIdentityRepository = holdingIdentityRepositoryMock(),
            virtualNodeRepository = vnodeRepo,
            migrationUtility = migrationUtilityMock()
        )
        val resp = processRequest(processor, VirtualNodeManagementRequest(clock.instant(), vnodeCreationReq))

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if the there is a holding-identity collision`() {
        val expectedEnvelope = ExceptionEnvelope(
            VirtualNodeWriteServiceException::class.java.name,
            ""
        )

        val entityRepository = mock<VirtualNodeEntityRepository> {
            on { getCpiMetadataByChecksum(any()) }.doReturn(cpiMetaData)
        }

        val holdingIdentityRepository = mock<HoldingIdentityRepository> {
            on { find(any(), any()) }.doReturn(mock())
        }
        val vnodeRepo = mock<VirtualNodeRepository> {
            on { find(any(), any()) }.doReturn(null)
        }

        val processor = VirtualNodeWriterProcessor(
            getPublisher(),
            connectionManager,
            entityRepository,
            vNodeFactory,
            groupPolicyParser,
            clock,
            changeLogsRepository = cpkDbChangeLogRepositoryMock(listOf()),
            holdingIdentityRepository = holdingIdentityRepository,
            virtualNodeRepository = vnodeRepo,
            migrationUtility = migrationUtilityMock()
        )
        val resp = processRequest(
            processor,
            VirtualNodeManagementRequest(clock.instant(), vnodeCreationReq)
        ).responseType as VirtualNodeManagementResponseFailure

        assertEquals(expectedEnvelope.errorType, resp.exception.errorType)
        assertTrue(
            resp.exception.errorMessage.contains(
                "New holding identity $holdingIdentity has a short hash that collided with existing holding identity"
            )
        )
    }
}
