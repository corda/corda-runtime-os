package net.corda.membership.lib.impl

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.toSortedMap
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.SortedMap

@Component(service = [MemberInfoFactory::class])
class MemberInfoFactoryImpl @Activate constructor(
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : MemberInfoFactory {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val serializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to create serializer for key value pair list.")
        }

    private val deserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to create deserializer for key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }

    private fun serialize(context: KeyValuePairList): ByteArray {
        return serializer.serialize(context) ?: throw CordaRuntimeException(
            "Failed to serialize key value pair list."
        )
    }

    private fun deserialize(data: ByteArray): KeyValuePairList {
        return deserializer.deserialize(data) ?: throw CordaRuntimeException(
            "Failed to deserialize key value pair list."
        )
    }

    override fun createMemberInfo(
        memberContext: MemberContext,
        mgmContext: MGMContext
    ) = MemberInfoImpl(memberContext, mgmContext)

    override fun createMemberInfo(
        memberContext: SortedMap<String, String?>,
        mgmContext: SortedMap<String, String?>
    ) = with(layeredPropertyMapFactory) {
        createMemberInfo(
            create<MemberContextImpl>(memberContext),
            create<MGMContextImpl>(mgmContext)
        )
    }

    override fun createMemberInfo(
        memberInfo: PersistentMemberInfo
    ) = with(memberInfo) {
        if (memberContextBytes == null || mgmContextBytes == null) {
            createMemberInfo(
                memberContext.toSortedMap(),
                mgmContext.toSortedMap(),
            )
        } else {
            createMemberInfo(
                deserialize(memberContextBytes.array()).toSortedMap(),
                deserialize(mgmContextBytes.array()).toSortedMap(),
            )
        }
    }

    override fun createPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        memberInfo: MemberInfo,
    ): PersistentMemberInfo = PersistentMemberInfo.newBuilder()
        .setViewOwningMember(viewOwningMember)
        .setMemberContext(null)
        .setMgmContext(null)
        .setMemberContextBytes(convertToByteBuffer(memberInfo.memberProvidedContext))
        .setMgmContextBytes(convertToByteBuffer(memberInfo.mgmProvidedContext))
        .build()

    override fun createPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        memberProvidedContext: ByteArray,
        mgmProvidedContext: ByteArray
    ): PersistentMemberInfo = PersistentMemberInfo.newBuilder()
        .setViewOwningMember(viewOwningMember)
        .setMemberContext(null)
        .setMgmContext(null)
        .setMemberContextBytes(ByteBuffer.wrap(memberProvidedContext))
        .setMgmContextBytes(ByteBuffer.wrap(mgmProvidedContext))
        .build()

    private fun convertToByteBuffer(
        context: LayeredPropertyMap
    ) = ByteBuffer.wrap(serialize(context.toAvro()))
}