package net.corda.kotlin.reflect.types

import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlinx.metadata.KmType
import kotlinx.metadata.isNullable

class KotlinType(private val kmType: KmType) : KType, KInternal {
    override val annotations: List<Annotation>
        get() = TODO("Not yet implemented")
    override val arguments: List<KTypeProjection>
        get() = TODO("Not yet implemented")
    override val classifier: KClassifier
        get() = TODO("Not yet implemented")
    override val isMarkedNullable: Boolean
        get() = kmType.isNullable

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other !is KotlinType -> false
            else -> kmType == other.kmType
        }
    }

    override fun hashCode(): Int {
        return kmType.hashCode()
    }

    override fun toString(): String {
        return kmType.classifier.toString()
    }
}
