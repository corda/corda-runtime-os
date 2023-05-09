package software.amazon.awssdk.services.kms.jce.util.csr;

import software.amazon.awssdk.services.kms.jce.provider.signature.KmsSigningAlgorithm;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

public abstract class CsrGenerator {

    /**
     * Generate CSR.
     *
     * @param keyPair
     * @param csrInfo
     * @param kmsSigningAlgorithm
     * @return
     */
    public static String generate(KeyPair keyPair, CsrInfo csrInfo, KmsSigningAlgorithm kmsSigningAlgorithm) {
        try {
            X500Principal subject = new X500Principal(csrInfo.toString());

            ContentSigner signGen = new JcaContentSignerBuilder(kmsSigningAlgorithm.getAlgorithm()).setProvider("KMS").build(keyPair.getPrivate());

            PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());
            PKCS10CertificationRequest csr = builder.build(signGen);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            JcaPEMWriter pemWriter = new JcaPEMWriter(new OutputStreamWriter(byteArrayOutputStream));
            pemWriter.writeObject(csr);
            pemWriter.close();

            return byteArrayOutputStream.toString(StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
