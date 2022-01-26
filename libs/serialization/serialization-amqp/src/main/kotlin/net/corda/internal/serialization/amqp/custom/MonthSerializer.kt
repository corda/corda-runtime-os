package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalDirectSerializer
import java.time.Month

class MonthSerializer : BaseDirectSerializer<Month>() {
    override val type: Class<Month> get() = Month::class.java
    override val withInheritance: Boolean get() = false

    override fun readObject(reader: InternalDirectSerializer.ReadObject): Month {
        return Month.valueOf(reader.getAs(String::class.java))
    }
    override fun writeObject(obj: Month, writer: InternalDirectSerializer.WriteObject) {
        writer.putAsString(obj.name)
    }
}

