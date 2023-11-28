package net.corda.kotlin.reflect.types

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections.singletonList
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KParameter
import kotlin.reflect.KParameter.Kind.VALUE
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType

class JavaMutableStaticProperty<V>(javaField: Field) :
    JavaStaticProperty<V>(javaField), KMutableProperty0<V>, KMutablePropertyInternal<V> {

    override fun toString(): String {
        return "var $name: $javaField"
    }

    override val setterSignature: MemberSignature? get() = null
    override val javaSetter: Method? get() = null

    override fun asPropertyFor(instanceClass: Class<*>) = this
    override fun withJavaAccessors(getter: Method, setter: Method) = this
    override fun withJavaSetter(setter: Method) = this
    override fun withJavaGetter(getter: Method) = this
    override fun withJavaField(field: Field) = JavaMutableStaticProperty<V>(javaField)

    override val setter: KMutableProperty0.Setter<V> = Setter(
        name = "<set-${javaField.name}>",
        property = this
    )

    override fun set(value: V) {
        javaField.set(null, value)
    }

    private data class Setter<V>(
        override val name: String,
        override val property: JavaMutableStaticProperty<V>
    ) : KMutableProperty0.Setter<V> {
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

        override val annotations: List<Annotation>
            get() = TODO("JavaMutableStaticProperty.Setter.annotations: Not yet implemented")
        override val parameters: List<KParameter>
            get() = singletonList(
                KotlinParameter(
                    name = null,
                    type = property.javaField.type.createKType(isNullable = false),
                    index = 0,
                    kind = VALUE,
                    isVararg = false,
                    isOptional = false
                )
            )
        override val returnType: KType
            get() = Unit::class.starProjectedType
        override val typeParameters: List<KTypeParameter>
            get() = TODO("JavaMutableStaticProperty.Setter.typeParameters: Not yet implemented")
        override val visibility: KVisibility?
            get() = property.visibility

        override fun call(vararg args: Any?) {
            TODO("JavaMutableStaticProperty.Setter.call(): Not yet implemented")
        }

        override fun callBy(args: Map<KParameter, Any?>) {
            TODO("JavaMutableStaticProperty.Setter.callBy(): Not yet implemented")
        }

        override fun invoke(value: V) {
            property.set(value)
        }
    }
}
