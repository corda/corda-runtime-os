package net.corda.cli.plugin.initialconfig

import javax.persistence.Column
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Table
import javax.persistence.Version
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

/**
 * Create the SQL insert statement to persist an object annotated as javax.persistence.Entity
 * into a database. It assumes that the table name is annotated on the object
 * and that all columns are either annotated as @Column, @JoinColumn or @Version.
 */
fun Any.toInsertStatement(): String {
    val values = this::class.declaredMemberProperties.mapNotNull { field ->
        val columnInfo = getColumnInfo(field) ?: return@mapNotNull null
        field.isAccessible = true
        val value = formatValue(extractValue(field, this, columnInfo.joinColumn)) ?: return@mapNotNull null
        columnInfo.name to value
    }

    return "insert into ${formatTableName(this)} (${values.joinToString { it.first }}) " +
            "values (${values.joinToString { it.second }})"
}

private fun formatValue(value: Any?): String? {
    return when (value) {
        null -> null
        is Short, is Int, is Long, is Float, is Double -> value.toString()
        is Boolean -> value.toString()
        is String -> "'${value.simpleSqlEscaping()}'"
        else -> "'${value.toString().simpleSqlEscaping()}'"
    }
}

private data class ColumnInfo(val name: String, val joinColumn: Boolean)

private fun getColumnInfo(field: KProperty1<out Any, *>): ColumnInfo? {
    field.javaField?.getAnnotation<Column>(Column::class.java)?.name?.let { name ->
        return if (name.isBlank()) {
            ColumnInfo(field.name, false)
        } else
            ColumnInfo(name, false)
    }
    field.javaField?.getAnnotation<JoinColumn>(JoinColumn::class.java)?.name?.let { name ->
        return if (name.isBlank()) {
            ColumnInfo(field.name, true)
        } else
            ColumnInfo(name, true)
    }
    field.javaField?.getAnnotation<Version>(Version::class.java)?.let {
        return ColumnInfo("version", false)
    }
    return null
}

private fun String.simpleSqlEscaping(): String {
    val output = StringBuilder()
    for (c in this) {
        output.append(
            when (c) {
                '\'' -> "\\'"
                '\\' -> "\\\\"
                else -> c
            }
        )
    }
    return output.toString()
}

private fun extractValue(field: KProperty1<out Any?, *>, obj: Any, getId: Boolean): Any? {
    val value = field.getter.call(obj)
    if (!getId || value == null)
        return value
    value::class.declaredMemberProperties.forEach { property ->
        if (property.javaField?.getAnnotation<Id>(Id::class.java) != null) {
            property.isAccessible = true
            return property.getter.call(value)
        }
    }
    throw IllegalArgumentException("Value ${value::class.qualifiedName} for join column does not have a primary key/id column")
}

private fun formatTableName(entity: Any): String {
    val table = entity::class.annotations.find { it is Table } as? Table
        ?: throw IllegalArgumentException("Can't create SQL from ${entity::class.qualifiedName}, it is not a persistence entity")

    val schema = table.schema.let { if (it.isBlank()) "" else "$it." }
    return table.name.let { name ->
        if (name.isBlank())
            "$schema${entity::class.simpleName}"
        else
            "$schema$name"
    }
}
