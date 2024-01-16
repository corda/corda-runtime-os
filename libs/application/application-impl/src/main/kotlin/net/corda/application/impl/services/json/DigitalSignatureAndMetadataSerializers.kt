package net.corda.application.impl.services.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.cipher.suite.merkle.MerkleProofProvider
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import java.time.Instant

class DigitalSignatureAndMetadataSerialisationModule(private val merkleProofProvider: MerkleProofProvider) {
    val module = SimpleModule().apply {
        addSerializer(DigitalSignatureAndMetadata::class.java, DigitalSignatureAndMetadataSerializer())
        addDeserializer(DigitalSignatureAndMetadata::class.java, DigitalSignatureAndMetadataDeserializer())
        addSerializer(DigitalSignatureMetadata::class.java, DigitalSignatureMetadataSerializer())
        addDeserializer(DigitalSignatureMetadata::class.java, DigitalSignatureMetadataDeserializer())
        addSerializer(DigitalSignature.WithKeyId::class.java, DigitalSignatureWithKeyIdSerializer())
        addDeserializer(DigitalSignature.WithKeyId::class.java, DigitalSignatureWithKeyIdDeserializer())
        addSerializer(SignatureSpec::class.java, SignatureSpecSerializer())
        addDeserializer(SignatureSpec::class.java, SignatureSpecDeserializer())
        addSerializer(MerkleProof::class.java, MerkleProofSerializer())
        addDeserializer(MerkleProof::class.java, MerkleProofDeserializer(merkleProofProvider))
        addSerializer(IndexedMerkleLeaf::class.java, IndexedMerkleLeafSerializer())
        addDeserializer(IndexedMerkleLeaf::class.java, IndexedMerkleLeafDeserializer(merkleProofProvider))
    }
}

class IndexedMerkleLeafSerializer : JsonSerializer<IndexedMerkleLeaf>() {
    override fun serialize(
        leaf: IndexedMerkleLeaf,
        gen: JsonGenerator,
        serializers: SerializerProvider
    ) {
        gen.writeStartObject()
        gen.writeObjectField("index", leaf.index)
        gen.writeObjectField("nonce", leaf.nonce)
        gen.writeObjectField("leafData", leaf.leafData)
        gen.writeEndObject()
    }
}

class IndexedMerkleLeafDeserializer(private val merkleProofProvider: MerkleProofProvider) :
    JsonDeserializer<IndexedMerkleLeaf>() {
    override fun deserialize(
        parser: JsonParser,
        ctxt: DeserializationContext
    ): IndexedMerkleLeaf {
        val node: JsonNode = parser.codec.readTree(parser)
        val index = node.get("index").asInt()
        val nonce = if (node.has("nonce")) node.get("nonce").binaryValue() else null
        val leafData = node.get("leafData").binaryValue()
        return merkleProofProvider.createIndexedMerkleLeaf(index, nonce, leafData)
    }
}

class MerkleProofSerializer : JsonSerializer<MerkleProof>() {
    override fun serialize(
        merkleProof: MerkleProof,
        generator: JsonGenerator,
        provider: SerializerProvider
    ) {
        generator.writeStartObject()
        generator.writeStringField("proofType", merkleProof.proofType.name)
        generator.writeNumberField("treeSize", merkleProof.treeSize)
        generator.writeArrayFieldStart("leaves")
        for (leaf in merkleProof.leaves) {
            generator.writeObject(leaf)
        }
        generator.writeEndArray()
        generator.writeArrayFieldStart("hashes")
        for (hash in merkleProof.hashes) {
            generator.writeObject(hash)
        }
        generator.writeEndArray()
        generator.writeEndObject()
    }
}

class MerkleProofDeserializer(private val merkleProofProvider: MerkleProofProvider) :
    JsonDeserializer<MerkleProof>() {
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
        return merkleProofProvider.createMerkleProof(proofType, treeSize, leaves, hashes)
    }
}

class SignatureSpecSerializer : JsonSerializer<SignatureSpec>() {
    override fun serialize(
        spec: SignatureSpec,
        generator: JsonGenerator,
        provider: SerializerProvider
    ) {
        generator.writeString(spec.signatureName)
    }
}

class SignatureSpecDeserializer : JsonDeserializer<SignatureSpec>() {
    override fun deserialize(
        parser: JsonParser,
        ctxt: DeserializationContext
    ): SignatureSpec {
        val signatureName = parser.text
        return SignatureSpecImpl(signatureName)
    }
}

class DigitalSignatureMetadataSerializer : JsonSerializer<DigitalSignatureMetadata>() {
    override fun serialize(
        metadata: DigitalSignatureMetadata,
        generator: JsonGenerator,
        provider: SerializerProvider
    ) {
        generator.writeStartObject()
        generator.writeStringField("timestamp", metadata.timestamp.toString())
        generator.writeObjectField("signatureSpec", metadata.signatureSpec)
        generator.writeObjectField("properties", metadata.properties.toSortedMap())
        generator.writeEndObject()
    }
}

class DigitalSignatureMetadataDeserializer : JsonDeserializer<DigitalSignatureMetadata>() {
    override fun deserialize(
        parser: JsonParser,
        ctxt: DeserializationContext
    ): DigitalSignatureMetadata {
        val node = parser.codec.readTree(parser) as JsonNode
        val timestamp = Instant.parse(node.get("timestamp").asText())
        val signatureSpec = parser.codec.treeToValue(node.get("signatureSpec"), SignatureSpec::class.java)
        @Suppress("unchecked_cast")
        val properties = (parser.codec.treeToValue(node.get("properties"), Map::class.java) as Map<String, String>)
            .toSortedMap()
        return DigitalSignatureMetadata(timestamp, signatureSpec, properties)
    }
}

class DigitalSignatureAndMetadataSerializer : JsonSerializer<DigitalSignatureAndMetadata>() {
    override fun serialize(
        signatureAndMetadata: DigitalSignatureAndMetadata,
        generator: JsonGenerator,
        provider: SerializerProvider
    ) {
        generator.writeStartObject()
        generator.writeObjectField("signature", signatureAndMetadata.signature)
        generator.writeObjectField("metadata", signatureAndMetadata.metadata)
        generator.writeObjectField("proof", signatureAndMetadata.proof)
        generator.writeEndObject()
    }
}

class DigitalSignatureAndMetadataDeserializer : JsonDeserializer<DigitalSignatureAndMetadata>() {
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

class DigitalSignatureWithKeyIdSerializer : JsonSerializer<DigitalSignature.WithKeyId>() {
    override fun serialize(
        signature: DigitalSignature.WithKeyId,
        generator: JsonGenerator,
        provider: SerializerProvider
    ) {
        generator.writeStartObject()
        val signatureBy = signature.by
        generator.writeObjectField("by", signatureBy)
        generator.writeFieldName("bytes")
        generator.writeBinary(signature.bytes)
        generator.writeEndObject()
    }
}

class DigitalSignatureWithKeyIdDeserializer : JsonDeserializer<DigitalSignature.WithKeyId>() {
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
