package net.corda.kotlin.reflect.types

import kotlinx.metadata.Flag.Function.IS_INFIX
import kotlinx.metadata.Flag.Function.IS_INLINE
import kotlinx.metadata.Flag.Function.IS_OPERATOR
import kotlinx.metadata.Flag.Function.IS_SUSPEND
import kotlinx.metadata.Flag.Property.IS_EXTERNAL
import kotlinx.metadata.Flags
import kotlinx.metadata.KmProperty
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.setterSignature
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Objects
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType

@Suppress("LongParameterList", "MaxLineLength")
class KotlinMutableProperty1<T, V> private constructor(
    property: KmProperty,
    getterSignature: MemberSignature?,
    override val setterSignature: MemberSignature?,
    javaField: Field?,
    javaGetter: Method?,
    override val javaSetter: Method?,
    instanceClass: Class<*>
) : KotlinProperty1<T, V>(property, getterSignature, javaField, javaGetter, instanceClass), KMutableProperty1<T, V>, KMutablePropertyAccessorInternal<V> {
    constructor(property: KmProperty, declaringClass: Class<*>) : this(
        property = property,
        getterSignature = property.getterSignature?.toSignature(declaringClass.classLoader),
        setterSignature = property.setterSignature?.toSignature(declaringClass.classLoader),
        javaField = null,
        javaGetter = null,
        javaSetter = null,
        instanceClass = declaringClass
    )

    override val isAbstract: Boolean
        get() = isAbstract(javaGetter, javaSetter, kmProperty.flags)
    override val isFinal: Boolean
        get() = isFinal(javaGetter, javaSetter, kmProperty.flags)

    override val isJava: Boolean
        get() = super.isJava && !javaSetter.isKotlin

    override fun asPropertyFor(instanceClass: Class<*>) = KotlinMutableProperty1<T, V>(
        property = kmProperty,
        getterSignature = getterSignature,
        setterSignature = setterSignature,
        javaField = javaField,
        javaGetter = javaGetter,
        javaSetter = javaSetter,
        instanceClass = instanceClass
    )

    override fun withJavaAccessors(getter: Method, setter: Method) = KotlinMutableProperty1<T, V>(
        property = kmProperty,
        getterSignature = getter.toSignature(),
        setterSignature = setter.toSignature(),
        javaField = javaField,
        javaGetter = getter,
        javaSetter = setter,
        instanceClass = instanceClass
    )
    override fun withJavaSetter(setter: Method) = KotlinMutableProperty1<T, V>(
        property = kmProperty,
        getterSignature = getterSignature,
        setterSignature = setter.toSignature(),
        javaField = javaField,
        javaGetter = javaGetter,
        javaSetter = setter,
        instanceClass = instanceClass
    )
    override fun withJavaGetter(getter: Method) = KotlinMutableProperty1<T, V>(
        property = kmProperty,
        getterSignature = getter.toSignature(),
        setterSignature = setterSignature,
        javaField = javaField,
        javaGetter = getter,
        javaSetter = javaSetter,
        instanceClass = instanceClass
    )
    override fun withJavaField(field: Field) = KotlinMutableProperty1<T, V>(
        property = kmProperty,
        getterSignature = getterSignature,
        setterSignature = setterSignature,
        javaField = field,
        javaGetter = javaGetter,
        javaSetter = javaSetter,
        instanceClass = instanceClass
    )

    override val setter: KSetter1Internal<T, V> = Setter(
        name = "<set-${kmProperty.name}>",
        flags = kmProperty.setterFlags,
        property = this,
        javaSetter
    )

    override fun set(receiver: T, value: V) {
       javaSetter!!.invoke(receiver, value)
    }

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other !is KotlinMutableProperty1<*,*> || other::class != this::class -> false
            else -> name == other.name
                    && javaField == other.javaField
                    && javaGetter == other.javaGetter
                    && javaSetter == other.javaSetter
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(name, javaField, javaGetter, javaSetter)
    }

    override fun toString(): String {
        return "var $name: $javaField, $javaGetter, $javaSetter"
    }

    private data class Setter<T, V>(
        override val name: String,
        private val flags: Flags,
        override val property: KotlinMutableProperty1<T, V>,
        private val javaSetter: Method?
    ) : KSetter1Internal<T, V> {
        override val isAbstract: Boolean
            get() = isAbstract(javaSetter, flags)
        override val isFinal: Boolean
            get() = isFinal(javaSetter, flags)
        override val isOpen: Boolean
            get() = isOpen(javaSetter, flags)
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
            get() = TODO("KotlinMutableProperty1.Setter.annotations: Not yet implemented")
        override val parameters: List<KParameter>
            get() = javaSetter?.createParameters(
                property.instanceClass,
                isExtension = false,
                kmValueParameters = property.kmProperty.setterValueParameter()
            ) ?: emptyList()
        override val returnType: KType
            get() = Unit::class.starProjectedType
        override val typeParameters: List<KTypeParameter>
            get() = TODO("KotlinMutableProperty1.Setter.typeParameters: Not yet implemented")
        override val visibility: KVisibility?
            get() = getVisibility(flags)

        override val javaMethod: Method?
            get() = javaSetter

        override fun toFunction(): KotlinTransientFunction<Unit>? {
            return if (javaSetter == null) {
                null
            } else {
                KotlinTransientFunction(
                    name = javaSetter.name,
                    flags = flags,
                    returnType = returnType,
                    javaMethod = javaSetter,
                    instanceClass = property.instanceClass,
                    isExtension = false
                )
            }
        }

        override fun call(vararg args: Any?) {
            TODO("KotlinMutableProperty1.Setter.call(): Not yet implemented")
        }

        override fun callBy(args: Map<KParameter, Any?>) {
            TODO("KotlinMutableProperty1.Setter.callBy(): Not yet implemented")
        }

        override fun invoke(receiver: T, value: V) {
            property.set(receiver, value)
        }
    }
}
