package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.AMQPTypeIdentifierParser
import net.corda.internal.serialization.amqp.PropertyDescriptor
import net.corda.internal.serialization.amqp.asClass
import net.corda.internal.serialization.amqp.propertyDescriptors
import net.corda.v5.serialization.ClassWhitelist
import java.lang.reflect.Type

/**
 * A set of functions in serialization:test that allows testing of serialization internal classes in the serialization-tests project.
 */

const val MAX_TYPE_PARAM_DEPTH = AMQPTypeIdentifierParser.MAX_TYPE_PARAM_DEPTH

fun Class<out Any?>.accessPropertyDescriptors(validateProperties: Boolean = true):
    Map<String, PropertyDescriptor> = propertyDescriptors(validateProperties)
fun Type.accessAsClass(): Class<*> = asClass()
fun <T> ifThrowsAppend(strToAppendFn: () -> String, block: () -> T): T =
    net.corda.internal.serialization.amqp.ifThrowsAppend(strToAppendFn, block)

object AlwaysEmptyWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = false
}

class TestMutableWhiteList : MutableClassWhitelist {

    private var whiteList = mutableSetOf<Class<*>>()

    override fun add(entry: Class<*>) {
        whiteList.add(entry)
    }

    override fun hasListed(type: Class<*>): Boolean = whiteList.contains(type)
}