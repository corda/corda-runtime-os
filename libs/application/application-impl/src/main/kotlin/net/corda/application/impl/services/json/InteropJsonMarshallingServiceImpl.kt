package net.corda.application.impl.services.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.databind.util.LRUMap
import com.fasterxml.jackson.databind.util.LookupCache
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProofProvider
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.marshalling.InteropJsonMarshallingService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import java.time.Instant
import java.util.Collections.unmodifiableList
import java.util.Collections.unmodifiableMap

/**
 * Advanced implementation, requires alignment with other serialization such as that used in the HTTP library
 */
@Component(
    service = [ InteropJsonMarshallingService::class, UsedByFlow::class ],
    scope = PROTOTYPE
)
class InteropJsonMarshallingServiceImpl
@Activate constructor(
    @Reference(service = MerkleTreeProofProvider::class)
    private val merkleTreeProofProvider: MerkleTreeProofProvider
) : InteropJsonMarshallingService, UsedByFlow, SingletonSerializeAsToken {
    private companion object {
        private const val INITIAL_SIZE = 16
        private const val MAX_SIZE = 200
    }

    private val mapper = ObjectMapper().apply {
        // Provide our own TypeFactory instance rather than using shared global one.
        typeFactory = TypeFactory.defaultInstance()
            .withCache(LRUMap<Any, JavaType>(INITIAL_SIZE, MAX_SIZE) as LookupCache<Any, JavaType>)

        // Provide our own AnnotationIntrospector to avoid using a shared global cache.
        setAnnotationIntrospector(JacksonAnnotationIntrospector())
        val module = SimpleModule()
        module.addSerializer(SecureHash::class.java, SecureHashSerializer)
        module.addDeserializer(SecureHash::class.java, SecureHashDeserializer)

        // Interoperability
        //TODO move to new service interface or new service implementation
        module.addDeserializer(DigitalSignatureAndMetadata::class.java, DigitalSignatureAndMetadataDeserializer())
        module.addDeserializer(DigitalSignature.WithKeyId::class.java, DigitalSignatureWithKeyIdDeserializer())
        module.addDeserializer(DigitalSignatureMetadata::class.java, DigitalSignatureMetadataDeserializer())
        module.addDeserializer(SignatureSpec::class.java, SignatureSpecDeserializer())
        module.addSerializer(DigitalSignatureMetadata::class.java, DigitalSignatureMetadataSerializer())
        module.addDeserializer(IndexedMerkleLeaf::class.java, IndexedMerkleLeafDeserializer(merkleTreeProofProvider))
        module.addDeserializer(MerkleProof::class.java, MerkleProofDeserializer(merkleTreeProofProvider))
        registerModule(JavaTimeModule())

        // Register Kotlin after resetting the AnnotationIntrospector.
        registerModule(KotlinModule.Builder().build())
        registerModule(module)
    }

    private val customSerializableClasses = mutableSetOf<Class<*>>()
    private val customDeserializableClasses = mutableSetOf<Class<*>>()

    override fun format(data: Any): String {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                mapper.writeValueAsString(data)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T> parse(input: String, clazz: Class<T>): T {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                mapper.readValue(input, clazz)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T> parseList(input: String, clazz: Class<T>): List<T> {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                unmodifiableList(mapper.readValue(
                    input, mapper.typeFactory.constructCollectionType(List::class.java, clazz)
                ))
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <K, V> parseMap(input: String, keyClass: Class<K>, valueClass: Class<V>): Map<K, V> {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                unmodifiableMap(mapper.readValue(
                    input, mapper.typeFactory.constructMapType(LinkedHashMap::class.java, keyClass, valueClass)
                ))
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }
}

class DigitalSignatureAndMetadataDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<DigitalSignatureAndMetadata>() {
        override fun deserialize(
            parser: JsonParser,
            ctxt: DeserializationContext
        ): DigitalSignatureAndMetadata {
            val node = parser.codec.readTree(parser) as JsonNode
            val signature = parser.codec.treeToValue(node.get("signature"), DigitalSignature.WithKeyId::class.java)
            val metadata = parser.codec.treeToValue(node.get("metadata"), DigitalSignatureMetadata::class.java)
            val proof = parser.codec.treeToValue(node.get("proof"), MerkleProof::class.java)
            return DigitalSignatureAndMetadata(signature, metadata, proof)
        }
    }

    class DigitalSignatureWithKeyIdDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<DigitalSignature.WithKeyId>() {
        override fun deserialize(
            parser: JsonParser,
            ctxt: DeserializationContext
        ): DigitalSignature.WithKeyId {
            val node = parser.codec.readTree(parser) as JsonNode
            val by = parser.codec.treeToValue(node.get("by"), SecureHash::class.java)
            val bytes = node.get("bytes").binaryValue()
            return DigitalSignatureWithKeyId(by, bytes)
        }
    }

    class DigitalSignatureMetadataDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<DigitalSignatureMetadata>() {
        override fun deserialize(
            parser: JsonParser,
            ctxt: DeserializationContext
        ): DigitalSignatureMetadata {
            val node = parser.codec.readTree(parser) as JsonNode
            val timestamp = Instant.parse(node.get("timestamp").asText())
            val signatureSpec = parser.codec.treeToValue(node.get("signatureSpec"), SignatureSpec::class.java)
            @Suppress("unchecked_cast")
            val properties = parser.codec.treeToValue(node.get("properties"), Map::class.java).toMutableMap() as MutableMap<String, String>
            return DigitalSignatureMetadata(timestamp, signatureSpec, properties)
        }
    }

    class DigitalSignatureMetadataSerializer : com.fasterxml.jackson.databind.JsonSerializer<DigitalSignatureMetadata>() {
        override fun serialize(
            metadata: DigitalSignatureMetadata,
            generator: JsonGenerator,
            provider: SerializerProvider
        ) {
            generator.writeStartObject()
            generator.writeStringField("timestamp", metadata.timestamp.toString())
            generator.writeObjectField("signatureSpec", metadata.signatureSpec)
            generator.writeObjectField("properties", metadata.properties)
            generator.writeEndObject()
        }
    }

    class SignatureSpecDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<SignatureSpec>() {
        override fun deserialize(
            parser: JsonParser,
            ctxt: DeserializationContext
        ): SignatureSpec {
            val signatureName = "SHA256withECDSA" //TODO use parser.text
            return SignatureSpecImpl(signatureName)
        }
    }

    class MerkleProofDeserializer(private val merkleTreeProofProvider: MerkleTreeProofProvider)
        : com.fasterxml.jackson.databind.JsonDeserializer<MerkleProof>() {
        override fun deserialize(
            parser: JsonParser,
            ctxt: DeserializationContext
        ): MerkleProof {
            val node: JsonNode = parser.codec.readTree(parser)
            val proofType = MerkleProofType.valueOf(node.get("proofType").asText())
            val treeSize = node.get("treeSize").asInt()
            val leavesNode = node.get("leaves")
            val leaves = mutableListOf<IndexedMerkleLeaf>()
            for (leafNode in leavesNode) {
                val leaf = parser.codec.treeToValue(leafNode, IndexedMerkleLeaf::class.java)
                leaves.add(leaf)
            }
            val hashesNode = node.get("hashes")
            val hashes = mutableListOf<SecureHash>()
            for (hashNode in hashesNode) {
                val hash = parser.codec.treeToValue(hashNode, SecureHash::class.java)
                hashes.add(hash)
            }
            return merkleTreeProofProvider.createMerkleProof(proofType, treeSize, leaves, hashes)
        }
    }

    class IndexedMerkleLeafDeserializer(private val merkleTreeProofProvider: MerkleTreeProofProvider)
        : com.fasterxml.jackson.databind.JsonDeserializer<IndexedMerkleLeaf>() {
        override fun deserialize(
            parser: JsonParser,
            ctxt: DeserializationContext
        ): IndexedMerkleLeaf {
            val node: JsonNode = parser.codec.readTree(parser)
            val index = node.get("index").asInt()
            val nonce = if (node.has("nonce")) node.get("nonce").binaryValue() else null
            val leafData = node.get("leafData").binaryValue()

            return merkleTreeProofProvider.createIndexedMerkleLeaf(index, nonce, leafData)
        }
    }