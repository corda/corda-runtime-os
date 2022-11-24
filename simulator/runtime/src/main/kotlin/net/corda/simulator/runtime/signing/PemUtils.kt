package net.corda.simulator.runtime.signing

import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemReader
import org.bouncycastle.util.io.pem.PemWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

/**
 * A utility function to encode an ECDSA public key into bytes.
 *
 * @param publicKey The key to encode.
 * @return The public key in encoded string form.
 */
fun pemEncode(publicKey: PublicKey): String {
    val pemObject = PemObject("ECDSA PUBLIC KEY", publicKey.encoded)
    val byteStream = ByteArrayOutputStream()
    val pemWriter = PemWriter(OutputStreamWriter(byteStream))
    pemWriter.writeObject(pemObject)
    pemWriter.close();
    return String(byteStream.toByteArray())
}

/**
 * A utility function to decode an ECDSA public key from bytes.
 *
 * @param pemEncodedPublicKey The public key in encoded string form.
 * @return The key.
 */
fun pemDecode(pemEncodedPublicKey: String) : PublicKey {
    val byteStream = ByteArrayInputStream(pemEncodedPublicKey.toByteArray())
    val pemObject = PemReader(InputStreamReader(byteStream)).readPemObject()
    val content = pemObject.content
    val pubKeySpec = X509EncodedKeySpec(content)
    return KeyFactory.getInstance("EC").generatePublic(pubKeySpec) as PublicKey
}
