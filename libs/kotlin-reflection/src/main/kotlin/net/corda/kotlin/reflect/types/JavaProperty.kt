package net.corda.kotlin.reflect.types

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections.singletonList
import java.util.Objects
import kotlin.reflect.KParameter
import kotlin.reflect.KParameter.Kind.INSTANCE
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType
import kotlinx.metadata.jvm.JvmFieldSignature

open class JavaProperty<T, V>(
    final override val javaField: Field,
    @JvmField
    protected val instanceClass: Class<*>
): KProperty1<T, V>, KPropertyInternal<V> {
    override val annotations: List<Annotation>
        get() = TODO("JavaProperty.annotations: Not yet implemented")
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
        get() = singletonList(
            KotlinParameter(
                name = null,
                type = instanceClass.createKType(isNullable = false),
                index = 0,
                kind = INSTANCE,
                isVararg = false,
                isOptional = false
            )
        )
    override val returnType: KType
        get() = javaField.type.kotlin.starProjectedType
    override val typeParameters: List<KTypeParameter>
        get() = TODO("JavaProperty.typeParameters: Not yet implemented")
    override val visibility: KVisibility?
        get() = getVisibility(javaField)

    override fun asPropertyFor(instanceClass: Class<*>)
        = JavaProperty<T, V>(javaField, instanceClass)

    override val fieldSignature: JvmFieldSignature = javaField.jvmSignature
    override fun withJavaField(field: Field) = JavaProperty<T, V>(field, instanceClass)

    override val javaGetter: Method? = null
    override val getterSignature: MemberSignature? = null
    override fun withJavaGetter(getter: Method): JavaProperty<T, V> = this

    @Suppress("LeakingThis")
    final override val getter: KProperty1.Getter<T, V> = Getter(
        name = "<get-${javaField.name}>", property = this
    )

    override fun get(receiver: T): V {
        @Suppress("unchecked_cast")
        return javaField.get(receiver) as V
    }

    override fun getDelegate(receiver: T): Any? {
        TODO("JavaProperty.getDelegate(): Not yet implemented")
    }
    override fun call(vararg args: Any?): V {
        TODO("JavaProperty.call(): Not yet implemented")
    }

    override fun callBy(args: Map<KParameter, Any?>): V {
        TODO("JavaProperty.callBy(): Not yet implemented")
    }

    override fun invoke(p1: T): V {
        TODO("JavaProperty.invoke(): Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other !is JavaProperty<*,*> || other::class != this::class -> false
            else -> name == other.name && javaField == other.javaField
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(name, javaField)
    }

    override fun toString(): String {
        return "val $name: $javaField"
    }

    private data class Getter<T, V>(
        override val name: String,
        override val property: JavaProperty<T, V>
    ) : KProperty1.Getter<T, V> {
        override val annotations: List<Annotation>
            get() = TODO("JavaProperty.Getter.annotations: Not yet implemented")
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
            get() = emptyList()
        override val returnType: KType
            get() = property.returnType
        override val typeParameters: List<KTypeParameter>
            get() = TODO("JavaProperty.Getter.typeParameters: Not yet implemented")
        override val visibility: KVisibility?
            get() = property.visibility

        override fun call(vararg args: Any?): V {
            TODO("JavaProperty.Getter.call(): Not yet implemented")
        }

        override fun callBy(args: Map<KParameter, Any?>): V {
            TODO("JavaProperty.Getter.callBy(): Not yet implemented")
        }

        override fun invoke(receiver: T): V {
            return property.get(receiver)
        }
    }
}
