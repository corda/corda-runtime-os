package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.SignedData
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberSignature
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.TestRandom
import net.corda.test.util.time.TestClock
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class QueryMemberSignatureHandlerTest {
    private val memberContext = "memberContext".toByteArray()
    private val mgmContext = "mgmContext".toByteArray()
    private val signatureSpec = "dummySignatureSpec"

    private val clock = TestClock(Instant.ofEpochMilli(0))
    private val jpaEntitiesSet = mock<JpaEntitiesSet>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn jpaEntitiesSet
    }
    private val memberInfoFactory = mock<MemberInfoFactory>()
    private val vaultDmlConnectionId = UUID(0, 0)
    private val virtualNodeInfo = VirtualNodeInfo(
        vaultDmlConnectionId = vaultDmlConnectionId,
        cpiIdentifier = CpiIdentifier(
            "", "", TestRandom.secureHash()
        ),
        cryptoDmlConnectionId = UUID(0, 0),
        uniquenessDmlConnectionId = UUID(0, 0),
        holdingIdentity = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "").toCorda(),
        timestamp = clock.instant(),
    )
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(any()) } doReturn virtualNodeInfo
    }
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn mock()
    }
    private val factory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on {
            getOrCreateEntityManagerFactory(
                vaultDmlConnectionId,
                jpaEntitiesSet
            )
        } doReturn factory
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock()
    private val keyEncodingService: KeyEncodingService = mock()
    private val platformInfoProvider: PlatformInfoProvider = mock()
    private val transactionTimerFactory = { _: String -> transactionTimer }
    private val service = PersistenceHandlerServices(
        clock,
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
        cordaAvroSerializationFactory,
        virtualNodeInfoReadService,
        keyEncodingService,
        platformInfoProvider,
        mock(),
        mock(),
        mock(),
        transactionTimerFactory
    )
    private val handler = QueryMemberSignatureHandler(service)

    @Test
    fun `invoke throws exception if member can not be found`() {
        val context = MembershipRequestContext(
            clock.instant(),
            "id",
            HoldingIdentity("CN=Member, O=Corp, L=LDN, C=GB", "group"),
        )
        val request = QueryMemberSignature(
            (1..10).map {
                HoldingIdentity("name-$it", "group")
            }
        )
        whenever(entityManager.find(eq(MemberInfoEntity::class.java), any())).doReturn(null)

        assertThrows<MembershipPersistenceException> {
            handler.invoke(context, request)
        }
    }

    @Test
    fun `invoke returns the correct data`() {
        val viewOwner = HoldingIdentity("CN=Member, O=Corp, L=LDN, C=GB", "group")
        val context = MembershipRequestContext(
            clock.instant(),
            "id",
            viewOwner,
        )
        val members = (1..10).map {
            HoldingIdentity("CN=Member-$it, O=Corp, L=LDN, C=GB", "group")
        }
        val request = QueryMemberSignature(
            members
        )
        whenever(entityManager.find(eq(MemberInfoEntity::class.java), any())).doAnswer {
            val memberKey = it.arguments[1] as MemberInfoEntityPrimaryKey
            MemberInfoEntity(
                memberKey.groupId,
                memberKey.memberX500Name,
                false,
                "ACTIVE",
                Instant.ofEpochMilli(200),
                memberContext,
                "pk-${memberKey.memberX500Name}".toByteArray(),
                "sig-${memberKey.memberX500Name}".toByteArray(),
                signatureSpec,
                mgmContext,
                1L,
                isDeleted = false
            )
        }

        members.forEach { member ->
            whenever(
                memberInfoFactory.createPersistentMemberInfo(
                    eq(viewOwner),
                    eq(memberContext),
                    eq(mgmContext),
                    eq("pk-${member.x500Name}".toByteArray()),
                    eq("sig-${member.x500Name}".toByteArray()),
                    eq(signatureSpec)
                )
            ).doAnswer {
                mock {
                    on { signedMemberContext } doReturn SignedData(
                        ByteBuffer.wrap(memberContext),
                        CryptoSignatureWithKey(
                            ByteBuffer.wrap("pk-${member.x500Name}".toByteArray()),
                            ByteBuffer.wrap("sig-${member.x500Name}".toByteArray()),
                        ),
                        CryptoSignatureSpec(signatureSpec, null, null),
                    )
                }
            }
        }

        val result = handler.invoke(context, request)
        assertThat(result.members.size).isEqualTo(10)
        val signatures = result.members.map { it.signedMemberContext.signature }
        assertThat(signatures).containsExactlyElementsOf(
            members.map { member ->
                CryptoSignatureWithKey(
                    ByteBuffer.wrap("pk-${member.x500Name}".toByteArray()),
                    ByteBuffer.wrap("sig-${member.x500Name}".toByteArray()),
                )
            }
        )
        val signatureSpecs = result.members.map { it.signedMemberContext.signatureSpec }
        assertThat(signatureSpecs).containsOnly(CryptoSignatureSpec(signatureSpec, null, null))
    }
}
