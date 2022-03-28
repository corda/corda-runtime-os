package net.corda.v5.crypto

import net.corda.v5.crypto.mocks.ECDSA_SECP256K1_SPEC
import net.corda.v5.crypto.mocks.ECDSA_SECP256R1_SPEC
import net.corda.v5.crypto.mocks.EDDSA_ED25519_SPEC
import net.corda.v5.crypto.mocks.RSA_SPEC
import net.corda.v5.crypto.mocks.createDevCertificate
import net.corda.v5.crypto.mocks.decodePublicKey
import net.corda.v5.crypto.mocks.generateKeyPair
import net.corda.v5.crypto.mocks.getDevSigner
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeKeyTests {
    companion object {
        private lateinit var publicKeyRSA: PublicKey
        private lateinit var alicePublicKey: PublicKey
        private lateinit var bobPublicKey: PublicKey
        private lateinit var charliePublicKey: PublicKey
        private lateinit var aliceSignature: DigitalSignature.WithKey
        private lateinit var bobSignature: DigitalSignature.WithKey
        private lateinit var charlieSignature: DigitalSignature.WithKey

        @BeforeAll
        @JvmStatic
        fun setupEach() {
            publicKeyRSA = generateKeyPair(RSA_SPEC).public
            alicePublicKey = generateKeyPair(ECDSA_SECP256K1_SPEC).public
            bobPublicKey = generateKeyPair(ECDSA_SECP256R1_SPEC).public
            charliePublicKey = generateKeyPair(EDDSA_ED25519_SPEC).public
            aliceSignature = DigitalSignature.WithKey(alicePublicKey, ByteArray(5) { 255.toByte() })
            bobSignature = DigitalSignature.WithKey(bobPublicKey, ByteArray(5) { 255.toByte() })
            charlieSignature = DigitalSignature.WithKey(charliePublicKey, ByteArray(5) { 255.toByte() })
        }
    }

    @Test
    fun `(Alice) fulfilled by Alice signature`() {
        assertTrue { alicePublicKey.isFulfilledBy(aliceSignature.by) }
        assertFalse { alicePublicKey.isFulfilledBy(charlieSignature.by) }
    }

    @Test
    fun `(Alice or Bob) fulfilled by either signature`() {
        val aliceOrBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build(threshold = 1)
        assertTrue { aliceOrBob.isFulfilledBy(aliceSignature.by) }
        assertTrue { aliceOrBob.isFulfilledBy(bobSignature.by) }
        assertTrue { aliceOrBob.isFulfilledBy(listOf(aliceSignature.by, bobSignature.by)) }
        assertFalse { aliceOrBob.isFulfilledBy(charlieSignature.by) }
    }

    @Test
    fun `(Alice and Bob) fulfilled by Alice, Bob signatures`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val signatures = listOf(aliceSignature, bobSignature)
        assertTrue { aliceAndBob.isFulfilledBy(signatures.byKeys()) }
    }

    @Test
    fun `(Alice and Bob) requires both signatures to fulfil`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        assertFalse { aliceAndBob.isFulfilledBy(listOf(aliceSignature).byKeys()) }
        assertFalse { aliceAndBob.isFulfilledBy(listOf(bobSignature).byKeys()) }
        assertTrue { aliceAndBob.isFulfilledBy(listOf(aliceSignature, bobSignature).byKeys()) }
    }

    @Test
    fun `((Alice and Bob) or Charlie) signature verifies`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)

        val signatures = listOf(aliceSignature, bobSignature)

        assertTrue { aliceAndBobOrCharlie.isFulfilledBy(signatures.byKeys()) }
    }

    @Test
    fun `der encoded tree decodes correctly`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)
        val encoded = aliceAndBobOrCharlie.encoded
        val decoded = CompositeKey.getInstance(ASN1Primitive.fromByteArray(encoded)) { decodePublicKey(it) }
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @Test
    fun `der encoded tree decodes correctly with weighting`() {
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val aliceAndBobOrCharlie = CompositeKey.Builder()
            .addKey(aliceAndBob, 3)
            .addKey(charliePublicKey, 2)
            .build(threshold = 3)
        val encoded = aliceAndBobOrCharlie.encoded
        val decoded = CompositeKey.getInstance(ASN1Primitive.fromByteArray(encoded)) { decodePublicKey(it) }
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @Test
    fun `tree canonical form`() {
        assertEquals(CompositeKey.Builder().addKeys(alicePublicKey).build(), alicePublicKey)
        val node1 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build(1) // threshold = 1
        val node2 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build(2) // threshold = 2
        assertFalse(node2.isFulfilledBy(alicePublicKey))
        // Ordering by weight.
        val tree1 = CompositeKey.Builder().addKey(node1, 13).addKey(node2, 27).build()
        val tree2 = CompositeKey.Builder().addKey(node2, 27).addKey(node1, 13).build()
        assertEquals(tree1, tree2)
        assertEquals(tree1.hashCode(), tree2.hashCode())

        // Ordering by node, weights the same.
        val tree3 = CompositeKey.Builder().addKeys(node1, node2).build()
        val tree4 = CompositeKey.Builder().addKeys(node2, node1).build()
        assertEquals(tree3, tree4)
        assertEquals(tree3.hashCode(), tree4.hashCode())

        // Duplicate node cases.
        val tree5 = CompositeKey.Builder().addKey(node1, 3).addKey(node1, 14).build()
        val tree6 = CompositeKey.Builder().addKey(node1, 14).addKey(node1, 3).build()
        assertEquals(tree5, tree6)

        // Chain of single nodes should throw.
        assertEquals(CompositeKey.Builder().addKeys(tree1).build(), tree1)
    }

    @Test
    fun `composite key constraints`() {
        // Zero weight.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, 0)
        }
        // Negative weight.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, -1)
        }
        // Zero threshold.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey).build(0)
        }
        // Negative threshold.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey).build(-1)
        }
        // Threshold > Total-weight.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, 2).addKey(bobPublicKey, 2).build(5)
        }
        // Threshold value different than weight of single child node.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, 3).build(2)
        }
        // Aggregated weight integer overflow.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, Int.MAX_VALUE).addKey(bobPublicKey, Int.MAX_VALUE).build()
        }
        // Duplicated children.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey, alicePublicKey).build()
        }
        // Duplicated composite key children.
        assertFailsWith(IllegalArgumentException::class) {
            val compositeKey1 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
            val compositeKey2 = CompositeKey.Builder().addKeys(bobPublicKey, alicePublicKey).build()
            CompositeKey.Builder().addKeys(compositeKey1, compositeKey2).build()
        }
    }

    @Test
    fun `composite key validation`() {
        val key1 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build() as CompositeKey
        val key2 = CompositeKey.Builder().addKeys(alicePublicKey, key1).build() as CompositeKey
        val key3 = CompositeKey.Builder().addKeys(alicePublicKey, key2).build() as CompositeKey
        val key4 = CompositeKey.Builder().addKeys(alicePublicKey, key3).build() as CompositeKey
        val key5 = CompositeKey.Builder().addKeys(alicePublicKey, key4).build() as CompositeKey
        val key6 = CompositeKey.Builder().addKeys(alicePublicKey, key5, key2).build() as CompositeKey

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
        val key1 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build() as CompositeKey
        val key2 = CompositeKey.Builder().addKeys(alicePublicKey, key1).build() as CompositeKey
        val key3 = CompositeKey.Builder().addKeys(alicePublicKey, key2).build() as CompositeKey
        val key4 = CompositeKey.Builder().addKeys(alicePublicKey, key3).build() as CompositeKey
        val key5 = CompositeKey.Builder().addKeys(alicePublicKey, key4).build() as CompositeKey
        val key6 = CompositeKey.Builder().addKeys(alicePublicKey, key5, key2).build() as CompositeKey

        // We will create a graph cycle between key5 and key3. Key5 has already a reference to key3 (via key4).
        // To create a cycle, we add a reference (child) from key3 to key5.
        // Children list is immutable, so reflection is used to inject key5 as an extra NodeAndWeight child of key3.
        CompositeKey::class.java.getDeclaredField("children").apply {
            isAccessible = true
        }.set(key3, key3.children + CompositeKey.NodeAndWeight(key5, 1))

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

        val rsaSignature = DigitalSignature.WithKey(publicKeyRSA, ByteArray(5) { 255.toByte() })
        val k1Signature = DigitalSignature.WithKey(publicKeyK1, ByteArray(5) { 255.toByte() })
        val r1Signature = DigitalSignature.WithKey(publicKeyR1, ByteArray(5) { 255.toByte() })
        val edSignature1 = DigitalSignature.WithKey(publicKeyEd1, ByteArray(5) { 255.toByte() })
        val edSignature2 = DigitalSignature.WithKey(publicKeyEd2, ByteArray(5) { 255.toByte() })

        val compositeKey =
            CompositeKey.Builder().addKeys(publicKeyRSA, publicKeyK1, publicKeyR1, publicKeyEd1, publicKeyEd2).build() as CompositeKey

        val signatures = listOf(rsaSignature, k1Signature, r1Signature, edSignature1, edSignature2)
        assertTrue { compositeKey.isFulfilledBy(signatures.byKeys()) }

        // One signature is missing.
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
        val composite1 = CompositeKey.Builder().addKeys(pub1, pub2, pub3, pub4, pub5, pub6, pub7).build() as CompositeKey
        // Store in reverse order.
        val composite2 = CompositeKey.Builder().addKeys(pub7, pub6, pub5, pub4, pub3, pub2, pub1).build() as CompositeKey
        // There are 7! = 5040 permutations, but as sorting is deterministic the following should never fail.
        assertEquals(composite1.children, composite2.children)
    }

    @Test
    fun `Test save to keystore`() {
        val publicKeyK1 = alicePublicKey
        val publicKeyR1 = bobPublicKey
        val publicKeyEd1 = charliePublicKey
        val publicKeyEd2 = generateKeyPair(EDDSA_ED25519_SPEC).public

        val rsaSignature = DigitalSignature.WithKey(publicKeyRSA, ByteArray(5) { 255.toByte() })
        val k1Signature = DigitalSignature.WithKey(publicKeyK1, ByteArray(5) { 255.toByte() })
        val r1Signature = DigitalSignature.WithKey(publicKeyR1, ByteArray(5) { 255.toByte() })
        val edSignature1 = DigitalSignature.WithKey(publicKeyEd1, ByteArray(5) { 255.toByte() })
        val edSignature2 = DigitalSignature.WithKey(publicKeyEd2, ByteArray(5) { 255.toByte() })

        val compositeKey =
            CompositeKey.Builder().addKeys(publicKeyRSA, publicKeyK1, publicKeyR1, publicKeyEd1, publicKeyEd2).build() as CompositeKey

        val signatures = listOf(rsaSignature, k1Signature, r1Signature, edSignature1, edSignature2)
        assertTrue { compositeKey.isFulfilledBy(signatures.byKeys()) }
        // One signature is missing.
        val signaturesWithoutRSA = listOf(k1Signature, r1Signature, edSignature1, edSignature2)
        assertFalse { compositeKey.isFulfilledBy(signaturesWithoutRSA.byKeys()) }

        val keyAlias = "my-key"
        val pwdArray = "password".toCharArray()

        val keyStoreSave = KeyStore.getInstance("JKS")
        keyStoreSave.load(null, pwdArray)
        val caKeyPair = generateKeyPair(ECDSA_SECP256K1_SPEC)
        val jksFile = ByteArrayOutputStream().use {
            keyStoreSave.setCertificateEntry(
                keyAlias, createDevCertificate(
                    issuer = X500Name("CN=ISSUER, O=o, L=L, ST=il, C=c"),
                    contentSigner = getDevSigner(
                        caKeyPair.private,
                        AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256, SECObjectIdentifiers.secp256k1)
                    ),
                    subject = X500Name("CN=SUBJECT, O=o, L=L, ST=il, C=c"),
                    subjectPublicKey = compositeKey
                )
            )
            keyStoreSave.store(it, pwdArray)
            it.flush()
            it.toByteArray()
        }

        val keyStoreRead = KeyStore.getInstance("JKS")
        val loadedKey = jksFile.inputStream().use {
            keyStoreRead.load(it, pwdArray)
            val encoded = keyStoreRead.getCertificate(keyAlias).publicKey.encoded
            CompositeKey.getInstance(ASN1Primitive.fromByteArray(encoded)) { encodedKey -> decodePublicKey(encodedKey) }
        }

        assertTrue(CompositeKey::class.java.isAssignableFrom(loadedKey::class.java))
        // Run the same composite key test again.
        assertTrue { loadedKey.isFulfilledBy(signatures.byKeys()) }
        assertFalse { loadedKey.isFulfilledBy(signaturesWithoutRSA.byKeys()) }
        // Ensure keys are the same before and after keystore.
        assertEquals(compositeKey, loadedKey)
    }
}
