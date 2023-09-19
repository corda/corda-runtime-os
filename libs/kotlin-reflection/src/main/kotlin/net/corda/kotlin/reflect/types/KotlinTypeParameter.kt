package net.corda.kotlin.reflect.types

import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVariance
import kotlinx.metadata.KmTypeParameter
import kotlinx.metadata.KmVariance
import kotlinx.metadata.isReified

class KotlinTypeParameter(private val kmTypeParameter: KmTypeParameter) : KTypeParameter, KInternal {
    override val isReified: Boolean
        get() = kmTypeParameter.isReified
    override val name: String
        get() = kmTypeParameter.name
    override val upperBounds: List<KType>
        get() = kmTypeParameter.upperBounds.map(::KotlinType)
    override val variance: KVariance
        get() = when (kmTypeParameter.variance) {
            KmVariance.INVARIANT -> KVariance.INVARIANT
            KmVariance.IN -> KVariance.IN
            KmVariance.OUT -> KVariance.OUT
        }

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other !is KotlinTypeParameter -> false
            else -> kmTypeParameter == other.kmTypeParameter
        }
    }

    override fun hashCode(): Int {
        return kmTypeParameter.hashCode()
    }

    override fun toString(): String {
        return kmTypeParameter.name
    }
}
