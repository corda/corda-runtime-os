package net.corda.testing.driver;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl;
import net.corda.crypto.cipher.suite.CipherSchemeMetadata;
import net.corda.crypto.cipher.suite.schemes.KeyScheme;
import net.corda.testing.driver.function.ThrowingConsumer;
import net.corda.testing.driver.function.ThrowingFunction;
import net.corda.testing.driver.impl.DriverDSLImpl;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.crypto.KeySchemeCodes;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtensionContext;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

abstract class AbstractDriver {
    private static final CipherSchemeMetadata SCHEME_METADATA = new CipherSchemeMetadataImpl();
    public static final String DEFAULT_SCHEME_NAME = KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME;

    private final Map<MemberX500Name, KeyPair> network;
    private ClassLoader originalTCCL;
    private DriverDSLImpl driver;

    @SafeVarargs
    @NotNull
    protected static <T> Set<T> setOf(@NotNull T item, T... others) {
        Set<T> items = new LinkedHashSet<>();
        items.add(item);
        Collections.addAll(items, others);
        return unmodifiableSet(items);
    }

    @NotNull
    private static KeyPairGenerator getKeyPairGenerator(@NotNull String schemeName)
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        final KeyScheme keyScheme = SCHEME_METADATA.findKeyScheme(schemeName);
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
            keyScheme.getAlgorithmName(),
            SCHEME_METADATA.getProviders().get(keyScheme.getProviderName())
        );
        final AlgorithmParameterSpec algorithmSpec = keyScheme.getAlgSpec();
        if (algorithmSpec != null) {
            keyPairGenerator.initialize(algorithmSpec, SCHEME_METADATA.getSecureRandom());
        } else {
            final Integer keySize = keyScheme.getKeySize();
            if (keySize != null) {
                keyPairGenerator.initialize(keySize, SCHEME_METADATA.getSecureRandom());
            }
        }
        return keyPairGenerator;
    }

    @NotNull
    private static Map<MemberX500Name, KeyPair> createNetwork(@NotNull String schemeName, @NotNull Set<MemberX500Name> members) {
        final KeyPairGenerator keyPairGenerator;
        try {
            keyPairGenerator = getKeyPairGenerator(schemeName);
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new AssertionError(e.getMessage(), e);
        }
        final Map<MemberX500Name, KeyPair> map = new LinkedHashMap<>();
        for (MemberX500Name member : members) {
            map.put(member, keyPairGenerator.generateKeyPair());
        }
        return map;
    }

    AbstractDriver(@NotNull String schemeName, @NotNull Set<MemberX500Name> members) {
        requireNonNull(schemeName);
        requireNonNull(members);
        network = members.isEmpty() ? emptyMap() : unmodifiableMap(createNetwork(schemeName, members));
    }

    private void checkDriver() {
        assertNotNull(driver, getClass().getSimpleName() + " not registered as a JUnit extension.");
    }

    private void publishReport(@NotNull ExtensionContext context, @NotNull String methodName) {
        final Map<String, String> map = new HashMap<>();
        map.put("driverTestClass", context.getRequiredTestClass().getName());
        map.put("driverMethod", methodName);
        map.put("driverType", getClass().getSimpleName());
        context.publishReportEntry(map);
    }

    protected final void createDriver(@NotNull ExtensionContext context) {
        publishReport(context, "createDriver");
        originalTCCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
        try {
            driver = new DriverDSLImpl(network);
        } catch (RuntimeException | Error e) {
            Thread.currentThread().setContextClassLoader(originalTCCL);
            throw e;
        }
    }

    protected final void destroyDriver(@NotNull ExtensionContext context) throws Exception {
        publishReport(context, "destroyDriver");
        try {
            driver.close();
        } finally {
            Thread.currentThread().setContextClassLoader(originalTCCL);
        }
    }

    public final <R> R let(@NotNull ThrowingFunction<DriverDSL, R> action) {
        checkDriver();
        return action.apply(driver);
    }

    public final void run(@NotNull ThrowingConsumer<DriverDSL> action) {
        checkDriver();
        action.accept(driver);
    }
}
