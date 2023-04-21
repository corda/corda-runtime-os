package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalDirectSerializer

class UnitSerializer: BaseDirectSerializer<Unit>() {
    override val type: Class<Unit> get() = Unit::class.java
    override val withInheritance: Boolean get() = false

    override fun readObject(reader: InternalDirectSerializer.ReadObject) {
        return Unit
    }

    override fun writeObject(obj: Unit, writer: InternalDirectSerializer.WriteObject) {
        writer.putAsString(obj.toString())
    }
}