package net.corda.membership.lib.impl

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SignedMemberInfo
import net.corda.membership.lib.retrieveSignatureSpec
import net.corda.membership.lib.toSortedMap
import net.corda.v5.base.exceptions.CordaRuntimeException
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
        if (signedMemberContext == null && serializedMgmContext == null) {
            createMemberInfo(
                memberContext.toSortedMap(),
                mgmContext.toSortedMap(),
            )
        } else {
            createMemberInfo(
                deserialize(signedMemberContext.data.array()).toSortedMap(),
                deserialize(serializedMgmContext.array()).toSortedMap(),
            )
        }
    }

    override fun createMemberInfo(memberContext: ByteArray, mgmContext: ByteArray): MemberInfo =
        createMemberInfo(deserialize(memberContext).toSortedMap(), deserialize(mgmContext).toSortedMap())

    override fun createPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        memberContext: ByteArray,
        mgmContext: ByteArray,
        memberSignature: CryptoSignatureWithKey,
        memberSignatureSpec: CryptoSignatureSpec,
    ) = PersistentMemberInfo.newBuilder()
        .setViewOwningMember(viewOwningMember)
        .setMemberContext(deserialize(memberContext))
        .setMgmContext(deserialize(mgmContext))
        .setSignedMemberContext(createSignedData(memberContext, memberSignature, memberSignatureSpec))
        .setSerializedMgmContext(mgmContext.toByteBuffer())
        .build()

    override fun createPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        memberContext: ByteArray,
        mgmContext: ByteArray,
        memberSignatureKey: ByteArray,
        memberSignatureContent: ByteArray,
        memberSignatureSpec: String
    ) = PersistentMemberInfo.newBuilder()
        .setViewOwningMember(viewOwningMember)
        .setMemberContext(deserialize(memberContext))
        .setMgmContext(deserialize(mgmContext))
        .setSignedMemberContext(
            createSignedData(
                memberContext,
                CryptoSignatureWithKey(memberSignatureKey.toByteBuffer(), memberSignatureContent.toByteBuffer()),
                retrieveSignatureSpec(memberSignatureSpec)
            )
        )
        .setSerializedMgmContext(mgmContext.toByteBuffer())
        .build()

    override fun createPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        memberInfo: MemberInfo,
        memberSignature: CryptoSignatureWithKey,
        memberSignatureSpec: CryptoSignatureSpec,
    ) = PersistentMemberInfo.newBuilder()
        .setViewOwningMember(viewOwningMember)
        .setMemberContext(memberInfo.memberProvidedContext.toAvro())
        .setMgmContext(memberInfo.mgmProvidedContext.toAvro())
        .setSignedMemberContext(
            createSignedData(
                serialize(memberInfo.memberProvidedContext.toAvro()),
                memberSignature,
                memberSignatureSpec,
            )
        )
        .setSerializedMgmContext(serialize(memberInfo.mgmProvidedContext.toAvro()).toByteBuffer())
        .build()

    override fun createSignedMemberInfo(
        memberContext: ByteArray,
        mgmContext: ByteArray,
        memberSignature: CryptoSignatureWithKey,
        memberSignatureSpec: CryptoSignatureSpec
    ) = SignedMemberInfoImpl(
        memberContext,
        mgmContext,
        memberSignature,
        memberSignatureSpec,
        this,
    )

    override fun createSignedMemberInfo(
        memberInfo: MemberInfo,
        memberSignature: CryptoSignatureWithKey,
        memberSignatureSpec: CryptoSignatureSpec
    ) = SignedMemberInfoImpl(
        serialize(memberInfo.memberProvidedContext.toAvro()),
        serialize(memberInfo.mgmProvidedContext.toAvro()),
        memberSignature,
        memberSignatureSpec,
        this,
    )

    private fun createSignedData(
        memberProvidedContext: ByteArray,
        memberSignature: CryptoSignatureWithKey,
        memberSignatureSpec: CryptoSignatureSpec,
    ) = SignedData.newBuilder()
        .setData(memberProvidedContext.toByteBuffer())
        .setSignature(memberSignature)
        .setSignatureSpec(memberSignatureSpec)
        .build()

    private fun ByteArray.toByteBuffer() = ByteBuffer.wrap(this)

    /*override fun createPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        signedMemberInfo: SignedMemberInfo
    ): PersistentMemberInfo = PersistentMemberInfo.newBuilder()
        .setMemberContext(signedMemberInfo.memberInfo.memberProvidedContext.toAvro())
        .setMgmContext(signedMemberInfo.memberInfo.mgmProvidedContext.toAvro())
        .setSignedMemberContext(
            SignedData(
                ByteBuffer.wrap(serializeKeyValuePairList(signedMemberInfo.memberInfo.memberProvidedContext.toAvro())),
                signedMemberInfo.memberSignature,
                signedMemberInfo.memberSignatureSpec,
            )
        )
        .setSerializedMgmContext(ByteBuffer.wrap(serializeKeyValuePairList(signedMemberInfo.memberInfo.mgmProvidedContext.toAvro())))
        .build()

    override fun createPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        memberInfo: MemberInfo,
    ): PersistentMemberInfo = PersistentMemberInfo.newBuilder()
        .setViewOwningMember(viewOwningMember)
        .setMemberContext(memberInfo.memberProvidedContext.toAvro())
        .setMgmContext(memberInfo.mgmProvidedContext.toAvro())
        .setSignedData(
            createSignedMembershipContexts(
                serialize(memberInfo.memberProvidedContext.toAvro()),
                serialize(memberInfo.mgmProvidedContext.toAvro()),
            )
        )
        .build()

    override fun createPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        memberProvidedContext: ByteArray,
        mgmProvidedContext: ByteArray
    ): PersistentMemberInfo = PersistentMemberInfo.newBuilder()
        .setViewOwningMember(viewOwningMember)
        .setMemberContext(deserialize(memberProvidedContext))
        .setMgmContext(deserialize(mgmProvidedContext))
        .setSignedData(createSignedMembershipContexts(memberProvidedContext, mgmProvidedContext))
        .build()

    private fun createSignedMembershipContexts(
        memberProvidedContext: ByteArray,
        mgmProvidedContext: ByteArray
    ): SignedContexts = SignedContexts.newBuilder()
        .setMemberContext(ByteBuffer.wrap(memberProvidedContext))
        .setMgmContext(ByteBuffer.wrap(mgmProvidedContext))
        .build()*/
}