package net.corda.common.identity

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
import net.corda.v5.application.identity.CordaX500Name

object CordaX500NameDeserializer : JsonDeserializer<CordaX500Name>() {
    private val log = LoggerFactory.getLogger(this::class.java)
    override fun deserialize(parser: JsonParser, context: DeserializationContext): CordaX500Name {
        log.trace { "Deserialize." }
        return try {
            CordaX500Name.parse(parser.text)
        } catch (e: IllegalArgumentException) {
            "Invalid Corda X.500 name ${parser.text}: ${e.message}".let {
                log.error(it)
                throw JsonParseException(parser, it, e)
            }
        }.also { log.trace { "Deserialize completed." } }
    }
}