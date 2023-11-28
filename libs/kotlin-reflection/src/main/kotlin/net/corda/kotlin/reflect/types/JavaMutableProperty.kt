package net.corda.kotlin.reflect.types

import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType

class JavaMutableProperty<T, V>(javaField: Field, instanceClass: Class<*>) :
    JavaProperty<T, V>(javaField, instanceClass), KMutableProperty1<T, V>, KMutablePropertyInternal<V> {

    override fun toString(): String {
        return "var $name: $javaField"
    }

    override fun asPropertyFor(instanceClass: Class<*>) =
        JavaMutableProperty<T, V>(javaField, instanceClass)

    override fun withJavaAccessors(getter: Method, setter: Method) = this
    override fun withJavaSetter(setter: Method) = this
    override fun withJavaGetter(getter: Method) = this
    override fun withJavaField(field: Field) = JavaMutableProperty<T, V>(field, instanceClass)

    override val setterSignature: MemberSignature? get() = null
    override val javaSetter: Method? get() = null

    override val setter: KMutableProperty1.Setter<T, V> = Setter(
        name = "<set-${javaField.name}>",
        property = this
    )

    override fun set(receiver: T, value: V) {
        javaField.set(receiver, value)
    }

    private data class Setter<T, V>(
        override val name: String,
        override val property: JavaMutableProperty<T, V>
    ) : KMutableProperty1.Setter<T, V> {
        override val annotations: List<Annotation>
            get() = TODO("JavaMutableProperty.Setter.annotations: Not yet implemented")
        override val isAbstract: Boolean
            get() = false
        override val isFinal: Boolean
            get() = true
        override val isOpen: Boolean
            get() = false
        override val isExternal: Boolean
            get() = false
        override val isInfix: Boolean
            get() = false
        override val isInline: Boolean
            get() = false
        override val isOperator: Boolean
            get() = false
        override val isSuspend: Boolean
            get() = false

        override val parameters: List<KParameter>
            // Not really correct, but Kotlin Reflection would throw a NPE here!
            get() = emptyList()
        override val returnType: KType
            get() = Unit::class.starProjectedType
        override val typeParameters: List<KTypeParameter>
            get() = TODO("JavaMutableProperty.Setter.typeParameters: Not yet implemented")
        override val visibility: KVisibility?
            get() = property.visibility

        override fun call(vararg args: Any?) {
            TODO("JavaMutableProperty.Setter.call(): Not yet implemented")
        }

        override fun callBy(args: Map<KParameter, Any?>) {
            TODO("JavaMutableProperty.Setter.callBy(): Not yet implemented")
        }

        override fun invoke(receiver: T, value: V) {
            property.set(receiver, value)
        }
    }
}
