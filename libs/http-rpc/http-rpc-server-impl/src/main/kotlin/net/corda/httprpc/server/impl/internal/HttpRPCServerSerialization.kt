package net.corda.httprpc.server.impl.internal

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.ValueNode
import net.corda.common.json.serialization.jacksonObjectMapper
import net.corda.httprpc.JsonObject
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash

internal val serverJacksonObjectMapper = jacksonObjectMapper().apply {
    val module = SimpleModule()
    module.addSerializer(SecureHash::class.java, SecureHashSerializer)
    module.addSerializer(MemberX500Name::class.java, MemberX500NameSerializer)
    module.addSerializer(JsonObject::class.java, JsonObjectSerializer)
    module.addDeserializer(JsonObject::class.java, JsonObjectDeserializer)
    registerModule(module)
}

internal object SecureHashSerializer : JsonSerializer<SecureHash>() {
    override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(obj.toString())
    }
}

internal object MemberX500NameSerializer : JsonSerializer<MemberX500Name>() {
    override fun serialize(obj: MemberX500Name, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(obj.toString())
    }
}

internal object JsonObjectSerializer : JsonSerializer<JsonObject>() {
    override fun serialize(obj: JsonObject, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(obj.toString())
    }
}

internal object JsonObjectDeserializer : JsonDeserializer<JsonObject>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JsonObject {
        val treeNode = p.readValueAsTree<TreeNode>()
        val str = if (treeNode.isObject) {
            (treeNode as ObjectNode).toString()
        } else if (treeNode.isValueNode) {
            (treeNode as ValueNode).textValue()
        } else if (treeNode.isArray) {
            (treeNode as ArrayNode).toString()
        } else {
            treeNode.toString()
        }
        return JsonObjectAsString(str)
    }
}