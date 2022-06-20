package net.corda.v5.ledger.obsolete.contracts;

import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PackageIdWithDependenciesJavaApiTest {
    private final SecureHash secureHash =
            SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final List<SecureHash> secureHashes = List.of(secureHash);
    private final PackageIdWithDependencies packageIdWithDependencies =
            new PackageIdWithDependencies(secureHash, secureHashes);

    @Test
    public void packageId() {
        final SecureHash secureHash1 = packageIdWithDependencies.getPackageId();

        Assertions.assertThat(secureHash1).isEqualTo(secureHash);
    }

    @Test
    public void dependencyIds() {
        List<SecureHash> secureHashes1 = packageIdWithDependencies.getDependencyIds();

        Assertions.assertThat(secureHashes1).isEqualTo(secureHashes);
    }
}
