package net.corda.kotlin.reflect.types

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Objects
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmPropertyAccessorAttributes
import kotlinx.metadata.isExternal
import kotlinx.metadata.isInline
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.setterSignature
import kotlinx.metadata.modality
import kotlinx.metadata.visibility

@Suppress("LongParameterList", "MaxLineLength")
class KotlinMutableProperty1<T, V> private constructor(
    kmProperty: KmProperty,
    getterSignature: MemberSignature?,
    override val setterSignature: MemberSignature?,
    javaField: Field?,
    javaGetter: Method?,
    override val javaSetter: Method?,
    instanceClass: Class<*>
) : KotlinProperty1<T, V>(kmProperty, getterSignature, javaField, javaGetter, instanceClass), KMutableProperty1<T, V>, KMutablePropertyAccessorInternal<V> {
    constructor(kmProperty: KmProperty, declaringClass: Class<*>) : this(
        kmProperty = kmProperty,
        getterSignature = kmProperty.getterSignature?.toSignature(declaringClass.classLoader),
        setterSignature = kmProperty.setterSignature?.toSignature(declaringClass.classLoader),
        javaField = null,
        javaGetter = null,
        javaSetter = null,
        instanceClass = declaringClass
    )

    override val isAbstract: Boolean
        get() = isAbstract(javaGetter, javaSetter, kmProperty.setter!!.modality)
    override val isFinal: Boolean
        get() = isFinal(javaGetter, javaSetter, kmProperty.setter!!.modality)

    override val isJava: Boolean
        get() = super.isJava && !javaSetter.isKotlin

    override fun asPropertyFor(instanceClass: Class<*>) = KotlinMutableProperty1<T, V>(
        kmProperty = kmProperty,
        getterSignature = getterSignature,
        setterSignature = setterSignature,
        javaField = javaField,
        javaGetter = javaGetter,
        javaSetter = javaSetter,
        instanceClass = instanceClass
    )

    override fun withJavaAccessors(getter: Method, setter: Method) = KotlinMutableProperty1<T, V>(
        kmProperty = kmProperty,
        getterSignature = getter.toSignature(),
        setterSignature = setter.toSignature(),
        javaField = javaField,
        javaGetter = getter,
        javaSetter = setter,
        instanceClass = instanceClass
    )
    override fun withJavaSetter(setter: Method) = KotlinMutableProperty1<T, V>(
        kmProperty = kmProperty,
        getterSignature = getterSignature,
        setterSignature = setter.toSignature(),
        javaField = javaField,
        javaGetter = javaGetter,
        javaSetter = setter,
        instanceClass = instanceClass
    )
    override fun withJavaGetter(getter: Method) = KotlinMutableProperty1<T, V>(
        kmProperty = kmProperty,
        getterSignature = getter.toSignature(),
        setterSignature = setterSignature,
        javaField = javaField,
        javaGetter = getter,
        javaSetter = javaSetter,
        instanceClass = instanceClass
    )
    override fun withJavaField(field: Field) = KotlinMutableProperty1<T, V>(
        kmProperty = kmProperty,
        getterSignature = getterSignature,
        setterSignature = setterSignature,
        javaField = field,
        javaGetter = javaGetter,
        javaSetter = javaSetter,
        instanceClass = instanceClass
    )

    override val setter: KSetter1Internal<T, V> = Setter(
        name = "<set-${kmProperty.name}>",
        attributes = kmProperty.setter!!,
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
        private val attributes: KmPropertyAccessorAttributes,
        override val property: KotlinMutableProperty1<T, V>,
        private val javaSetter: Method?
    ) : KSetter1Internal<T, V> {
        override val isAbstract: Boolean
            get() = isAbstract(javaSetter, attributes.modality)
        override val isFinal: Boolean
            get() = isFinal(javaSetter, attributes.modality)
        override val isOpen: Boolean
            get() = isOpen(javaSetter, attributes.modality)
        override val isExternal: Boolean
            get() = attributes.isExternal
        override val isInfix: Boolean
            get() = false
        override val isInline: Boolean
            get() = attributes.isInline
        override val isOperator: Boolean
            get() = false
        override val isSuspend: Boolean
            get() = false

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
            get() = getVisibility(attributes.visibility)

        override val javaMethod: Method?
            get() = javaSetter

        override fun toFunction(): KotlinTransientFunction<Unit>? {
            return if (javaSetter == null) {
                null
            } else {
                KotlinTransientFunction(
                    name = javaSetter.name,
                    attributes = attributes,
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
