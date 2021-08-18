package net.corda.kotlin

import java.util.LinkedList

@Suppress("unused")
class KotlinExample : Base(), ExtraApi {
    private val privateField: String = "Hello Example!"

    private fun privateFunction(data: String): String = data

    override val secondVal: String?
        get() = null
    override var secondVar: String? = null

    override fun tell(name: String, message: String?): String {
        return "Say '$message' to $name"
    }

    override val firstVal: String = "Override First Val"
    override var firstVar: String = "Override First Var"

    override val primitiveIntVal: Int = 101

    override fun greet(name: String) {
        println("Hello, $name")
    }

    @Suppress("unused_parameter")
    override var Collection<Any>.apiShow: String
        get() = "Api Show Getter"
        set(value) {}

    override fun ByteArray.testFunc(obj: Any): Boolean = false

    override fun shareApi(item: Any) {
        println("Enjoy $item")
    }

    override fun anything(): LinkedList<Any> {
        return LinkedList()
    }

    override fun anything(index: Int) {
    }

    override fun anything(message: String, vararg params: Any?) = listOf(message)

    override var shareApiProp: String = "Override Property"

    override val String.extraApiExtensionProp: String
        get() = "Api Extension Property"

    override fun Long.extraApiExtensionFunc(data: Any?): Any? = data

    override val baseNullableVal: Any?
        get() = null
    override val protectedBaseNullableVal: Any?
        get() = null

    override fun Any.nullableBaseExtensionFunc(data: String?): String? = data

    override fun String.nonNullableBaseExtensionFunc(data: String): String = data

    @Suppress("unused_parameter")
    override var Any.nullableBaseExtensionProp: String?
        get() = null
        set(value) {}

    @Suppress("unused", "unused_parameter")
    var Double.nullableExtensionProp: Any?
        get() = null
        set(value) {}

    @JvmOverloads
    fun hasOptionalParams(message: String, index: Int = 0, reference: Long = -1) {
        println("Say '$message', index=$index, reference=$reference")
    }
}
