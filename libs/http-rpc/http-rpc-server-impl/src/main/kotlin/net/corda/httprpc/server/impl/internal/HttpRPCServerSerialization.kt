package net.corda.httprpc.server.impl.internal

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.common.json.serialization.jacksonObjectMapper
import net.corda.httprpc.durablestream.DurableCursorTransferObject
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.base.stream.Cursor
import net.corda.v5.base.stream.PositionManager
import net.corda.v5.crypto.SecureHash

internal val serverJacksonObjectMapper = jacksonObjectMapper().apply {
    val module = SimpleModule()
    module.addSerializer(SecureHash::class.java, SecureHashSerializer)
    module.addSerializer(CordaX500Name::class.java, CordaX500NameSerializer)
    module.setMixInAnnotation(Cursor.PollResult::class.java, PollResultMixin::class.java)
    module.setMixInAnnotation(DurableCursorTransferObject.Companion.InfinitePollResultImpl::class.java, InfinitePollResultMixin::class.java)
    registerModule(module)
    configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true)
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

interface PollResultMixin<T> : Cursor.PollResult<T> {

    @get:JsonIgnore
    override val values: List<T>

    @get:JsonIgnore
    override val firstPosition: Long

    @get:JsonIgnore
    override val lastPosition: Long

    @get:JsonIgnore
    override val isEmpty: Boolean
}

interface InfinitePollResultMixin {

    @get:JsonIgnore
    val isLastResult: Boolean
}