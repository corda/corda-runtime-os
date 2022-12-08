@file:JvmName("SignatureSpecEquality")

package net.corda.crypto.cipher.suite

import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.SignatureSpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.util.function.BiPredicate

private val comparators = mapOf<Class<*>, BiPredicate<Any, Any>>(
    PSSParameterSpec::class.java to BiPredicate { left, right ->
        left as PSSParameterSpec
        right as PSSParameterSpec
        if (left.mgfParameters !is MGF1ParameterSpec || right.mgfParameters !is MGF1ParameterSpec) {
            false
        } else {
            val leftMgf = left.mgfParameters as MGF1ParameterSpec
            val rightMgf = right.mgfParameters as MGF1ParameterSpec
            left.digestAlgorithm.equals(right.digestAlgorithm, true) &&
                    left.mgfAlgorithm.equals(right.mgfAlgorithm, true) &&
                    left.saltLength == right.saltLength &&
                    left.trailerField == right.trailerField &&
                    leftMgf.digestAlgorithm.equals(rightMgf.digestAlgorithm, true)
        }
    }
)

/**
 * Compares two instances of the [SignatureSpec]
 *
 * @return True if the instances are describing the same specification or of both values are null.
 */
@Suppress("ComplexMethod")
fun SignatureSpec?.equal(right: SignatureSpec?): Boolean =
    if (this == null) {
        right == null
    } else if(right == null) {
        false
    } else if( this === right) {
        true
    } else if (this::class.java != right::class.java ) {
        false
    } else if(!signatureName.equals(right.signatureName, true)) {
        false
    } else {
        when(this) {
            is CustomSignatureSpec -> {
                right as CustomSignatureSpec
                customDigestName == right.customDigestName && params.paramsAreEqual(right.params)
            }
            is ParameterizedSignatureSpec -> {
                right as ParameterizedSignatureSpec
                params.paramsAreEqual(right.params)
            }
            else -> true
        }
    }

private fun Any?.paramsAreEqual(right: Any?): Boolean =
    if (this == null) {
        right == null
    } else if(right == null) {
        false
    } else if (this::class.java != right::class.java) {
        false
    } else {
        comparators[this::class.java]?.test(this, right) ?: false
    }
