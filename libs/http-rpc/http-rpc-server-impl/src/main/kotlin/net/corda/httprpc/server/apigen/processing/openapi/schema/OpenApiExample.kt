package net.corda.httprpc.server.apigen.processing.openapi.schema

import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.*
import java.time.ZonedDateTime

private val log = LoggerFactory.getLogger("net.corda.httprpc.server.apigen.processing.openapi.schema.OpenApiExample.kt")
private val dateFormatPattern = "yyyy-MM-dd'T'HH:mm:ss"

@Suppress("ComplexMethod")
@VisibleForTesting
fun Class<*>.toExample(): Any {
    fun List<Class<*>>.anyIsAssignableFrom(clazz: Class<*>) =
            this.any { it.isAssignableFrom(clazz) }

    log.trace { """To example for class: "$this".""" }

    if (this.isEnum && this.enumConstants.isNotEmpty()) {
        //Return the second value if possible as RpcVaultQueryEnums.kt enums have UNKNOWN as the first value
        return if (enumConstants.size > 1) enumConstants[1] else enumConstants.first().also {
            log.trace { """To example for class: "$this" completed. Result: "$it".""" }
        }
    }

    return when {
        listOf(String::class.java).anyIsAssignableFrom(this) -> "string"
        listOf(Int::class.java, Integer::class.java, Long::class.java, Long::class.javaObjectType).anyIsAssignableFrom(this) -> 0
        listOf(Double::class.java, Double::class.javaObjectType, Float::class.java, Float::class.javaObjectType).anyIsAssignableFrom(this) -> 0.0
        listOf(Boolean::class.java, Boolean::class.javaObjectType).anyIsAssignableFrom(this) -> true
        listOf(Instant::class.java).anyIsAssignableFrom(this) -> Instant.now()
        listOf(Date::class.java).anyIsAssignableFrom(this) -> SimpleDateFormat(dateFormatPattern).format(Date())
        listOf(ZonedDateTime::class.java) .anyIsAssignableFrom(this) -> ZonedDateTime.now()
        listOf(TemporalAmount::class.java).anyIsAssignableFrom(this) -> "PT15M" // 15 minutes in ISO-8601
        else -> "No example available for this type"
    }
}