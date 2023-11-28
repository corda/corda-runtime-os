package net.corda.serialization

interface InternalCustomSerializer<T> {
    val type: Class<T>

    /**
     * @property withInheritance Controls whether we serialize only instances
     * of [type], or serialize anything that is assignable to [type].
     */
    val withInheritance: Boolean

    /**
     * @property revealSubclasses Controls whether Corda should include
     * subclasses of [type] in the AMQP schema object. This is only
     * meaningful when [withInheritance] is `true`.
     */
    val revealSubclasses: Boolean get() = false
}
