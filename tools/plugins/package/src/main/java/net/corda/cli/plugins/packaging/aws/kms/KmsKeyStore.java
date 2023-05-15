package net.corda.cli.plugins.packaging.aws.kms;

import net.corda.cli.plugins.packaging.aws.kms.ec.KmsECKeyFactory;
import net.corda.cli.plugins.packaging.aws.kms.rsa.KmsRSAKeyFactory;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AliasListEntry;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class KmsKeyStore extends KeyStoreSpi {

    @NotNull
    private final KmsClient kmsClient;

    public KmsKeyStore(KmsClient kmsClient) {
        Objects.requireNonNull(kmsClient);
        this.kmsClient = kmsClient;
    }

    @Override
    public Enumeration<String> engineAliases() {
        return Collections.enumeration(getAliases().stream().map(AliasListEntry::aliasName).collect(Collectors.toSet()));
    }

    @Override
    public boolean engineContainsAlias(String alias) {
        String prefixedAlias = getPrefixedAlias(alias);
        return getAliases().stream().filter(e -> e.aliasName().equals(prefixedAlias)).findAny().isPresent();
    }

    @Override
    public int engineSize() {
        return getAliases().size();
    }

    @Override
    public Key engineGetKey(String alias, char[] chars) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        String prefixedAlias = getPrefixedAlias(alias);
        AliasListEntry aliasListEntry = getAliases().stream().filter(e -> e.aliasName().equals(prefixedAlias)).findAny().orElse(null);
        if (aliasListEntry == null) return null;

        DescribeKeyResponse describeKeyResponse = kmsClient.describeKey(builder -> builder.keyId(aliasListEntry.targetKeyId()));
        if (!describeKeyResponse.keyMetadata().hasSigningAlgorithms()) {
            throw new IllegalStateException("Unsupported Key type. Only signing keys are supported.");
        }

        boolean rsa = describeKeyResponse.keyMetadata().signingAlgorithmsAsStrings().stream().filter(a -> a.startsWith("RSA")).findAny().isPresent();
        return rsa ? KmsRSAKeyFactory.getPrivateKey(describeKeyResponse.keyMetadata().keyId()) : KmsECKeyFactory.getPrivateKey(describeKeyResponse.keyMetadata().keyId());
    }

    private String getPrefixedAlias(String alias) {
        return alias.startsWith("alias/") ? alias : "alias/" + alias;
    }

    private Set<AliasListEntry> getAliases() {
        ListAliasesResponse listAliasesResponse = null;
        String marker = null;
        Set<AliasListEntry> aliases = new HashSet<>();

        do {
            listAliasesResponse = kmsClient.listAliases(ListAliasesRequest.builder().marker(marker).build());
            aliases.addAll(listAliasesResponse.aliases());
            marker = listAliasesResponse.nextMarker();
        } while (listAliasesResponse.truncated());

        return aliases;
    }

    @Override
    public void engineLoad(InputStream inputStream, char[] chars) throws IOException, NoSuchAlgorithmException, CertificateException {
    }

    /*
    UNSUPPORTED OPERATIONS
     */

    @Override
    public Certificate[] engineGetCertificateChain(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Certificate engineGetCertificate(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date engineGetCreationDate(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineSetKeyEntry(String s, Key key, char[] chars, Certificate[] certificates) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineSetKeyEntry(String s, byte[] bytes, Certificate[] certificates) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineSetCertificateEntry(String s, Certificate certificate) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineDeleteEntry(String s) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean engineIsKeyEntry(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean engineIsCertificateEntry(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String engineGetCertificateAlias(Certificate certificate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineStore(OutputStream outputStream, char[] chars) throws IOException, NoSuchAlgorithmException, CertificateException {
        throw new UnsupportedOperationException();
    }
}