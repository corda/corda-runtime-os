package net.corda.httprpc.server.impl.utils

import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("net.corda.httprpc.server.utils.StringToPrimitiveMapper.kt")

@Suppress("ComplexMethod")
fun <T> String.mapTo(clazz: Class<T>): T {
    log.trace { """Map string: "$this" to primitive class.""" }
    @Suppress("UNCHECKED_CAST")
    return when (clazz) {
        String::class.java -> uncheckedCast(this)
        Int::class.java -> uncheckedCast(this.toInt())
        Integer::class.java -> uncheckedCast(Integer.parseInt(this))
        Long::class.java, Long::class.javaObjectType -> uncheckedCast(this.toLong())
        Boolean::class.java, Boolean::class.javaObjectType -> uncheckedCast(this.toBoolean())
        Double::class.java, Double::class.javaObjectType -> uncheckedCast(this.toDouble())
        Byte::class.java, Byte::class.javaObjectType -> uncheckedCast(this.toByte())
        Float::class.java, Float::class.javaObjectType -> uncheckedCast(this.toFloat())
        Short::class.java, Short::class.javaObjectType -> uncheckedCast(this.toShort())
        Char::class.java, Char::class.javaObjectType -> uncheckedCast(this.toCharArray().single())
        else -> {
            if (clazz.isEnum) (clazz.enumConstants as Array<Enum<*>>).first { it.name == this } as T
            else throw IllegalArgumentException("Unknown conversion from string to ${clazz.name}")
        }
    }.also { log.trace { """Map string: "$this" to primitive class completed.""" } }

}