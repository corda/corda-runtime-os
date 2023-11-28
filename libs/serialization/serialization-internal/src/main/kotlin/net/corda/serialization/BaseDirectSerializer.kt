package net.corda.serialization

import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject

abstract class BaseDirectSerializer<T : Any> : InternalDirectSerializer<T> {
    protected abstract fun readObject(reader: ReadObject): T
    protected abstract fun writeObject(obj: T, writer: WriteObject)

    override fun readObject(reader: ReadObject, context: SerializationContext): T = readObject(reader)
    override fun writeObject(obj: T, writer: WriteObject, context: SerializationContext) = writeObject(obj, writer)
}
