package net.corda.v5.application.persistence.jpa

import java.util.UUID
import javax.persistence.AttributeConverter

/** [AttributeConverter] used to convert JPA Entity String types to DB [UUID] types and vice versa. */
class UUIDConverter : AttributeConverter<UUID, String> {
    override fun convertToDatabaseColumn(uuid: UUID?) = uuid?.toString()
    override fun convertToEntityAttribute(dbData: String?) = if (dbData != null) UUID.fromString(dbData) else null
}