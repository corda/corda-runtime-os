package net.corda.rest.server.impl.utils

import net.corda.utilities.trace
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("net.corda.rest.server.utils.StringToPrimitiveMapper.kt")

@Suppress("ComplexMethod")
fun <T> String.mapTo(clazz: Class<T>): T {
    log.trace { """Map string: "$this" to primitive class.""" }
    @Suppress("UNCHECKED_CAST")
    return (when (clazz) {
        String::class.java -> this
        Int::class.java -> this.toInt()
        Integer::class.java -> Integer.parseInt(this)
        Long::class.java, Long::class.javaObjectType -> this.toLong()
        Boolean::class.java, Boolean::class.javaObjectType -> this.toBoolean()
        Double::class.java, Double::class.javaObjectType -> this.toDouble()
        Byte::class.java, Byte::class.javaObjectType -> this.toByte()
        Float::class.java, Float::class.javaObjectType -> this.toFloat()
        Short::class.java, Short::class.javaObjectType -> this.toShort()
        Char::class.java, Char::class.javaObjectType -> this.toCharArray().single()
        else -> {
            if (clazz.isEnum) (clazz.enumConstants as Array<Enum<*>>).first { it.name == this }
            else throw IllegalArgumentException("Unknown conversion from string to ${clazz.name}")
        }
    } as T).also { log.trace { """Map string: "$this" to primitive class completed.""" } }

}