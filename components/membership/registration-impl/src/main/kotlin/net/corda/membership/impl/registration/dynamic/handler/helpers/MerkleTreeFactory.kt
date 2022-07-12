package net.corda.membership.impl.registration.dynamic.mgm.handler.helpers
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.membership.MemberInfo
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Enumeration

internal class MerkleTreeFactory(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val digestService: DigestService
) {
    private val LayeredPropertyMap.inputStream
        get() =
            serializer.serialize(this)?.inputStream() ?: throw CordaRuntimeException("Could not serialize context")

    private companion object {
        val logger = contextLogger()
    }

    private val serializer: CordaAvroSerializer<LayeredPropertyMap> by lazy {
        cordaAvroSerializationFactory.createAvroSerializer<LayeredPropertyMap> {
            logger.warn("Serialization failed")
        }
    }
    fun buildTree(members: Collection<MemberInfo>): MerkleTree {
        // Should be replaced once https://github.com/corda/corda-runtime-os/pull/1550 is merged
        return object : MerkleTree {
            override val digestProvider: MerkleTreeHashDigestProvider
                get() = TODO("Not yet implemented")
            override val leaves: List<ByteArray>
                get() = TODO("Not yet implemented")

            override fun createAuditProof(leafIndices: List<Int>): MerkleProof {
                TODO("Not yet implemented")
            }
            override val root: SecureHash by lazy {
                val enumInputs = object : Enumeration<InputStream> {
                    val iterator = members.iterator()
                    override fun hasMoreElements() = iterator.hasNext()

                    override fun nextElement(): InputStream {
                        val member = iterator.next()
                        return SequenceInputStream(
                            member.memberProvidedContext.inputStream,
                            member.mgmProvidedContext.inputStream,
                        )
                    }
                }
                val inputs = SequenceInputStream(enumInputs)
                digestService.hash(inputs, DigestAlgorithmName.DEFAULT_ALGORITHM_NAME)
            }
        }
    }
}
