package net.corda.common.json.serialization

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.common.identity.MemberX500NameDeserializer
import net.corda.v5.application.utilities.JsonRepresentable
import net.corda.v5.base.annotations.CordaInternal
import net.corda.v5.base.types.MemberX500Name
import java.util.TimeZone

private val jsonMapper = JsonMapper.builder().enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES).build()

/**
 * General purpose Jackson Mapper which has sensible security default applied to it.
 */
@CordaInternal
fun jacksonObjectMapper(): JsonMapper = jsonMapper.apply {
    registerModule(KotlinModule.Builder().build())
    registerModule(JavaTimeModule())
    enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
    setTimeZone(TimeZone.getTimeZone("UTC"))
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    registerModule(with(SimpleModule("Standard types")) {
        addDeserializer(MemberX500Name::class.java, MemberX500NameDeserializer)
        this
    })
}

fun Any.formatAsJson(mapper: ObjectMapper = jacksonObjectMapper()): String {
    return if (this is JsonRepresentable) {
        this.toJsonString()
    } else {
        mapper.writeValueAsString(this)
    }
}
