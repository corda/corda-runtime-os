package net.corda.serialization

interface InternalCustomSerializer<OBJ, PROXY>  {
    val type: Class<OBJ>
    val proxyType: Class<PROXY>

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

    fun fromProxy(proxy: PROXY, context: SerializationContext): OBJ

    fun toProxy(obj: OBJ, context: SerializationContext): PROXY
}
