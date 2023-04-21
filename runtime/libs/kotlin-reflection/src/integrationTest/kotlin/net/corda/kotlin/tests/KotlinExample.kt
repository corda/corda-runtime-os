package net.corda.kotlin.tests

import net.corda.kotlin.test.example.ExtendedKotlinApi
import net.corda.kotlin.test.example.KotlinBase
import net.corda.kotlin.test.example.SampleAnnotation
import java.util.LinkedList

@SampleAnnotation("Kotlin Example")
@Suppress("unused")
class KotlinExample : KotlinBase(), ExtendedKotlinApi {
    override val nativeLong: Long
        get() = -1
    override val nullableString: String?
        get() = null
    override val nonNullableString: String
        get() = "I am not null!"
    override val neverNull: String
        get() = "Hello World!"
    override val listOfItems: List<String>
        get() = emptyList()

    override fun anything(): LinkedList<Any> {
        return LinkedList()
    }

    private var privateVar: String = "Secret"
}
