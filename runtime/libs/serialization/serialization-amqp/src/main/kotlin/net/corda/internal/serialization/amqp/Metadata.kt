package net.corda.internal.serialization.amqp

import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.codec.DescribedTypeConstructor
import java.io.NotSerializableException

/**
 * This class represents metadata information associated with the object being serialised.
 *
 * This class complements the AMQP XML schema to facilitate object serialisation/deserialisation.
 *
 * The [toString] representation generates the associated XML form.
 */
data class Metadata(val values: MutableMap<String, Any> = mutableMapOf()) : DescribedType {
    companion object : DescribedTypeConstructor<Metadata> {

        @JvmField
        val DESCRIPTOR = AMQPDescriptorRegistry.METADATA.amqpDescriptor

        private const val VALUES_IDX = 0

        fun get(obj: Any): Metadata {
            val describedType = obj as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            val list = describedType.described as List<*>
            if (list.size != 1) {
                throw AMQPNoTypeNotSerializableException("Malformed list, bad length of ${list.size} (should be 1)")
            }
            return newInstance(listOf((list[VALUES_IDX])))
        }

        override fun getTypeClass(): Class<*> = Metadata::class.java

        override fun newInstance(described: Any?): Metadata {
            val list = described as? List<*> ?: throw IllegalStateException("Was expecting a list")
            if (list.size != 1) {
                throw AMQPNoTypeNotSerializableException("Malformed list, bad length of ${list.size} (should be 1)")
            }
            @Suppress("unchecked_cast")
            return Metadata(list[VALUES_IDX] as MutableMap<String, Any>)
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(values)

    override fun toString(): String {
        val sb = StringBuilder("<metadata>").append(System.lineSeparator())
        values.forEach {
            sb.append("<key>").append(it.key).append("</key>")
            sb.append(System.lineSeparator())
            sb.append("<value>").append(it.value).append("</value>")
            sb.append(System.lineSeparator())
        }
        sb.append("</metadata>")
        return sb.toString()
    }

    fun getValue(key: Any) = values[key]

    fun isEmpty() = values.isEmpty()

    fun getSize() = values.size

    fun putValue(key: String, value: Any) {
        values[key] = value
    }

    fun clear() = values.clear()

    fun containsKey(key: String) = values.containsKey(key)
}