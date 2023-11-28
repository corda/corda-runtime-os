package net.corda.kotlin.reflect.types

import kotlin.reflect.KParameter
import kotlin.reflect.KParameter.Kind.EXTENSION_RECEIVER
import kotlin.reflect.KParameter.Kind.INSTANCE
import kotlin.reflect.KType

@Suppress("LongParameterList")
data class KotlinParameter(
    override val name: String?,
    override val type: KType,
    override val index: Int,
    override val kind: KParameter.Kind,
    override val isVararg: Boolean,
    override val isOptional: Boolean
) : KParameter, KInternal {
    override val annotations: List<Annotation>
        get() = TODO("KotlinParameter.annotations: Not yet implemented")

    override fun toString(): String {
        return when (kind) {
            INSTANCE -> "instance parameter of ${type.classifier}"
            EXTENSION_RECEIVER -> "extension receiver"
            else -> "parameter #$index: $name"
        }
    }
}
