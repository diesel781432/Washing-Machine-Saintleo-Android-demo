package org.gradle.wrapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal transparent launcher supplied because an official Gradle wrapper JAR
 * could not be downloaded in the artifact-building environment.
 * Downloads only the explicitly pinned Gradle distribution, validates SHA-256,
 * unpacks to ~/.gradle/wrapper/dists, and delegates to its gradle executable.
 * Standard project Gradle wrapper configuration remains compatible with the IDE.
 */
public final class GradleWrapperMain {
    private GradleWrapperMain() { }

    public static void main(String[] args) throws Exception {
        Path jar = Paths.get(GradleWrapperMain.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toAbsolutePath();
        Path projectRoot = jar.getParent().getParent().getParent();
        Path configPath = projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties config = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) { config.load(input); }
        String distributionUrl = config.getProperty("distributionUrl");
        String checksum = config.getProperty("distributionSha256Sum", "");
        if (!"https://services.gradle.org/distributions/gradle-8.7-bin.zip".equals(distributionUrl)) {
            throw new IllegalStateException("Unexpected Gradle distribution URL: " + distributionUrl);
        }
        if (!"544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d".equalsIgnoreCase(checksum)) {
            throw new IllegalStateException("Unexpected Gradle distribution SHA256");
        }
        Path cache = Paths.get(System.getProperty("user.home"), ".gradle", "wrapper", "dists", "gradle-8.7-bin");
        Path folder = cache.resolve("gradle-8.7");
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path executable = folder.resolve("bin").resolve(windows ? "gradle.bat" : "gradle");
        if (!Files.exists(executable)) {
            Files.createDirectories(cache);
            Path download = cache.resolve("gradle-8.7-bin.zip");
            if (!Files.exists(download) || !sha256(download).equalsIgnoreCase(checksum)) {
                System.out.println("Downloading verified Gradle 8.7 distribution…");
                Path temp = cache.resolve("gradle-8.7-bin.zip.part");
                try (InputStream in = new URL(distributionUrl).openStream()) {
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                }
                if (!sha256(temp).equalsIgnoreCase(checksum)) {
                    Files.deleteIfExists(temp);
                    throw new IllegalStateException("Gradle distribution SHA-256 verification failed");
                }
                Files.move(temp, download, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("Extracting Gradle 8.7…");
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(download))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path target = cache.resolve(entry.getName()).normalize();
                    if (!target.startsWith(cache)) throw new IllegalStateException("Unsafe ZIP entry");
                    if (entry.isDirectory()) Files.createDirectories(target);
                    else {
                        Files.createDirectories(target.getParent());
                        try (OutputStream output = Files.newOutputStream(target)) {
                            byte[] buffer = new byte[8192];
                            int count;
                            while ((count = zip.read(buffer)) != -1) output.write(buffer, 0, count);
                        }
                    }
                    zip.closeEntry();
                }
            }
            if (!Files.exists(executable)) throw new IllegalStateException("Gradle executable missing after extraction");
        }
        List<String> command = new ArrayList<>();
        if (windows) { command.add("cmd.exe"); command.add("/c"); command.add(executable.toString()); }
        else { command.add("sh"); command.add(executable.toString()); }
        for (String arg : args) command.add(arg);
        Process process = new ProcessBuilder(command).directory(projectRoot.toFile()).inheritIO().start();
        int result = process.waitFor();
        if (result != 0) System.exit(result);
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format("%02x", b & 0xff));
        return result.toString();
    }
}
