package net.corda.libs.packaging.internal

import net.corda.crypto.core.parseSecureHash
import net.corda.libs.packaging.CpkDependencyResolver
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.exception.DependencyResolutionException
import net.corda.libs.packaging.hash
import net.corda.libs.packaging.secureHashComparator
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.Collections
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.TreeMap
import java.util.TreeSet

private fun id(name: String,
               version : String,
               signers : NavigableSet<SecureHash> = Collections.emptyNavigableSet()) : CpkIdentifier {
    val signersSummaryHash = hash { md ->
        signers.map(SecureHash::toString)
            .map(String::toByteArray)
            .forEach(md::update)
    }
    return CpkIdentifier(name, version, signersSummaryHash)
}

private fun ids(vararg ids : CpkIdentifier) = ids.toCollection(TreeSet())

private fun signers(vararg publicKey : String) =
    publicKey.mapTo(TreeSet(secureHashComparator)) { parseSecureHash("SHA256:$it") } as NavigableSet<SecureHash>

private fun dependencyMap(vararg pairs : Pair<CpkIdentifier, NavigableSet<CpkIdentifier>>) =
        pairs.associateByTo(TreeMap(),
                Pair<CpkIdentifier, NavigableSet<CpkIdentifier>>::first,
                Pair<CpkIdentifier, NavigableSet<CpkIdentifier>>::second)

private val a_10 = id("a", "1.0", signers("7599dfdec7e313b747878ab589c210d8f8f65f08bbe352de7e4400814efa1217"))
private val b_10 = id("b", "1.0", signers("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
private val b_20 = id("b", "2.0", signers("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
private val c_10 = id("c", "1.0", signers("e14d1f88acc76e9de04af734d1e04d016c8eb0223afc149da9aeb2757f1e79ce"))
private val c_20 = id("c", "2.0", signers("e14d1f88acc76e9de04af734d1e04d016c8eb0223afc149da9aeb2757f1e79ce"))

@Suppress("EnumNaming")
class CpkDependencyResolverTest {

    enum class TestCase(
        val roots : NavigableSet<CpkIdentifier>,
        val availableIds : NavigableMap<CpkIdentifier, NavigableSet<CpkIdentifier>>,
        val expectedResult : NavigableSet<CpkIdentifier>? = null,
        val throwable : Class<out Throwable>? = null,
        val useSignature : Boolean = false
    ) {
        `Required dependencies are pulled in`(
            ids(a_10),
            dependencyMap((a_10 to ids(b_10)), (b_10 to ids(c_10)), (c_10 to ids())),
            ids(a_10, b_10, c_10)
        ),
        `The highest available version is picked when multiple versions are available`(
            ids(a_10),
            dependencyMap((a_10 to ids(b_10)), (b_10 to ids()), (b_20 to ids())),
            ids(a_10, b_20)
        ),
        `A dependency can be satisfied by one with the same name but higher version`(
                ids(a_10),
                dependencyMap((a_10 to ids(b_10)), (b_20 to ids())),
                ids(a_10, b_20)
        ),
        `A dependency cannot be satisfied by one with the same name but lower version`(
                ids(a_10),
                dependencyMap((a_10 to ids(b_20)), (b_10 to ids())),
                throwable = DependencyResolutionException::class.java,
        ),

        `Cyclic dependencies can be resolved seamlessly`(
            ids(a_10),
            dependencyMap((a_10 to ids(b_10)), (b_10 to ids(c_10)), (c_10 to ids(a_10))),
            ids(a_10, b_10, c_10),
        ),

        `Multiple roots are resolved correctly`(
                ids(a_10, b_10),
                dependencyMap((a_10 to ids()), (b_10 to ids(c_10)), (c_10 to ids())),
                ids(a_10, b_10, c_10),
        ),

        `Dependencies are deduplicated`(
            ids(a_10),
            dependencyMap((a_10 to ids(b_10, c_10)), (b_10 to ids(c_20)), (c_20 to ids()), (c_10 to ids())),
            ids(a_10, b_10, c_20),
        ),

        `Root ids are also deduplicated`(
                ids(b_10, b_20),
                dependencyMap((b_20 to ids()), (b_10 to ids(a_10))),
                ids(b_20),
        ),

        `A unsatisfied dependency is an error`(
                ids(a_10),
                dependencyMap((a_10 to ids(b_10))),
                throwable = DependencyResolutionException::class.java
        )
    }

    @ParameterizedTest(name="{0}")
    @EnumSource(value = TestCase::class)
    fun test(case : TestCase) {
        if(case.throwable == null) {
            val actualResult = CpkDependencyResolver.resolveDependencies(case.roots, case.availableIds, case.useSignature)
            Assertions.assertEquals(case.expectedResult, actualResult)
        } else {
            Assertions.assertThrows(case.throwable) {
                CpkDependencyResolver.resolveDependencies(case.roots, case.availableIds, case.useSignature)
            }
        }
    }
}
