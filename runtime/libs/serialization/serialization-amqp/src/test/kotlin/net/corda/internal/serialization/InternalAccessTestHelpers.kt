package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.AMQPTypeIdentifierParser
import net.corda.internal.serialization.amqp.PropertyDescriptor
import net.corda.internal.serialization.amqp.asClass
import net.corda.internal.serialization.amqp.propertyDescriptors
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
