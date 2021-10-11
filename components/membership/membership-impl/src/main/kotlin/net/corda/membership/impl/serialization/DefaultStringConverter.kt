package net.corda.membership.impl.serialization

import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.node.StringObjectConverter
import net.corda.v5.cipher.suite.KeyEncodingService
import java.security.PublicKey
import java.time.Instant

class DefaultStringConverter<T>(private val keyEncodingService: KeyEncodingService) : StringObjectConverter<T> {

    override fun convert(stringProperties: Map<String, String>, clazz: Class<T>): T {
        val value = stringProperties.values.firstOrNull()
        if(value == null) {
            throw IllegalStateException("")
        }
        return when (clazz) {
            Int::class -> value.toInt() as T
            Long::class -> value.toLong() as T
            Short::class -> value.toShort() as T
            Float::class -> value.toFloat() as T
            Double::class -> value.toDouble() as T
            String::class -> value as T
            CordaX500Name::class -> CordaX500Name.parse(value) as T
            PublicKey::class -> keyEncodingService.decodePublicKey(value) as T
            Instant::class -> Instant.parse(value) as T
            else -> throw IllegalStateException("Parsing failed due to unknown ${clazz.name} type.")
        }
    }

}