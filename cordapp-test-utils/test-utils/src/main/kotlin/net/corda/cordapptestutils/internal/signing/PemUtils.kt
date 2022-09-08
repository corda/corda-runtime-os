package net.corda.cordapptestutils.internal.signing

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


fun pemEncode(publicKey: PublicKey): String {
    val pemObject = PemObject("ECDSA PUBLIC KEY", publicKey.encoded)
    val byteStream = ByteArrayOutputStream()
    val pemWriter = PemWriter(OutputStreamWriter(byteStream))
    pemWriter.writeObject(pemObject)
    pemWriter.close();
    return String(byteStream.toByteArray())
}

fun pemDecode(pemEncodedPublicKey: String) : PublicKey {
    val byteStream = ByteArrayInputStream(pemEncodedPublicKey.toByteArray())
    val pemObject = PemReader(InputStreamReader(byteStream)).readPemObject()
    val content = pemObject.content
    val pubKeySpec = X509EncodedKeySpec(content)
    return KeyFactory.getInstance("EC").generatePublic(pubKeySpec) as PublicKey
}
