package net.corda.httprpc.server.impl.internal

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.common.json.serialization.jacksonObjectMapper
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.crypto.SecureHash

internal val serverJacksonObjectMapper = jacksonObjectMapper().apply {
    val module = SimpleModule()
    module.addSerializer(SecureHash::class.java, SecureHashSerializer)
    module.addSerializer(CordaX500Name::class.java, CordaX500NameSerializer)
    registerModule(module)
}

internal object SecureHashSerializer : JsonSerializer<SecureHash>() {
    override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(obj.toString())
    }
}

internal object CordaX500NameSerializer : JsonSerializer<CordaX500Name>() {
    override fun serialize(obj: CordaX500Name, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(obj.toString())
    }
}
