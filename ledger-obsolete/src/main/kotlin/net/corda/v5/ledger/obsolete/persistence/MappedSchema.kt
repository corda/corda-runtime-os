package net.corda.v5.ledger.obsolete.persistence

/**
 * NOTE: MOVED THIS HERE AS THIS CLASS IS NOW REDUNDANT IN THE API. (CORE-6192)
 *
 *
 * A database schema that might be configured for this node.  As well as a name and version for identifying the schema,
 * also list the classes that may be used in the generated object graph in order to configure the ORM tool.
 *
 * @param schemaFamily A class to fully qualify the name of a schema family (i.e. excludes version)
 * @property version The version number of this instance within the family.
 * @property mappedTypes The JPA entity classes that the ORM layer needs to be configure with for this schema.
 */
open class MappedSchema(
    schemaFamily: Class<*>,
    val version: Int,
    val mappedTypes: Iterable<Class<*>>
) {
    val name: String = schemaFamily.name

    /**
     * Optional classpath resource containing the database changes for the [mappedTypes]
     */
    open val migrationResource: String? = null

    override fun toString(): String = "${this.javaClass.simpleName}(name=$name, version=$version)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MappedSchema

        if (version != other.version) return false
        if (mappedTypes != other.mappedTypes) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + mappedTypes.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}
