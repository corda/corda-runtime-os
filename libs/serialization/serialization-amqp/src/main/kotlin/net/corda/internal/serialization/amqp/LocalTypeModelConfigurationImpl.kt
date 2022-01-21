package net.corda.internal.serialization.amqp

import com.google.common.primitives.Primitives
import net.corda.internal.serialization.model.BaseLocalTypes
import net.corda.internal.serialization.model.LocalTypeModelConfiguration
import org.apache.qpid.proton.amqp.Decimal128
import org.apache.qpid.proton.amqp.Decimal32
import org.apache.qpid.proton.amqp.Decimal64
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.amqp.UnsignedByte
import org.apache.qpid.proton.amqp.UnsignedInteger
import org.apache.qpid.proton.amqp.UnsignedLong
import org.apache.qpid.proton.amqp.UnsignedShort
import java.lang.reflect.Type
import java.util.Date
import java.util.EnumSet
import java.util.UUID

class LocalTypeModelConfigurationImpl(
    private val customSerializerRegistry: CustomSerializerRegistry,
    override val baseTypes: BaseLocalTypes
) : LocalTypeModelConfiguration {
    constructor(customSerializerRegistry: CustomSerializerRegistry)
        : this(customSerializerRegistry, DEFAULT_BASE_TYPES)

    override fun isExcluded(type: Type): Boolean = !hasCordaSerializable(type.asClass())
    override fun isOpaque(type: Type): Boolean = Primitives.unwrap(type.asClass()) in opaqueTypes ||
            customSerializerRegistry.findCustomSerializer(type.asClass(), type) != null
}

// Copied from SerializerFactory so that we can have equivalent behaviour, for now.
private val opaqueTypes = setOf(
        Character::class.java,
        Char::class.java,
        Boolean::class.java,
        Byte::class.java,
        UnsignedByte::class.java,
        Short::class.java,
        UnsignedShort::class.java,
        Int::class.java,
        UnsignedInteger::class.java,
        Long::class.java,
        UnsignedLong::class.java,
        Float::class.java,
        Double::class.java,
        Decimal32::class.java,
        Decimal64::class.java,
        Decimal128::class.java,
        Date::class.java,
        UUID::class.java,
        ByteArray::class.java,
        String::class.java,
        Symbol::class.java
)

@Suppress("unchecked_cast")
private val DEFAULT_BASE_TYPES = BaseLocalTypes(
    collectionClass = Collection::class.java,
    enumSetClass = EnumSet::class.java,
    exceptionClass = Exception::class.java,
    mapClass = Map::class.java,
    stringClass = String::class.java,
    isEnum = Class<*>::isEnum,
    enumConstants = Class<*>::getEnumConstants,
    enumConstantNames = { clazz ->
        (clazz as Class<out Enum<*>>).enumConstants.map(Enum<*>::name)
    }
)
