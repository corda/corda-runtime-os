package net.corda.crypto.impl

import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.KeyUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Provider
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue


val bouncyCastleProvider: Provider = BouncyCastleProvider()


fun CompositeKeyProviderImpl.create(vararg keys: CompositeKeyNodeAndWeight, threshold: Int? = null) =
    create(keys.toList(), threshold)

class CompositeKeyImplTests {
    companion object {
        private lateinit var publicKeyRSA: PublicKey
        private lateinit var alicePublicKey: PublicKey
        private lateinit var bobPublicKey: PublicKey
        private lateinit var charliePublicKey: PublicKey
        private lateinit var aliceSignature: DigitalSignature.WithKey
        private lateinit var bobSignature: DigitalSignature.WithKey
        private lateinit var charlieSignature: DigitalSignature.WithKey
        private lateinit var target: CompositeKeyProviderImpl

        fun generateKeyPair(spec: KeySpec): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance(spec.name, bouncyCastleProvider)
            if (spec.spec != null) {
                keyPairGenerator.initialize(spec.spec)
            } else if (spec.keyLength != null) {
                keyPairGenerator.initialize(spec.keyLength)
            }
            return keyPairGenerator.generateKeyPair()
        }


        @BeforeAll
        @JvmStatic
        fun setupEach() {
            publicKeyRSA = generateKeyPair(RSA_SPEC).public
            alicePublicKey = generateKeyPair(ECDSA_SECP256K1_SPEC).public
            bobPublicKey = generateKeyPair(ECDSA_SECP256R1_SPEC).public
            charliePublicKey = generateKeyPair(EDDSA_ED25519_SPEC).public
            aliceSignature = DigitalSignature.WithKey(alicePublicKey, ByteArray(5) { 255.toByte() }, emptyMap())
            bobSignature = DigitalSignature.WithKey(bobPublicKey, ByteArray(5) { 255.toByte() }, emptyMap())
            charlieSignature = DigitalSignature.WithKey(charliePublicKey, ByteArray(5) { 255.toByte() }, emptyMap())
            target = CompositeKeyProviderImpl()
        }
    }


    @Test
    fun `(Alice) fulfilled by Alice signature`() {
        assertTrue { KeyUtils.isFulfilledBy(alicePublicKey, aliceSignature.by) }
        assertFalse { KeyUtils.isFulfilledBy(alicePublicKey, charlieSignature.by) }
    }

    @Test
    fun `(Alice or Bob) fulfilled by either signature`() {
        val aliceOrBob = target.createFromKeys(alicePublicKey, bobPublicKey)
        assertTrue { KeyUtils.isFulfilledBy(aliceOrBob, aliceSignature.by) }
        assertTrue { KeyUtils.isFulfilledBy(aliceOrBob, bobSignature.by) }
        assertTrue { KeyUtils.isFulfilledBy(aliceOrBob, listOf(aliceSignature.by, bobSignature.by)) }
        assertFalse { KeyUtils.isFulfilledBy(aliceOrBob, charlieSignature.by) }
    }


    @Test
    fun `(Alice and Bob) fulfilled by Alice, Bob signatures`() {
        val aliceAndBob = target.createFromKeys(alicePublicKey, bobPublicKey)
        val signatures = listOf(aliceSignature, bobSignature)
        assertTrue { KeyUtils.isFulfilledBy(aliceAndBob, signatures.byKeys()) }
    }

    @Test
    fun `(Alice and Bob) requires both signatures to fulfil`() {
        val aliceAndBob = target.createFromKeys(alicePublicKey, bobPublicKey, threshold = null)
        assertFalse { KeyUtils.isFulfilledBy(aliceAndBob, listOf(aliceSignature).byKeys()) }
        assertFalse { KeyUtils.isFulfilledBy(aliceAndBob, listOf(bobSignature).byKeys()) }
        assertTrue { KeyUtils.isFulfilledBy(aliceAndBob, listOf(aliceSignature, bobSignature).byKeys()) }
    }

    @Test
    fun `((Alice and Bob) or Charlie) signature verifies`() {
        val aliceAndBob = target.createFromKeys(alicePublicKey, bobPublicKey)
        val aliceAndBobOrCharlie = target.createFromKeys(listOf(aliceAndBob, charliePublicKey), 1)

        val signatures = listOf(aliceSignature, bobSignature)

        assertTrue { KeyUtils.isFulfilledBy(aliceAndBobOrCharlie, signatures.byKeys()) }
    }
    
    @Test
    fun `tree canonical form`() {
        assertEquals(target.createFromKeys(alicePublicKey), alicePublicKey)
        val node1 = target.createFromKeys(alicePublicKey, bobPublicKey) // threshold = 1
        val node2 = target.createFromKeys(alicePublicKey, bobPublicKey, threshold = 2)
        assertFalse(KeyUtils.isFulfilledBy(node2, alicePublicKey))
        // Ordering by weight.
        val tree1 = target.create(CompositeKeyNodeAndWeight(node1, 13), CompositeKeyNodeAndWeight(node2, 27))
        val tree2 = target.create(CompositeKeyNodeAndWeight(node2, 27), CompositeKeyNodeAndWeight(node1, 13))
        assertEquals(tree1, tree2)
        assertEquals(tree1.hashCode(), tree2.hashCode())
        // Ordering by node, weights the same.

        val tree3 = target.createFromKeys(node1, node2)
        val tree4 = target.createFromKeys(node2, node1)
        assertEquals(tree3, tree4)
        assertEquals(tree3.hashCode(), tree4.hashCode())

        // Duplicate node cases.
        val tree5 = target.create(CompositeKeyNodeAndWeight(node1, 3), CompositeKeyNodeAndWeight(node1, 14))
        val tree6 = target.create(CompositeKeyNodeAndWeight(node1, 14), CompositeKeyNodeAndWeight(node1, 3))
        assertEquals(tree5, tree6)

        // Chain of single nodes should be equivalent to single node.
        assertEquals(target.createFromKeys(tree1), tree1)
    }

    @Test
    fun `composite key constraints`() {
        // Zero weight.
        assertFailsWith(IllegalArgumentException::class) {
            target.create(
                CompositeKeyNodeAndWeight(
                    alicePublicKey,
                    0
                )
            )
        }
        // Negative weight.
        assertFailsWith(IllegalArgumentException::class) {
            target.create(
                CompositeKeyNodeAndWeight(
                    alicePublicKey,
                    -1
                )
            )
        }
        // Zero threshold.
        assertFailsWith(IllegalArgumentException::class) { target.createFromKeys(alicePublicKey, threshold = 0) }
        // Negative threshold.
        assertFailsWith(IllegalArgumentException::class) { target.createFromKeys(alicePublicKey, threshold = -1) }
        // Threshold > Total-weight.
        assertFailsWith(IllegalArgumentException::class) {
            target.create(
                CompositeKeyNodeAndWeight(
                    alicePublicKey,
                    2
                ), CompositeKeyNodeAndWeight(bobPublicKey, 2), threshold = 5
            )
        }
        // Threshold value different than weight of single child node.
        assertFailsWith(IllegalArgumentException::class) {
            target.create(CompositeKeyNodeAndWeight(alicePublicKey, 3), threshold = 2)
        }

        // Aggregated weight integer overflow.
        assertFailsWith(IllegalArgumentException::class) {
            target.create(
                CompositeKeyNodeAndWeight(alicePublicKey, Int.MAX_VALUE),
                CompositeKeyNodeAndWeight(bobPublicKey, Int.MAX_VALUE),
                threshold = null
            )
        }
        // Duplicated children.
        assertFailsWith(IllegalArgumentException::class) {
            target.createFromKeys(alicePublicKey, bobPublicKey, alicePublicKey)
        }
        // Duplicated composite key children.
        assertFailsWith(IllegalArgumentException::class) {
            val compositeKey1 = target.createFromKeys(alicePublicKey, bobPublicKey)
            val compositeKey2 = target.createFromKeys(bobPublicKey, alicePublicKey)
            target.createFromKeys(compositeKey1, compositeKey2)
        }
    }

    @Test
    fun `composite key validation`() {
        val key1 = target.createFromKeys(alicePublicKey, bobPublicKey) as CompositeKey
        val key2 = target.createFromKeys(alicePublicKey, key1) as CompositeKey
        val key3 = target.createFromKeys(alicePublicKey, key2) as CompositeKey
        val key4 = target.createFromKeys(alicePublicKey, key3) as CompositeKey
        val key5 = target.createFromKeys(alicePublicKey, key4) as CompositeKey
        val key6 = target.createFromKeys(alicePublicKey, key5, key2) as CompositeKey

        // Initially, there is no any graph cycle.
        key1.checkValidity()
        key2.checkValidity()
        key3.checkValidity()
        key4.checkValidity()
        key5.checkValidity()
        // The fact that key6 has a direct reference to key2 and an indirect (via path key5->key4->key3->key2)
        // does not imply a cycle, as expected (independent paths).
        key6.checkValidity()
    }

    @Test
    fun `composite key validation with graph cycle detection`() {
        val key1 = target.createFromKeys(alicePublicKey, bobPublicKey) as CompositeKeyImpl
        val key2 = target.createFromKeys(alicePublicKey, key1) as CompositeKeyImpl
        val key3 = target.createFromKeys(alicePublicKey, key2) as CompositeKeyImpl
        val key4 = target.createFromKeys(alicePublicKey, key3) as CompositeKeyImpl
        val key5 = target.createFromKeys(alicePublicKey, key4) as CompositeKeyImpl
        val key6 = target.createFromKeys(alicePublicKey, key5, key2) as CompositeKeyImpl

        // We will create a graph cycle between key5 and key3. Key5 has already a reference to key3 (via key4).
        // To create a cycle, we add a reference (child) from key3 to key5.
        // Children list is immutable, so reflection is used to inject key5 as an extra CompositeKeyNodeAndWeight child of key3.
        CompositeKeyImpl::class.java.getDeclaredField("children").apply {
            isAccessible = true
        }.set(key3, key3.children + CompositeKeyNodeAndWeight(key5, 1))

        /* A view of the example graph cycle.
         *
         *               key6
         *              /    \
         *            key5   key2
         *            /
         *         key4
         *         /
         *       key3
         *      /   \
         *    key2  key5
         *    /
         *  key1
         *
         */

        // Detect the graph cycle starting from key3.
        assertFailsWith(IllegalArgumentException::class) {
            key3.checkValidity()
        }

        // Detect the graph cycle starting from key4.
        assertFailsWith(IllegalArgumentException::class) {
            key4.checkValidity()
        }

        // Detect the graph cycle starting from key5.
        assertFailsWith(IllegalArgumentException::class) {
            key5.checkValidity()
        }

        // Detect the graph cycle starting from key6.
        // Typically, one needs to test on the root tree-node only (thus, a validity check on key6 would be enough).
        assertFailsWith(IllegalArgumentException::class) {
            key6.checkValidity()
        }

        // Key2 (and all paths below it, i.e. key1) are outside the graph cycle and thus, there is no impact on them.
        key2.checkValidity()
        key1.checkValidity()
    }

    @Test
    fun `CompositeKey from multiple signature schemes and signature verification`() {
        val publicKeyK1 = alicePublicKey
        val publicKeyR1 = bobPublicKey
        val publicKeyEd1 = charliePublicKey
        val publicKeyEd2 = generateKeyPair(EDDSA_ED25519_SPEC).public

        val rsaSignature = DigitalSignature.WithKey(publicKeyRSA, ByteArray(5) { 255.toByte() }, emptyMap())
        val k1Signature = DigitalSignature.WithKey(publicKeyK1, ByteArray(5) { 255.toByte() }, emptyMap())
        val r1Signature = DigitalSignature.WithKey(publicKeyR1, ByteArray(5) { 255.toByte() }, emptyMap())
        val edSignature1 = DigitalSignature.WithKey(publicKeyEd1, ByteArray(5) { 255.toByte() }, emptyMap())
        val edSignature2 = DigitalSignature.WithKey(publicKeyEd2, ByteArray(5) { 255.toByte() }, emptyMap())

        val compositeKey = target.createFromKeys(
            publicKeyRSA,
            publicKeyK1,
            publicKeyR1,
            publicKeyEd1,
            publicKeyEd2, threshold = 5
        ) as CompositeKey

        val signatures = listOf(rsaSignature, k1Signature, r1Signature, edSignature1, edSignature2)

        // One signature is missing.
        assertTrue { KeyUtils.isFulfilledBy(compositeKey, signatures.byKeys()) }
        val signaturesWithoutRSA = listOf(k1Signature, r1Signature, edSignature1, edSignature2)
        assertFalse { compositeKey.isFulfilledBy(signaturesWithoutRSA.byKeys()) }
    }

    @Test
    fun `CompositeKey deterministic children sorting`() {
        val pub1 = bobPublicKey
        val pub2 = alicePublicKey
        val pub3 = publicKeyRSA
        val pub4 = charliePublicKey
        val pub5 = generateKeyPair(ECDSA_SECP256R1_SPEC).public
        val pub6 = generateKeyPair(ECDSA_SECP256R1_SPEC).public
        val pub7 = generateKeyPair(ECDSA_SECP256K1_SPEC).public

        // Using default weight = 1, thus all weights are equal.
        val composite1 = target.createFromKeys(pub1, pub2, pub3, pub4, pub5, pub6, pub7) as CompositeKeyImpl
        // Store in reverse order.
        val composite2 = target.createFromKeys(pub7, pub6, pub5, pub4, pub3, pub2, pub1) as CompositeKeyImpl
        // There are 7! = 5040 permutations, but as sorting is deterministic the following should never fail.
        assertEquals(composite1.children, composite2.children)
    }

    internal fun Iterable<DigitalSignature.WithKey>.byKeys() = map { it.by }.toSet()

//    @Test
//    fun `Test save to keystore`() {
//        val publicKeyK1 = alicePublicKey
//        val publicKeyR1 = bobPublicKey
//        val publicKeyEd1 = charliePublicKey
//        val publicKeyEd2 = generateKeyPair(EDDSA_ED25519_SPEC).public
//
//        val rsaSignature = DigitalSignature.WithKey(publicKeyRSA, ByteArray(5) { 255.toByte() }, emptyMap())
//        val k1Signature = DigitalSignature.WithKey(publicKeyK1, ByteArray(5) { 255.toByte() }, emptyMap())
//        val r1Signature = DigitalSignature.WithKey(publicKeyR1, ByteArray(5) { 255.toByte() }, emptyMap())
//        val edSignature1 = DigitalSignature.WithKey(publicKeyEd1, ByteArray(5) { 255.toByte() }, emptyMap())
//        val edSignature2 = DigitalSignature.WithKey(publicKeyEd2, ByteArray(5) { 255.toByte() }, emptyMap())
//
//        val compositeKey = target.createFromKeys(
//            publicKeyRSA,
//            publicKeyK1,
//            publicKeyR1,
//            publicKeyEd1,
//            publicKeyEd2, threshold = 5
//        ) as CompositeKeyImpl
//
//        val signatures = listOf(rsaSignature, k1Signature, r1Signature, edSignature1, edSignature2)
//        assertTrue { compositeKey.isFulfilledBy(signatures.byKeys()) }
//        // One signature is missing.
//        val signaturesWithoutRSA = listOf(k1Signature, r1Signature, edSignature1, edSignature2)
//        assertFalse { compositeKey.isFulfilledBy(signaturesWithoutRSA.byKeys()) }
//
//        val keyAlias = "my-key"
//        val pwdArray = "password".toCharArray()
//
//        val keyStoreSave = KeyStore.getInstance("JKS")
//        keyStoreSave.load(null, pwdArray)
//        val caKeyPair = generateKeyPair(ECDSA_SECP256K1_SPEC)
//        val jksFile = ByteArrayOutputStream().use {
//            keyStoreSave.setCertificateEntry(
//                keyAlias, createDevCertificate(
//                    issuer = X500Name("CN=ISSUER, O=o, L=L, ST=il, C=c"),
//                    contentSigner = getDevSigner(
//                        caKeyPair.private,
//                        AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256, SECObjectIdentifiers.secp256k1)
//                    ),
//                    subject = X500Name("CN=SUBJECT, O=o, L=L, ST=il, C=c"),
//                    subjectPublicKey = compositeKey
//                )
//            )
//            keyStoreSave.store(it, pwdArray)
//            it.flush()
//            it.toByteArray()
//        }
//
//        val keyStoreRead = KeyStore.getInstance("JKS")
//        val loadedKey = jksFile.inputStream().use {
//            keyStoreRead.load(it, pwdArray)
//            val encoded = keyStoreRead.getCertificate(keyAlias).publicKey.encoded
//            target.createFromASN1(encoded)
//        }
//
//        assertTrue(CompositeKey::class.java.isAssignableFrom(loadedKey::class.java))
//        // Run the same composite key test again.
//        assertTrue { loadedKey.isFulfilledBy(signatures.byKeys()) }
//        assertFalse { loadedKey.isFulfilledBy(signaturesWithoutRSA.byKeys()) }
//        // Ensure keys are the same before and after keystore.
//        assertEquals(compositeKey, loadedKey)
//    }
}
