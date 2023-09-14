package net.corda.kotlin.reflect.types

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections.unmodifiableList
import java.util.Objects
import kotlin.reflect.KParameter
import kotlin.reflect.KParameter.Kind.EXTENSION_RECEIVER
import kotlin.reflect.KParameter.Kind.INSTANCE
import kotlin.reflect.KProperty2
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmPropertyAccessorAttributes
import kotlinx.metadata.isConst
import kotlinx.metadata.isExternal
import kotlinx.metadata.isInline
import kotlinx.metadata.isLateinit
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.modality
import kotlinx.metadata.visibility

@Suppress("LongParameterList")
open class KotlinProperty2<D, E, V> protected constructor(
    @JvmField
    protected val kmProperty: KmProperty,
    final override val getterSignature: MemberSignature?,
    final override val javaField: Field?,
    final override val javaGetter: Method?,
    @JvmField
    protected val instanceClass: Class<*>
) : KProperty2<D, E, V>, KPropertyAccessorInternal<V> {
    constructor(kmProperty: KmProperty, declaringClass: Class<*>) : this(
        kmProperty = kmProperty,
        getterSignature = kmProperty.getterSignature?.toSignature(declaringClass.classLoader),
        javaField = null,
        javaGetter = null,
        instanceClass = declaringClass
    )

    override val isAbstract: Boolean
        get() = isAbstract(javaGetter, kmProperty.modality)
    override val isFinal: Boolean
        get() = isFinal(javaGetter, kmProperty.modality)
    final override val isOpen: Boolean
        get() = !isAbstract && !isFinal
    final override val isConst: Boolean
        get() = kmProperty.isConst
    final override val isLateinit: Boolean
        get() = kmProperty.isLateinit
    override val isSuspend: Boolean
        get() = false

    final override val visibility: KVisibility?
        get() = getVisibility(kmProperty.visibility, isJava)

    override val annotations: List<Annotation>
        get() = TODO("KotlinProperty2.annotations: Not yet implemented")
    override val name: String
        get() = kmProperty.name
    override val parameters: List<KParameter>
        get() = unmodifiableList(listOf(
            KotlinParameter(
                name = null,
                type = instanceClass.createKType(isNullable = false),
                index = 0,
                kind = INSTANCE,
                isVararg = false,
                isOptional = false
            ),
            KotlinParameter(
                name = null,
                type = javaGetter?.receiverType ?: Any::class.starProjectedType,
                index = 1,
                kind = EXTENSION_RECEIVER,
                isVararg = false,
                isOptional = false
            )
        ))
    override val returnType: KType
        get() = KotlinType(kmProperty.returnType)
    override val typeParameters: List<KTypeParameter>
        get() = kmProperty.typeParameters.map(::KotlinTypeParameter)

    open val isJava: Boolean
        get() = !javaGetter.isKotlin && !javaField.isKotlin

    override fun asPropertyFor(instanceClass: Class<*>) = KotlinProperty2<D, E, V>(
        kmProperty = kmProperty,
        getterSignature = getterSignature,
        javaField = javaField,
        javaGetter = javaGetter,
        instanceClass = instanceClass
    )

    override fun withJavaGetter(getter: Method) = KotlinProperty2<D, E, V>(
        kmProperty = kmProperty,
        getterSignature = getter.toSignature(),
        javaField = javaField,
        javaGetter = getter,
        instanceClass = instanceClass
    )

    override fun withJavaField(field: Field) = KotlinProperty2<D, E, V>(
        kmProperty = kmProperty,
        getterSignature = getterSignature,
        javaField = field,
        javaGetter = javaGetter,
        instanceClass = instanceClass
    )

    final override val fieldSignature: JvmFieldSignature?
        get() = kmProperty.fieldSignature

    @Suppress("LeakingThis")
    final override val getter: KGetter2Internal<D, E, V> = Getter(
        name = "<get-${kmProperty.name}>",
        attributes = kmProperty.getter,
        property = this,
        javaGetter,
    )

    override fun get(receiver1: D, receiver2: E): V {
        @Suppress("unchecked_cast")
        return javaGetter!!.invoke(receiver1, receiver2) as V
    }

    override fun getDelegate(receiver1: D, receiver2: E): Any? {
        TODO("KotlinProperty2.getDelegate(): Not yet implemented")
    }

    override fun call(vararg args: Any?): V {
        TODO("KotlinProperty2.call(): Not yet implemented")
    }

    override fun callBy(args: Map<KParameter, Any?>): V {
        TODO("KotlinProperty2.callBy(): Not yet implemented")
    }

    override fun invoke(p1: D, p2: E): V {
        TODO("KotlinProperty2.invoke(): Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other !is KotlinProperty2<*,*,*> || other::class != this::class -> false
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

    private data class Getter<D, E, V>(
        override val name: String,
        private val attributes: KmPropertyAccessorAttributes,
        override val property: KotlinProperty2<D, E, V>,
        private val javaGetter: Method?
    ) : KGetter2Internal<D, E, V> {
        override val isAbstract: Boolean
            get() = isAbstract(javaGetter, attributes.modality)
        override val isFinal: Boolean
            get() = isFinal(javaGetter, attributes.modality)
        override val isOpen: Boolean
            get() = isOpen(javaGetter, attributes.modality)
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
            get() = TODO("KotlinProperty2.Getter.annotations: Not yet implemented")
        override val parameters: List<KParameter>
            get() = javaGetter?.createParameters(property.instanceClass, isExtension = true, emptyList()) ?: emptyList()
        override val returnType: KType
            get() = property.returnType
        override val typeParameters: List<KTypeParameter>
            get() = TODO("KotlinProperty2.Getter.typeParameters: Not yet implemented")
        override val visibility: KVisibility?
            get() = getVisibility(attributes.visibility)

        override val javaMethod: Method?
            get() = javaGetter

        override fun toFunction(): KotlinTransientFunction<V>? {
            return if (javaGetter == null) {
                null
            } else {
                KotlinTransientFunction(
                    name = javaGetter.name,
                    attributes = attributes,
                    returnType = property.returnType,
                    javaMethod = javaGetter,
                    instanceClass = property.instanceClass,
                    isExtension = true
                )
            }
        }

        override fun call(vararg args: Any?): V {
            TODO("KotlinProperty2.Getter.call(): Not yet implemented")
        }

        override fun callBy(args: Map<KParameter, Any?>): V {
            TODO("KotlinProperty2.Getter.callBy(): Not yet implemented")
        }

        override fun invoke(receiver1: D, receiver2: E): V {
            return property.get(receiver1, receiver2)
        }
    }
}
