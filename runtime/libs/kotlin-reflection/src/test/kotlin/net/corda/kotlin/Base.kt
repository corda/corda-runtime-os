package net.corda.kotlin

@Suppress("unused", "unused_parameter")
abstract class Base {
    private val privateBaseField: Double = 999.9

    @JvmField
    val baseField: Int = 10

    @JvmField
    protected val protectedBaseField: Long = 1000

    @JvmField
    internal val internalBaseField: String = "Internal"

    abstract val baseNullableVal: Any?
    val baseNonNullableVal: String = "Base Message"

    protected abstract val protectedBaseNullableVal: Any?
    protected open val protectedBaseNonNullableVal: String = "Protected Message"

    internal open val internalBaseNonNullable: String = "Internal Message"

    var String.nonNullableBaseExtensionProp: String
        get() = "Base Extension"
        set(value) {}

    abstract var Any.nullableBaseExtensionProp: String?

    abstract fun Any.nullableBaseExtensionFunc(data: String?): String?
    abstract fun String.nonNullableBaseExtensionFunc(data: String): String

    protected fun protectedBaseFunc() {}
    internal fun internalBaseFunc() {}
    private fun privateBaseFunc() {}
}
