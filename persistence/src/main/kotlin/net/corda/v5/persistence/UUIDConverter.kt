package net.corda.v5.persistence

import java.util.UUID
import javax.persistence.AttributeConverter

/** Used to convert to and from [UUID]s when defining [MappedSchema] entity columns. */
object UUIDConverter : AttributeConverter<UUID, String> {
    override fun convertToDatabaseColumn(uuid: UUID?) = uuid?.toString()
    override fun convertToEntityAttribute(dbData: String?) = if (dbData != null) UUID.fromString(dbData) else null
}