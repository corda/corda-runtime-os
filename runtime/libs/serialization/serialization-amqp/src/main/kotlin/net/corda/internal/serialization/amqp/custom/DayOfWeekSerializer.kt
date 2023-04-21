package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalDirectSerializer
import java.time.DayOfWeek

class DayOfWeekSerializer : BaseDirectSerializer<DayOfWeek>() {
    override val type: Class<DayOfWeek> get() = DayOfWeek::class.java
    override val withInheritance: Boolean get() = false

    override fun readObject(reader: InternalDirectSerializer.ReadObject): DayOfWeek {
        return DayOfWeek.valueOf(reader.getAs(String::class.java))
    }
    override fun writeObject(obj: DayOfWeek, writer: InternalDirectSerializer.WriteObject) {
        writer.putAsString(obj.name)
    }

}

