package net.corda.virtualnode.write.db.impl.tests.writer.asyncoperation.factories

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.membership.lib.MemberInfoFactory
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.impl.tests.ALICE_HOLDING_ID1
import net.corda.virtualnode.write.db.impl.tests.CPI_IDENTIFIER1
import net.corda.virtualnode.write.db.impl.tests.GROUP_ID1
import net.corda.virtualnode.write.db.impl.tests.MGM_HOLDING_ID1
import net.corda.virtualnode.write.db.impl.tests.MGM_X500_NAME
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.factories.RecordFactoryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

class RecordFactoryImplTest {
    private val memberInfoFactory = mock<MemberInfoFactory>()

    @Test
    fun `create mgm info record`() {
        val memberProvidedContext = mock<MemberContext>().apply {
            whenever(this.parse("corda.groupId", String::class.java)).thenReturn(GROUP_ID1)
            whenever(entries).thenReturn(setOf())
        }

        val mgmProvidedContext = mock<MGMContext>().apply {
            whenever(entries).thenReturn(setOf())
        }

        val mgmMemberInfo = mock<MemberInfo>().apply {
            whenever(this.name).thenReturn(MGM_X500_NAME)
            whenever(this.memberProvidedContext).thenReturn(memberProvidedContext)
            whenever(this.mgmProvidedContext).thenReturn(mgmProvidedContext)
        }

        val persistentMemberInfo = mock<PersistentMemberInfo>()

        whenever(
            memberInfoFactory.createMgmOrStaticPersistentMemberInfo(
                ALICE_HOLDING_ID1.toAvro(),
                mgmMemberInfo,
                CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf()), ByteBuffer.wrap(byteArrayOf())),
                CryptoSignatureSpec("", null, null),
            )
        ).doReturn(persistentMemberInfo)

        val target = RecordFactoryImpl(mock(), memberInfoFactory)

        val result = target.createMgmInfoRecord(ALICE_HOLDING_ID1, mgmMemberInfo)

        assertThat(result.topic).isEqualTo(MEMBER_LIST_TOPIC)
        assertThat(result.key).isEqualTo("${ALICE_HOLDING_ID1.shortHash}-${MGM_HOLDING_ID1.shortHash}")
        assertThat(result.value).isEqualTo(persistentMemberInfo)
    }

    @Test
    fun `create virtual node record`() {
        val now = Instant.now()
        val clock = mock<Clock>()
        whenever(clock.instant()).thenReturn(now)

        val dbConnections = VirtualNodeDbConnections(
            vaultDdlConnectionId = UUID.randomUUID(),
            vaultDmlConnectionId = UUID.randomUUID(),
            cryptoDdlConnectionId = UUID.randomUUID(),
            cryptoDmlConnectionId = UUID.randomUUID(),
            uniquenessDdlConnectionId = UUID.randomUUID(),
            uniquenessDmlConnectionId = UUID.randomUUID(),
        )

        val expectedVirtualNodeInfo =
            VirtualNodeInfo(
                ALICE_HOLDING_ID1,
                CPI_IDENTIFIER1,
                dbConnections.vaultDdlConnectionId,
                dbConnections.vaultDmlConnectionId,
                dbConnections.cryptoDdlConnectionId,
                dbConnections.cryptoDmlConnectionId,
                dbConnections.uniquenessDdlConnectionId,
                dbConnections.uniquenessDmlConnectionId,
                timestamp = now,
            ).toAvro()

        val target = RecordFactoryImpl(clock, memberInfoFactory)

        val result = target.createVirtualNodeInfoRecord(
            ALICE_HOLDING_ID1,
            CPI_IDENTIFIER1,
            dbConnections,
            externalMessagingRouteConfig = null
        )

        assertThat(result.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)
        assertThat(result.key).isEqualTo(ALICE_HOLDING_ID1.toAvro())
        assertThat(result.value).isEqualTo(expectedVirtualNodeInfo)
    }
}
