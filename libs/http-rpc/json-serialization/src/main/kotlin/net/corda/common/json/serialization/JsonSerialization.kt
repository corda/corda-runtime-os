package net.corda.common.json.serialization

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.common.identity.CordaX500NameDeserializer
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.utilities.JsonRepresentable
import net.corda.v5.base.annotations.CordaInternal
import java.util.TimeZone

/**
 * General purpose Jackson Mapper which has sensible security default applied to it.
 */
@CordaInternal
fun jacksonObjectMapper() = ObjectMapper().apply {
    registerModule(KotlinModule())
    registerModule(JavaTimeModule())
    enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
    enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
    setTimeZone(TimeZone.getTimeZone("UTC"))
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    registerModule(with(SimpleModule("Standard types")) {
        addDeserializer(CordaX500Name::class.java, CordaX500NameDeserializer)
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
