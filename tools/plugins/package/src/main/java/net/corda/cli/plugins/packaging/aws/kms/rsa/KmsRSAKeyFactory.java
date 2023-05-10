package net.corda.cli.plugins.packaging.aws.kms.rsa;

import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

public abstract class KmsRSAKeyFactory {

    /**
     * Retrieve KMS KeyPair reference (Private Key / Public Key) based on the keyId informed.
     * Also fetch the real Public Key from KMS.
     * Use only when it is necessary the real Public Key.
     *
     * @param kmsClient
     * @param keyId
     * @return
     */
    public static KeyPair getKeyPair(@NotNull KmsClient kmsClient, @NotNull String keyId) throws NoSuchAlgorithmException, InvalidKeySpecException {
        Objects.requireNonNull(kmsClient);
        Objects.requireNonNull(keyId);
        return new KeyPair(getPublicKey(kmsClient, keyId), getPrivateKey(keyId));
    }

    /**
     * Retrieve KMS KeyPair reference (Private Key / Public Key) based on the keyId informed.
     * The real Public Key is not fetched.
     *
     * @param keyId
     * @return
     */
    public static KeyPair getKeyPair(@NotNull String keyId) {
        Objects.requireNonNull(keyId);
        return new KeyPair(getPublicKey(keyId), getPrivateKey(keyId));
    }

    /**
     * Retrieve KMS Private Key reference based on the keyId informed.
     *
     * @param keyId
     * @return
     */
    public static KmsRSAPrivateKey getPrivateKey(@NotNull String keyId) {
        Objects.requireNonNull(keyId);
        return new KmsRSAPrivateKey(keyId);
    }

    /**
     * Retrieve KMS Public Key reference based on the keyId informed.
     *
     * @param keyId
     * @return
     */
    public static KmsRSAPublicKey getPublicKey(@NotNull String keyId) {
        Objects.requireNonNull(keyId);
        return new KmsRSAPublicKey(keyId, null);
    }

    /**
     * Retrieve KMS Public Key reference based on the keyId informed.
     * Also fetch the real Public Key from KMS.
     * Use only when it is necessary the real Public Key.
     *
     * @param kmsClient
     * @param keyId
     * @return
     */
    public static KmsRSAPublicKey getPublicKey(@NotNull KmsClient kmsClient, @NotNull String keyId) throws NoSuchAlgorithmException, InvalidKeySpecException {
        Objects.requireNonNull(kmsClient);
        Objects.requireNonNull(keyId);
        GetPublicKeyRequest getPublicKeyRequest = GetPublicKeyRequest.builder().keyId(keyId).build();
        GetPublicKeyResponse getPublicKeyResponse = kmsClient.getPublicKey(getPublicKeyRequest);

        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(getPublicKeyResponse.publicKey().asByteArray());
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return new KmsRSAPublicKey(keyId, (RSAPublicKey) keyFactory.generatePublic(keySpec));
    }

}
