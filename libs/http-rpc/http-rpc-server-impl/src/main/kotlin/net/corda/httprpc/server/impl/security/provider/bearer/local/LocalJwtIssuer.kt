package net.corda.httprpc.server.impl.security.provider.bearer.local

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.osgi.service.component.annotations.Reference
import java.time.Instant
import java.util.*


class LocalJwtIssuer {

    val key: ECKey = ECKeyGenerator(Curve.P_256)
        .keyID("123")
        .generate()

    fun generateToken(): String {

        val header = JWSHeader.Builder(JWSAlgorithm.ES256K)
            .type(JOSEObjectType.JWT)
            .keyID(key.keyID)
            .build();

        val payload = JWTClaimsSet.Builder()
            .issuer("me")
            .audience("you")
            .subject("bob")
            .expirationTime(Date.from(Instant.now().plusSeconds(120)))
            .build()

        val signedJWT = SignedJWT(header, payload)
        signedJWT.sign(ECDSASigner(key.toECPrivateKey()))
        return signedJWT.serialize()
    }

    fun validateToken(jwt: String): Boolean {
        return SignedJWT.parse(jwt)
            .verify(ECDSAVerifier(key.toECPublicKey()))
    }
}