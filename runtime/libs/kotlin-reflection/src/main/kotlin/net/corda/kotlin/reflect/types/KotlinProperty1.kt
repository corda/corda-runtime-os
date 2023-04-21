package net.corda.kotlin.reflect.types

import kotlinx.metadata.Flag.Function.IS_INFIX
import kotlinx.metadata.Flag.Function.IS_INLINE
import kotlinx.metadata.Flag.Function.IS_OPERATOR
import kotlinx.metadata.Flag.Function.IS_SUSPEND
import kotlinx.metadata.Flag.Property.IS_CONST
import kotlinx.metadata.Flag.Property.IS_EXTERNAL
import kotlinx.metadata.Flag.Property.IS_LATEINIT
import kotlinx.metadata.Flags
import kotlinx.metadata.KmProperty
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.getterSignature
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

@Suppress("LongParameterList")
open class KotlinProperty1<T, V> protected constructor(
    @JvmField
    protected val kmProperty: KmProperty,
    final override val getterSignature: MemberSignature?,
    final override val javaField: Field?,
    final override val javaGetter: Method?,
    @JvmField
    protected val instanceClass: Class<*>
) : KPropertyAccessorInternal<V>, KProperty1<T, V> {
    constructor(property: KmProperty, declaringClass: Class<*>) : this(
        kmProperty = property,
        getterSignature = property.getterSignature?.toSignature(declaringClass.classLoader),
        javaField = null,
        javaGetter = null,
        instanceClass = declaringClass
    )

    override val isAbstract: Boolean
        get() = isAbstract(javaGetter, kmProperty.flags)
    override val isFinal: Boolean
        get() = isFinal(javaGetter, kmProperty.flags)
    final override val isOpen: Boolean
        get() = !isAbstract && !isFinal
    final override val isConst: Boolean
        get() = IS_CONST(kmProperty.flags)
    final override val isLateinit: Boolean
        get() = IS_LATEINIT(kmProperty.flags)
    override val isSuspend: Boolean
        get() = false

    final override val visibility: KVisibility?
        get() = getVisibility(kmProperty.flags, isJava)

    override val annotations: List<Annotation>
        get() = TODO("KotlinProperty1.annotations: Not yet implemented")
    override val name: String
        get() = kmProperty.name
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
        get() = KotlinType(kmProperty.returnType)
    override val typeParameters: List<KTypeParameter>
        get() = kmProperty.typeParameters.map(::KotlinTypeParameter)

    open val isJava: Boolean
        get() = !javaGetter.isKotlin && !javaField.isKotlin

    override fun asPropertyFor(instanceClass: Class<*>) = KotlinProperty1<T, V>(
        kmProperty = kmProperty,
        getterSignature = getterSignature,
        javaField = javaField,
        javaGetter = javaGetter,
        instanceClass = instanceClass
    )

    override fun withJavaGetter(getter: Method) = KotlinProperty1<T, V>(
        kmProperty = kmProperty,
        getterSignature = getter.toSignature(),
        javaField = javaField,
        javaGetter = getter,
        instanceClass = instanceClass
    )

    override fun withJavaField(field: Field) = KotlinProperty1<T, V>(
        kmProperty = kmProperty,
        getterSignature = getterSignature,
        javaField = field,
        javaGetter = javaGetter,
        instanceClass = instanceClass
    )

    final override val fieldSignature: JvmFieldSignature? get() = kmProperty.fieldSignature

    @Suppress("LeakingThis")
    final override val getter: KGetter1Internal<T, V> = Getter(
        name = "<get-${kmProperty.name}>",
        flags = kmProperty.getterFlags,
        property = this,
        javaGetter
    )

    override fun get(receiver: T): V {
        @Suppress("unchecked_cast")
        return javaGetter!!.invoke(receiver) as V
    }

    override fun getDelegate(receiver: T): Any? {
        TODO("KotlinProperty1.getDelegate(): Not yet implemented")
    }

    override fun call(vararg args: Any?): V {
        TODO("KotlinProperty1.call(): Not yet implemented")
    }

    override fun callBy(args: Map<KParameter, Any?>): V {
        TODO("KotlinProperty1.callBy(): Not yet implemented")
    }

    override fun invoke(p1: T): V {
        TODO("KotlinProperty1.invoke(): Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other !is KotlinProperty1<*,*> || other::class != this::class -> false
            else -> name == other.name
                    && javaField == other.javaField
                    && javaGetter == other.javaGetter
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(name, javaField, javaGetter)
    }

    override fun toString(): String {
        return "val $name: $javaField, $javaGetter"
    }

    private data class Getter<T, V>(
        override val name: String,
        private val flags: Flags,
        override val property: KotlinProperty1<T, V>,
        private val javaGetter: Method?
    ) : KGetter1Internal<T, V> {
        override val isAbstract: Boolean
            get() = isAbstract(javaGetter, flags)
        override val isFinal: Boolean
            get() = isFinal(javaGetter, flags)
        override val isOpen: Boolean
            get() = isOpen(javaGetter, flags)
        override val isExternal: Boolean
            get() = IS_EXTERNAL(flags)
        override val isInfix: Boolean
            get() = IS_INFIX(flags)
        override val isInline: Boolean
            get() = IS_INLINE(flags)
        override val isOperator: Boolean
            get() = IS_OPERATOR(flags)
        override val isSuspend: Boolean
            get() = IS_SUSPEND(flags)

        override val annotations: List<Annotation>
            get() = TODO("Not yet implemented")
        override val parameters: List<KParameter>
            get() = javaGetter?.createParameters(property.instanceClass, isExtension = false, emptyList()) ?: emptyList()
        override val returnType: KType
            get() = property.returnType
        override val typeParameters: List<KTypeParameter>
            get() = TODO("Not yet implemented")
        override val visibility: KVisibility?
            get() = getVisibility(flags)

        override val javaMethod: Method?
            get() = javaGetter

        override fun toFunction(): KotlinTransientFunction<V>? {
            return if (javaGetter == null) {
                null
            } else {
                KotlinTransientFunction(
                    name = javaGetter.name,
                    flags = flags,
                    returnType = property.returnType,
                    javaMethod = javaGetter,
                    instanceClass = property.instanceClass,
                    isExtension = false
                )
            }
        }

        override fun call(vararg args: Any?): V {
            TODO("Not yet implemented")
        }

        override fun callBy(args: Map<KParameter, Any?>): V {
            TODO("Not yet implemented")
        }

        override fun invoke(receiver: T): V {
            return property.get(receiver)
        }
    }
}
