package net.corda.httprpc.server.impl.apigen.processing.openapi.schema

import net.corda.utilities.VisibleForTesting
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAmount
import java.util.*
import java.time.ZonedDateTime

private val log = LoggerFactory.getLogger("net.corda.httprpc.server.apigen.processing.openapi.schema.OpenApiExample.kt")
private val instant = Instant.parse("2022-06-24T10:15:30.00Z")
private val dateAsString = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(Date(instant.toEpochMilli()))
private val zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.of("GMT"))

@Suppress("ComplexMethod")
@VisibleForTesting
fun Class<*>.toExample(): Any {
    fun List<Class<*>>.anyIsAssignableFrom(clazz: Class<*>) =
        this.any { it.isAssignableFrom(clazz) }

    log.trace { """To example for class: "$this".""" }

    if (this.isEnum && this.enumConstants.isNotEmpty()) {
        //Return the second value if possible
        return if (enumConstants.size > 1) enumConstants[1] else enumConstants.first().also {
            log.trace { """To example for class: "$this" completed. Result: "$it".""" }
        }
    }

    return when {
        listOf(String::class.java).anyIsAssignableFrom(this) -> "string"
        listOf(Int::class.java, Integer::class.java, Long::class.java, Long::class.javaObjectType).anyIsAssignableFrom(
            this
        ) -> 0
        listOf(
            Double::class.java,
            Double::class.javaObjectType,
            Float::class.java,
            Float::class.javaObjectType
        ).anyIsAssignableFrom(this) -> 0.0
        listOf(Boolean::class.java, Boolean::class.javaObjectType).anyIsAssignableFrom(this) -> true
        listOf(Instant::class.java).anyIsAssignableFrom(this) -> instant
        listOf(Date::class.java).anyIsAssignableFrom(this) -> dateAsString
        listOf(ZonedDateTime::class.java).anyIsAssignableFrom(this) -> zonedDateTime
        listOf(TemporalAmount::class.java).anyIsAssignableFrom(this) -> "PT15M" // 15 minutes in ISO-8601
        else -> "No example available for this type"
    }
}