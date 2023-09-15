package net.corda.kotlin.reflect.types

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Objects
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty0
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType
import kotlinx.metadata.jvm.JvmFieldSignature

open class JavaStaticProperty<V>(final override val javaField: Field): KProperty0<V>, KPropertyInternal<V> {
    override val annotations: List<Annotation>
        get() = TODO("JavaStaticProperty.annotations: Not yet implemented")
    override val isAbstract: Boolean
        get() = false
    override val isFinal: Boolean
        get() = true
    override val isOpen: Boolean
        get() = false
    override val isConst: Boolean
        get() = isConst(javaField)
    override val isLateinit: Boolean
        get() = false
    override val isSuspend: Boolean
        get() = false

    override val name: String
        get() = javaField.name
    override val parameters: List<KParameter>
        get() = emptyList()
    override val returnType: KType
        get() = javaField.type.kotlin.starProjectedType
    override val typeParameters: List<KTypeParameter>
        get() = TODO("JavaStaticProperty.typeParameters: Not yet implemented")
    override val visibility: KVisibility?
        get() = getVisibility(javaField)

    override fun asPropertyFor(instanceClass: Class<*>) = this

    override val fieldSignature: JvmFieldSignature = javaField.jvmSignature
    override fun withJavaField(field: Field) = JavaStaticProperty<V>(field)

    override val javaGetter: Method? = null
    override val getterSignature: MemberSignature? = null
    override fun withJavaGetter(getter: Method): JavaStaticProperty<V> = this

    @Suppress("LeakingThis")
    final override val getter: KProperty0.Getter<V> = Getter(
        name = "<get-${javaField.name}>", property = this
    )

    override fun get(): V {
        @Suppress("unchecked_cast")
        return javaField.get(null) as V
    }

    override fun getDelegate(): Any? {
        TODO("JavaStaticProperty.getDelegate(): Not yet implemented")
    }
    override fun call(vararg args: Any?): V {
        TODO("JavaStaticProperty.call(): Not yet implemented")
    }

    override fun callBy(args: Map<KParameter, Any?>): V {
        TODO("JavaStaticProperty.callBy(): Not yet implemented")
    }

    override fun invoke(): V {
        TODO("javaStaticProperty.invoke(): Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other !is JavaStaticProperty<*> || this::class != other::class -> false
            else -> name == other.name && javaField == other.javaField
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(name, javaField)
    }

    override fun toString(): String {
        return "val $name: $javaField"
    }

    private data class Getter<V>(
        override val name: String,
        override val property: JavaStaticProperty<V>
    ) : KProperty0.Getter<V> {
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
            get() = TODO("JavaStaticProperty.Getter.annotations: Not yet implemented")
        override val parameters: List<KParameter>
            get() = emptyList()
        override val returnType: KType
            get() = property.returnType
        override val typeParameters: List<KTypeParameter>
            get() = TODO("JavaStaticProperty.Getter.typeParameters: Not yet implemented")
        override val visibility: KVisibility?
            get() = property.visibility

        override fun call(vararg args: Any?): V {
            TODO("JavaStaticProperty.Getter.call(): Not yet implemented")
        }

        override fun callBy(args: Map<KParameter, Any?>): V {
            TODO("JavaStaticProperty.Getter.callBy(): Not yet implemented")
        }

        override fun invoke(): V {
            return property.get()
        }
    }
}
