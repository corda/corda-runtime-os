package software.amazon.awssdk.services.kms.jce.provider.signature;

import software.amazon.awssdk.services.kms.jce.provider.KmsKey;
import lombok.NonNull;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;
import software.amazon.awssdk.services.kms.model.VerifyRequest;

import java.security.*;

public class KmsSignature extends SignatureSpi {

    private final KmsClient kmsClient;

    private SigningAlgorithmSpec signingAlgorithmSpec;
    private MessageDigest messageDigest;
    private boolean digestReset;

    private KmsKey key;

    public KmsSignature(@NonNull KmsClient kmsClient, @NonNull KmsSigningAlgorithm kmsSigningAlgorithm) {
        this.kmsClient = kmsClient;
        this.signingAlgorithmSpec = kmsSigningAlgorithm.getSigningAlgorithmSpec();
        initMessageDigest(kmsSigningAlgorithm.getDigestAlgorithm());
    }

    @Override
    protected void engineInitVerify(PublicKey publicKey) {
        this.key = (KmsKey) publicKey;
        this.resetDigest();
    }

    @Override
    protected void engineInitSign(PrivateKey privateKey) {
        this.key = (KmsKey) privateKey;
        this.resetDigest();
    }

    @Override
    protected void engineUpdate(byte bytes) {
        this.messageDigest.update(bytes);
        this.digestReset = false;
    }

    @Override
    protected void engineUpdate(byte[] bytes, int off, int len) {
        this.messageDigest.update(bytes, off, len);
        this.digestReset = false;
    }

    @Override
    protected byte[] engineSign() {
        SignRequest signRequest = SignRequest.builder()
                .keyId(key.getId())
                .messageType(MessageType.DIGEST)
                .signingAlgorithm(signingAlgorithmSpec)
                .message(SdkBytes.fromByteArray(this.getDigestValue()))
                .build();
        return kmsClient.sign(signRequest).signature().asByteArray();
    }

    @Override
    protected boolean engineVerify(byte[] signature) throws SignatureException {
        VerifyRequest verifyRequest = VerifyRequest.builder()
                .keyId(key.getId())
                .messageType(MessageType.DIGEST)
                .signingAlgorithm(signingAlgorithmSpec)
                .message(SdkBytes.fromByteArray(this.getDigestValue()))
                .signature(SdkBytes.fromByteArray(signature))
                .build();
        return kmsClient.verify(verifyRequest).signatureValid();
    }

    @Override
    protected void engineSetParameter(String s, Object o) throws InvalidParameterException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object engineGetParameter(String param) throws InvalidParameterException {
        throw new UnsupportedOperationException();
    }

    private void initMessageDigest(String digestAlgorithm) {
        try {
            this.messageDigest = MessageDigest.getInstance(digestAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new ProviderException(e);
        }
        this.digestReset = true;
    }

    private void resetDigest() {
        if (!this.digestReset) {
            this.messageDigest.reset();
            this.digestReset = true;
        }
    }

    private byte[] getDigestValue() {
        this.digestReset = true;
        return this.messageDigest.digest();
    }

}
