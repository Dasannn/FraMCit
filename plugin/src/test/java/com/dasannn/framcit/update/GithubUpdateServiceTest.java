package com.dasannn.framcit.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class GithubUpdateServiceTest {

    private static final String SHA256 = "a".repeat(64);

    @TempDir
    Path tempDir;

    @Test
    void parsesOnlyTheExpectedStableReleaseAssetAndDigest() throws IOException {
        GithubUpdateService.Release release = GithubUpdateService.parseRelease(releaseJson(false, false, SHA256,
            "https://github.com/Dasannn/FraMCit/releases/download/v1.2.3/FraMCit-1.2.3.jar"));

        assertEquals("1.2.3", release.version());
        assertEquals("v1.2.3", release.tag());
        assertEquals(4L, release.size());
        assertEquals(SHA256, release.sha256());
        assertEquals("github.com", release.assetUri().getHost());
        assertNull(release.signatureUri(), "La release bootstrap actual aún no contiene firma; no debe ofrecer updates nuevos");
    }

    @Test
    void comparesStableVersionsNumerically() throws IOException {
        assertTrue(GithubUpdateService.compareStableVersions("1.10.0", "1.9.9") > 0);
        assertEquals(0, GithubUpdateService.compareStableVersions("v1.2.3", "1.2.3"));
        assertTrue(GithubUpdateService.compareStableVersions("1.0.1", "1.0.0") > 0);
    }

    @Test
    void rejectsPrereleasesAndMissingDigests() {
        assertThrows(IOException.class, () -> GithubUpdateService.parseRelease(releaseJson(false, true, SHA256,
            "https://github.com/Dasannn/FraMCit/releases/download/v1.2.3/FraMCit-1.2.3.jar")));
        assertThrows(IOException.class, () -> GithubUpdateService.parseRelease(releaseJson(false, false, "",
            "https://github.com/Dasannn/FraMCit/releases/download/v1.2.3/FraMCit-1.2.3.jar")));
    }

    @Test
    void parsesTheExpectedEd25519SignatureAsset() throws IOException {
        GithubUpdateService.Release release = GithubUpdateService.parseRelease(signedReleaseJson());
        assertEquals("FraMCit-1.2.3.jar.sig",
            Path.of(release.signatureUri().getPath()).getFileName().toString());
        assertEquals(64, release.signatureSize());
        assertEquals(SHA256, release.signatureSha256());
    }

    @Test
    void rejectsAssetUrlsOutsideTheRepositoryDownloadPath() {
        assertThrows(IOException.class, () -> GithubUpdateService.parseRelease(releaseJson(false, false, SHA256,
            "https://example.org/FraMCit-1.2.3.jar")));
    }

    @Test
    void writesOnlyACompleteAssetWhoseSha256Matches() throws NoSuchAlgorithmException, IOException {
        byte[] bytes = "test-jar-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        GithubUpdateService.Release release = releaseForTest(bytes.length, digest);
        Path target = tempDir.resolve("candidate.part");

        GithubUpdateService.verifyAndWrite(new ByteArrayInputStream(bytes), target, release);

        assertArrayEquals(bytes, Files.readAllBytes(target));
        GithubUpdateService.Release wrongDigest = releaseForTest(bytes.length, SHA256);
        assertThrows(IOException.class, () -> GithubUpdateService.verifyAndWrite(
            new ByteArrayInputStream(bytes), tempDir.resolve("rejected.part"), wrongDigest));
    }

    @Test
    void validatesThePluginDescriptorAndReleaseVersionInsideTheJar() throws IOException {
        GithubUpdateService.Release release = releaseForTest(1, SHA256);
        Path validJar = createJar("name: FraMCit\nversion: '1.2.3'\n");
        Path wrongVersionJar = createJar("name: FraMCit\nversion: '1.2.2'\n");

        assertDoesNotThrow(() -> GithubUpdateService.validatePluginJar(validJar, release));
        assertThrows(IOException.class, () -> GithubUpdateService.validatePluginJar(wrongVersionJar, release));
    }

    @Test
    void stagesVerifiedJarsWithTheCurrentlyInstalledFilename() throws IOException {
        GithubUpdateService.Release release = releaseForTest(1, SHA256);
        Path downloadedJar = createJar("name: FraMCit\nversion: '1.2.3'\n");
        Path installedJar = tempDir.resolve("FraMCit-1.0.0.jar");
        Path updateDirectory = Files.createDirectory(tempDir.resolve("update"));

        Path staged = GithubUpdateService.stageVerifiedJar(downloadedJar, installedJar, updateDirectory, release);

        assertEquals(updateDirectory.resolve("FraMCit-1.0.0.jar"), staged);
        assertTrue(Files.exists(staged));
        assertFalse(Files.exists(downloadedJar));
    }

    @Test
    void allowsOnlyHttpsGithubAssetRedirectHosts() throws IOException {
        assertDoesNotThrow(() -> GithubUpdateService.requireAllowedAssetUri(
            URI.create("https://release-assets.githubusercontent.com/release.jar")));
        assertDoesNotThrow(() -> GithubUpdateService.requireAllowedAssetUri(
            URI.create("https://objects.githubusercontent.com/release.jar")));
        assertThrows(IOException.class, () -> GithubUpdateService.requireAllowedAssetUri(
            URI.create("https://example.org/release.jar")));
        assertThrows(IOException.class, () -> GithubUpdateService.requireAllowedAssetUri(
            URI.create("http://github.com/release.jar")));
    }

    @Test
    void verifiesJarSignatureWithPinnedEd25519PublicKey() throws Exception {
        byte[] jarBytes = "jar test bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(jarBytes);
        byte[] signature = signer.sign();
        Path jar = tempDir.resolve("signed.jar");
        Files.write(jar, jarBytes);

        assertDoesNotThrow(() -> GithubUpdateService.verifyJarSignature(jar, signature, pair.getPublic()));
        Files.writeString(jar, "modified jar bytes");
        assertThrows(IOException.class, () -> GithubUpdateService.verifyJarSignature(jar, signature, pair.getPublic()));
    }

    @Test
    void loadsThePinnedUpdateVerificationKeyFromPluginResources() throws IOException {
        assertNotNull(GithubUpdateService.loadPinnedPublicKey());
    }

    private Path createJar(String descriptor) throws IOException {
        Path jarPath = tempDir.resolve("candidate-" + System.nanoTime() + ".jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jarPath))) {
            zip.putNextEntry(new ZipEntry("paper-plugin.yml"));
            zip.write(descriptor.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jarPath;
    }

    private static String releaseJson(boolean draft, boolean prerelease, String digest, String downloadUrl) {
        String releaseDigest = digest.isBlank() ? "null" : "\"sha256:" + digest + "\"";
        return """
            {
              "tag_name":"v1.2.3",
              "draft":%s,
              "prerelease":%s,
              "html_url":"https://github.com/Dasannn/FraMCit/releases/tag/v1.2.3",
              "assets":[{
                "name":"FraMCit-1.2.3.jar",
                "size":4,
                "digest":%s,
                "browser_download_url":"%s"
              }]
            }
            """.formatted(draft, prerelease, releaseDigest, downloadUrl);
    }

    private static String signedReleaseJson() {
        return """
            {
              "tag_name":"v1.2.3",
              "draft":false,
              "prerelease":false,
              "html_url":"https://github.com/Dasannn/FraMCit/releases/tag/v1.2.3",
              "assets":[
                {"name":"FraMCit-1.2.3.jar","size":4,"digest":"sha256:%s","browser_download_url":"https://github.com/Dasannn/FraMCit/releases/download/v1.2.3/FraMCit-1.2.3.jar"},
                {"name":"FraMCit-1.2.3.jar.sig","size":64,"digest":"sha256:%s","browser_download_url":"https://github.com/Dasannn/FraMCit/releases/download/v1.2.3/FraMCit-1.2.3.jar.sig"}
              ]
            }
            """.formatted(SHA256, SHA256);
    }

    private static GithubUpdateService.Release releaseForTest(long size, String sha256) {
        return new GithubUpdateService.Release("1.2.3", "v1.2.3",
            URI.create("https://github.com/Dasannn/FraMCit/releases/tag/v1.2.3"),
            URI.create("https://github.com/Dasannn/FraMCit/releases/download/v1.2.3/FraMCit-1.2.3.jar"),
            size, sha256, null, 0, null);
    }
}
