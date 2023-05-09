package software.amazon.awssdk.services.kms.jce.util.crt;

import software.amazon.awssdk.services.kms.jce.provider.signature.KmsSigningAlgorithm;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public abstract class SelfSignedCrtGenerator {

    /**
     * Generate Self Signed Certificate for the CSR that was previously generated.
     * @see software.amazon.awssdk.services.kms.jce.util.csr.CsrGenerator
     *
     * @param keyPair
     * @param csr
     * @param kmsSigningAlgorithm
     * @param validity in days
     * @return
     */
    public static String generate(KeyPair keyPair, String csr, KmsSigningAlgorithm kmsSigningAlgorithm, int validity) {

        try {
            PEMParser pemParser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(csr.getBytes(StandardCharsets.UTF_8))));
            PKCS10CertificationRequest csrRequest = (PKCS10CertificationRequest) pemParser.readObject();

            X509v3CertificateBuilder certificateGenerator = new X509v3CertificateBuilder(
                    csrRequest.getSubject(),
                    new BigInteger("1"),
                    Date.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)),
                    Date.from(LocalDateTime.now().plusDays(validity).toInstant(ZoneOffset.UTC)),
                    csrRequest.getSubject(),
                    csrRequest.getSubjectPublicKeyInfo()
            );

            ContentSigner signGen = new JcaContentSignerBuilder(kmsSigningAlgorithm.getAlgorithm()).setProvider("KMS").build(keyPair.getPrivate());

            X509CertificateHolder holder = certificateGenerator.build(signGen);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(holder.toASN1Structure().getEncoded()));

            ByteArrayOutputStream certOutputStream = new ByteArrayOutputStream();
            JcaPEMWriter pemWriter = new JcaPEMWriter(new OutputStreamWriter(certOutputStream));
            pemWriter.writeObject(new PemObject("CERTIFICATE", certificate.getEncoded()));
            pemWriter.close();

            return certOutputStream.toString(StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
