package net.corda.cli.plugin.initialconfig

import javax.persistence.Column
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Table
import javax.persistence.Version
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

/**
 * Create the SQL insert statement to persist an object annotated as javax.persistence.Entity
 * into a database. It assumes that the table name is annotated on the object
 * and that all columns are either annotated as @Column, @JoinColumn, @Version or @Id.
 */
fun Any.toInsertStatement(): String {
    val values = this::class.declaredMemberProperties.mapNotNull { property ->
        val columnInfo = getColumnInfo(property) ?: return@mapNotNull null
        property.isAccessible = true
        val value = formatValue(extractValue(property, this, columnInfo.joinColumn)) ?: return@mapNotNull null
        columnInfo.name to value
    }

    return "insert into ${formatTableName(this)} (${values.joinToString { it.first }}) " +
            "values (${values.joinToString { it.second }});"
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

private fun getColumnInfo(property: KProperty1<out Any, *>): ColumnInfo? {
    property.getVarAnnotation(Column::class.java)?.name?.let { name ->
        return if (name.isBlank()) {
            ColumnInfo(property.name, false)
        } else {
            ColumnInfo(name, false)
        }
    }
    property.getVarAnnotation(JoinColumn::class.java)?.name?.let { name ->
        return if (name.isBlank()) {
            ColumnInfo(property.name, true)
        } else {
            ColumnInfo(name, true)
        }
    }
    property.getVarAnnotation(Id::class.java)?.let {
        return ColumnInfo(property.name, false)
    }
    return property.getVarAnnotation(Version::class.java)?.let {
        ColumnInfo("version", false)
    }
}

private fun <T : Annotation> KProperty1<*, *>.getVarAnnotation(type: Class<T>): T? {
    return (javaField?.getAnnotation(type) ?: javaGetter?.getAnnotation(type))?.also {
        if (this !is KMutableProperty1<*, *>) {
            throw IllegalArgumentException("Property '$this' must be var for JPA annotations.")
        }
    }
}

private fun String.simpleSqlEscaping(): String {
    val output = StringBuilder()
    var i = 0
    for (c in this) {
        output.append(
            when (c) {
                '\'' -> "\\'"
                // This prevents escaping backslash if it is part of already escaped double quotes
                '\\' -> if (this.length > i+1 && this[i+1] == '\"') {"\\"} else {"\\\\"}
                else -> c
            }
        )
        i++
    }
    return output.toString()
}

private fun extractValue(field: KProperty1<out Any?, *>, obj: Any, getId: Boolean): Any? {
    val value = field.getter.call(obj)
    if (!getId || value == null)
        return value
    value::class.declaredMemberProperties.forEach { property ->
        if (property.getVarAnnotation(Id::class.java) != null) {
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
