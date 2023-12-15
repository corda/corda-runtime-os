package net.corda.virtualnode.write.db.impl.writer.asyncoperation.factories

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections
import java.nio.ByteBuffer

internal class RecordFactoryImpl(
    private val clock: Clock,
    private val memberInfoFactory: MemberInfoFactory,
) : RecordFactory {

    override fun createVirtualNodeInfoRecord(
        holdingIdentity: HoldingIdentity,
        cpiIdentifier: CpiIdentifier,
        dbConnections: VirtualNodeDbConnections,
        externalMessagingRouteConfig: String?
    ): Record<*, *> {
        val virtualNodeInfo = with(dbConnections) {
            VirtualNodeInfo(
                holdingIdentity,
                cpiIdentifier,
                vaultDdlConnectionId,
                vaultDmlConnectionId,
                cryptoDdlConnectionId,
                cryptoDmlConnectionId,
                uniquenessDdlConnectionId,
                uniquenessDmlConnectionId,
                externalMessagingRouteConfig = externalMessagingRouteConfig,
                timestamp = clock.instant(),
            ).toAvro()
        }
        return Record(VIRTUAL_NODE_INFO_TOPIC, virtualNodeInfo.holdingIdentity, virtualNodeInfo)
    }

    override fun createMgmInfoRecord(holdingIdentity: HoldingIdentity, mgmInfo: MemberInfo): Record<*, *> {
        val mgmHoldingIdentity = HoldingIdentity(mgmInfo.name, mgmInfo.groupId)
        return Record(
            MEMBER_LIST_TOPIC,
            "${holdingIdentity.shortHash}-${mgmHoldingIdentity.shortHash}",
            memberInfoFactory.createMgmOrStaticPersistentMemberInfo(
                holdingIdentity.toAvro(),
                mgmInfo,
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(byteArrayOf()),
                    ByteBuffer.wrap(byteArrayOf())
                ),
                CryptoSignatureSpec("", null, null),
            )
        )
    }
}
