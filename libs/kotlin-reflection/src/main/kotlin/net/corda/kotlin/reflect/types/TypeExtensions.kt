@file:JvmName("TypeExtensions")
@file:Suppress("TooManyFunctions")
package net.corda.kotlin.reflect.types

import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Modifier.ABSTRACT
import java.lang.reflect.Modifier.FINAL
import java.lang.reflect.Modifier.PRIVATE
import java.lang.reflect.Modifier.STATIC
import java.util.Collections.singletonList
import java.util.LinkedList
import java.util.function.Predicate
import kotlin.reflect.KCallable
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KParameter.Kind.EXTENSION_RECEIVER
import kotlin.reflect.KParameter.Kind.INSTANCE
import kotlin.reflect.KParameter.Kind.VALUE
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.Modality
import kotlinx.metadata.Visibility
import kotlinx.metadata.isNullable
import kotlinx.metadata.isVar
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmMethodSignature
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.Type

fun getVisibility(visibility: Visibility): KVisibility? {
    return when(visibility) {
        Visibility.PUBLIC -> KVisibility.PUBLIC
        Visibility.PRIVATE -> KVisibility.PRIVATE
        Visibility.PROTECTED -> KVisibility.PROTECTED
        Visibility.INTERNAL -> KVisibility.INTERNAL
        else -> null
    }
}

fun getVisibility(visibility: Visibility, isJavaProperty: Boolean): KVisibility? {
    return if (isJavaProperty && visibility != Visibility.PUBLIC && visibility != Visibility.PRIVATE) {
        null
    } else {
        getVisibility(visibility)
    }
}

fun getVisibility(member: Member): KVisibility? {
    return when {
        Modifier.isPublic(member.modifiers) -> KVisibility.PUBLIC
        Modifier.isPrivate(member.modifiers) -> KVisibility.PRIVATE
        else -> null
    }
}

val Member?.isKotlin: Boolean
    get() {
        return (this ?: return false).declaringClass.isAnnotationPresent(Metadata::class.java)
    }

val Member?.isForInterface: Boolean
    get() {
        return (this ?: return false).declaringClass.isInterface
    }

fun isAbstract(member: Member): Boolean {
    return (member.modifiers and ABSTRACT) != 0
}

fun isAbstract(member: Member?, modality: Modality): Boolean {
    return if (member != null) {
        isAbstract(member)
    } else {
        modality == Modality.ABSTRACT
    }
}

fun isAbstract(member1: Member?, member2: Member?, modality: Modality): Boolean {
    return if (member1 != null && member2 != null && member1.declaringClass === member2.declaringClass) {
        isAbstract(member1) || isAbstract(member2)
    } else {
        modality == Modality.ABSTRACT
    }
}

fun isFinal(member: Member): Boolean {
    return (member.modifiers and FINAL) != 0
}

fun isFinal(member: Member?, modality: Modality): Boolean {
    return if (member != null) {
        isFinal(member)
    } else {
        modality == Modality.FINAL
    }
}

fun isFinal(member1: Member?, member2: Member?, modality: Modality): Boolean {
    return if (member1 != null && member2 != null && member1.declaringClass === member2.declaringClass) {
        isFinal(member1) && isFinal(member2)
    } else {
        modality == Modality.FINAL
    }
}

fun isConst(field: Field): Boolean {
    return (field.modifiers and (STATIC or FINAL)) == (STATIC or FINAL)
            && (field.type.isPrimitive || field.type === String::class.java)
}

fun isOpen(member: Member): Boolean {
    return (member.modifiers and (ABSTRACT or FINAL)) == 0
}

fun isOpen(member: Member?, modality: Modality): Boolean {
    return if (member != null) {
        isOpen(member)
    } else {
        modality == Modality.OPEN
    }
}

fun isInheritable(callable: KCallable<*>): Boolean
        = callable.visibility != null && callable.visibility != KVisibility.PRIVATE
fun isInheritable(member: Member): Boolean
        = (member.modifiers and (PRIVATE or ACC_SYNTHETIC)) == 0

fun isInheritableMember(member: Member) : Boolean
        = (member.modifiers and (STATIC or PRIVATE or ACC_SYNTHETIC)) == 0

fun isStatic(member: Member): Boolean
        = (member.modifiers and (STATIC or ACC_SYNTHETIC)) == STATIC
fun isMember(member: Member): Boolean
        = (member.modifiers and (STATIC or ACC_SYNTHETIC)) == 0

fun isExtension(property: KmProperty): Boolean = property.receiverParameterType != null
fun isExtension(function: KmFunction): Boolean = function.receiverParameterType != null

// Only guaranteed to work with the classloader from the method's declaring class.
fun JvmMethodSignature.toSignature(classLoader: ClassLoader): MemberSignature {
    val methodDescriptor = Type.getMethodType(descriptor)
    return MemberSignature(
        name = name,
        returnType = methodDescriptor.returnType.toClass(classLoader),
        parameterTypes = methodDescriptor.argumentTypes.map { type ->
            type.toClass(classLoader)
        }.toTypedArray()
    )
}

private fun Type.toClass(classLoader: ClassLoader): Class<*> {
    return when(sort) {
        Type.VOID -> Void.TYPE
        Type.INT -> Integer.TYPE
        Type.BOOLEAN -> java.lang.Boolean.TYPE
        Type.BYTE -> java.lang.Byte.TYPE
        Type.LONG -> java.lang.Long.TYPE
        Type.DOUBLE -> java.lang.Double.TYPE
        Type.SHORT -> java.lang.Short.TYPE
        Type.CHAR -> Character.TYPE
        Type.FLOAT -> java.lang.Float.TYPE
        Type.ARRAY ->
            Class.forName("[".repeat(dimensions) + elementType.descriptor.replace('/', '.'), false, classLoader)
        else ->
            Class.forName(className, false, classLoader)
    }
}

fun Method.toSignature(): MemberSignature
    = MemberSignature(name, returnType, parameterTypes)

private val NO_SUCH_MEMBER = MemberSignature("", Nothing::class.java, emptyArray())

val KFunction<*>.signature: MemberSignature
    get() = (this as? KFunctionInternal<*>)?.signature ?: javaMethod?.toSignature() ?: NO_SUCH_MEMBER
val KProperty<*>.getterSignature: MemberSignature
    get() = (this as? KPropertyInternal<*>)?.getterSignature ?: javaGetter?.toSignature() ?: NO_SUCH_MEMBER
val KProperty<*>.nameOnlySignature: MemberSignature
    get() = MemberSignature(name, Any::class.java, emptyArray())

val Field.jvmSignature: JvmFieldSignature
    get() = JvmFieldSignature(name, Type.getDescriptor(type))

@Suppress("ComplexMethod")
fun Method.createParameters(
    instanceClass: Class<*>,
    isExtension: Boolean,
    kmValueParameters: List<KmValueParameter>
): List<KParameter> {
    val kParameters = mutableListOf<KParameter>()
    if (isMember(this)) {
        kParameters.add(KotlinParameter(
            name = null,
            type = instanceClass.createKType(isNullable = false),
            index = 0,
            kind = INSTANCE,
            isVararg = false,
            isOptional = false
        ))
    }
    val offset = kParameters.size
    var valueIdx = kmValueParameters.size - parameterCount
    return parameters.mapIndexedTo(kParameters) { index, parameter ->
        val kValue = kmValueParameters.getOrNull(valueIdx++)
        val paramKind = if (index == 0 && isExtension) {
            EXTENSION_RECEIVER
        } else {
            VALUE
        }
        KotlinParameter(
            name = when {
                kValue != null -> kValue.name
                paramKind == EXTENSION_RECEIVER -> null
                parameter.isNamePresent -> parameter.name
                else -> "arg$index"
            },
            type = parameter.type.createKType(isNullable = kValue?.type?.isNullable ?: false),
            index = index + offset,
            kind = paramKind,
            isVararg = parameter.isVarArgs,
            isOptional = false
        )
    }
}

val Method.receiverType: KType?
    get() {
        return if (parameterCount > 0) {
            parameterTypes[0].createKType(isNullable = false)
        } else {
            null
        }
    }

fun KmProperty.setterValueParameter(): List<KmValueParameter> {
    return setterParameter?.let(::singletonList) ?: emptyList()
}

fun <V> createStaticProperty(field: Field): KProperty0<V> {
    return if (Modifier.isFinal(field.modifiers)) {
        JavaStaticProperty(field)
    } else {
        JavaMutableStaticProperty(field)
    }
}

fun <T, V> Class<*>.createProperty(field: Field): KProperty1<T, V> {
    return if (Modifier.isFinal(field.modifiers)) {
        JavaProperty(field, instanceClass = this)
    } else {
        JavaMutableProperty(field, instanceClass = this)
    }
}

fun <T, V> Class<*>.createKotlinProperty1(kmProperty: KmProperty): KotlinProperty1<T, V> {
    return if (kmProperty.isVar) {
        KotlinMutableProperty1(kmProperty, declaringClass = this)
    } else {
        KotlinProperty1(kmProperty, declaringClass = this)
    }
}

fun <D, E, V> Class<*>.createKotlinProperty2(kmProperty: KmProperty): KotlinProperty2<D, E, V> {
    return if (kmProperty.isVar) {
        KotlinMutableProperty2(kmProperty, declaringClass = this)
    } else {
        KotlinProperty2(kmProperty, declaringClass = this)
    }
}

fun Class<*>.createKType(isNullable: Boolean): KType {
    return kotlin.starProjectedType.withNullability(isNullable)
}

val Class<*>.jvmSuperClasses: MutableList<Class<*>>
    get() {
        val superClasses = LinkedList<Class<*>>()
        var parent = superclass
        while (parent != null && parent !== Any::class.java) {
            superClasses.addFirst(parent)
            parent = parent.superclass
        }
        superClasses.addFirst(Any::class.java)
        return superClasses
    }

val Class<*>.declaredMemberFields: MutableMap<JvmFieldSignature, Field>
    get() = declaredFields.filter(::isMember)
                .associateByTo(HashMap(), Field::jvmSignature)

val Class<*>.declaredMemberMethods: MutableMap<MemberSignature, Method>
    get() = declaredMethods.filter(::isMember)
                .associateByTo(MemberOverrideMap(), Method::toSignature)

val Class<*>.memberMethods: MutableMap<MemberSignature, Method>
    get() = methods.filter(::isMember)
                .associateByTo(MemberOverrideMap(), Method::toSignature)

internal fun <E> MutableCollection<E>.extractFirstBy(predicate: Predicate<E>): E? {
    val iter = iterator()
    while (iter.hasNext()) {
        val item = iter.next()
        if (predicate.test(item)) {
            iter.remove()
            return item
        }
    }
    return null
}

internal fun <K, V> unionOf(first: MutableMap<K, V>, second: MutableMap<K, V>) = UnionMap(first, second)
