package net.corda.httprpc.server.impl.internal

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.common.json.serialization.jacksonObjectMapper
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash

internal val serverJacksonObjectMapper = jacksonObjectMapper().apply {
    val module = SimpleModule()
    module.addSerializer(SecureHash::class.java, SecureHashSerializer)
    module.addSerializer(MemberX500Name::class.java, MemberX500NameSerializer)
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
