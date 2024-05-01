package net.corda.osgi.framework;

import java.io.*;
import java.util.jar.*;
import java.util.stream.Stream;

public class FixLogging extends InputStream {
    public static final String IMPORT_PACKAGE = "Import-Package";
    private final InputStream inputStream;

    public FixLogging(InputStream inputStream) throws IOException {

        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
        bufferedInputStream.mark(Integer.MAX_VALUE);

        // Open the jar file
        JarInputStream jarInputStream = new JarInputStream(bufferedInputStream);

        // Get Import-Package from the manifest
        Manifest manifest = jarInputStream.getManifest();
        Attributes attributes = manifest.getMainAttributes();
        String importPackage = attributes.getValue(IMPORT_PACKAGE);

        // Exit early if this is not an OSGi bundle
        if (importPackage == null) {
            this.inputStream = bufferedInputStream;
            bufferedInputStream.reset();
            return;
        }

        // Fix the Import-Package value
        String newImportPackage = importPackage.replaceAll("org\\.slf4j;version=\"\\[1\\.[^,]*,2.*\\)\"",
                "org.slf4j;version=\"1.7\"");

        // Replace the attribute in the manifest
        attributes.putValue(IMPORT_PACKAGE, newImportPackage);

        // Build replacement jar stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        JarOutputStream jarOutputStream = new JarOutputStream(byteArrayOutputStream, manifest);

        // Copy all entries into the jar
        var entry = jarInputStream.getNextJarEntry();
        while (entry != null) {

            // Skip signature which will not validate after changing the import
            String name = entry.getName().toUpperCase();
            if (name.startsWith("META-INF/")
                    && (Stream.of(".SF", ".DSA", ".RSA", ".EC").anyMatch(name::endsWith))) {

                entry = jarInputStream.getNextJarEntry();
                continue;
            }

            jarOutputStream.putNextEntry(new JarEntry(entry));
            jarOutputStream.write(jarInputStream.readAllBytes());
            jarOutputStream.closeEntry();
            entry = jarInputStream.getNextJarEntry();
        }

        // Close streams we have finished reading
        inputStream.close();
        bufferedInputStream.close();
        jarOutputStream.close();
        jarInputStream.close();

        byte[] newJar = byteArrayOutputStream.toByteArray();
        this.inputStream = new ByteArrayInputStream(newJar);
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
