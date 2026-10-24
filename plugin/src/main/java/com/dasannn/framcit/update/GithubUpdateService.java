package com.dasannn.framcit.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Checks the public GitHub release and stages verified JARs for Paper's next startup. */
public final class GithubUpdateService {

    private static final URI RELEASE_API = URI.create("https://api.github.com/repos/Dasannn/FraMCit/releases/latest");
    private static final String RELEASES_PATH = "/Dasannn/FraMCit/releases/download/";
    private static final long MAX_ASSET_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_SIGNATURE_BYTES = 1024L;
    private static final int MAX_REDIRECTS = 5;
    private static final Pattern JAR_NAME = Pattern.compile("[A-Za-z0-9._-]+\\.jar");
    private static final Set<String> GITHUB_ASSET_HOSTS = Set.of(
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    );

    private final HttpClient httpClient;

    public GithubUpdateService() {
        this(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
    }

    GithubUpdateService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public CheckResult checkLatest(String currentVersion) throws IOException, InterruptedException {
        String responseBody = getReleaseMetadata();
        Release release = parseRelease(responseBody);
        int versionComparison = compareStableVersions(release.version(), currentVersion);
        if (versionComparison > 0 && release.signatureUri() == null) {
            throw new IOException("La nueva release no contiene una firma Ed25519 válida.");
        }
        return new CheckResult(currentVersion, release, versionComparison > 0, versionComparison < 0, false);
    }

    public CheckResult downloadAndStage(String currentVersion, Path installedJar, Path updateDirectory)
        throws IOException, InterruptedException {
        CheckResult result = checkLatest(currentVersion);
        if (!result.updateAvailable()) {
            return result;
        }

        validateInstallPaths(installedJar, updateDirectory);
        Files.createDirectories(updateDirectory);
        Path temporaryFile = Files.createTempFile(updateDirectory, ".framcit-update-", ".part");
        Path signatureFile = null;
        try {
            signatureFile = Files.createTempFile(updateDirectory, ".framcit-update-signature-", ".part");
            downloadVerifiedAsset(result.release(), temporaryFile);
            downloadVerifiedFile(result.release().signatureUri(), result.release().signatureSize(),
                result.release().signatureSha256(), signatureFile);
            verifyJarSignature(temporaryFile, Files.readAllBytes(signatureFile), loadPinnedPublicKey());
            stageVerifiedJar(temporaryFile, installedJar, updateDirectory, result.release());
            return new CheckResult(result.currentVersion(), result.release(), true, false, true);
        } finally {
            Files.deleteIfExists(temporaryFile);
            if (signatureFile != null) Files.deleteIfExists(signatureFile);
        }
    }

    static Path stageVerifiedJar(Path downloadedJar, Path installedJar, Path updateDirectory, Release release) throws IOException {
        validateInstallPaths(installedJar, updateDirectory);
        validatePluginJar(downloadedJar, release);
        Path stagedJar = updateDirectory.resolve(installedJar.getFileName().toString());
        try {
            return Files.move(downloadedJar, stagedJar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            return Files.move(downloadedJar, stagedJar, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void validateInstallPaths(Path installedJar, Path updateDirectory) throws IOException {
        if (installedJar == null || installedJar.getFileName() == null || updateDirectory == null) {
            throw new IOException("No se pudo determinar la ruta del plugin o de actualización.");
        }
        if (!JAR_NAME.matcher(installedJar.getFileName().toString()).matches()) {
            throw new IOException("El nombre del JAR instalado no es válido para preparar la actualización.");
        }
    }

    private String getReleaseMetadata() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(RELEASE_API)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FraMCit-UpdateChecker")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub respondió con HTTP " + response.statusCode() + " al consultar releases.");
        }
        return response.body();
    }

    static Release parseRelease(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.get("draft").getAsBoolean() || root.get("prerelease").getAsBoolean()) {
                throw new IOException("La release más reciente no es estable y publicada.");
            }

            String tag = root.get("tag_name").getAsString();
            String version = normalizeStableVersion(tag);
            URI releasePage = requireHttpsGithubUri(root.get("html_url").getAsString(), "github.com", null);
            JsonArray assets = root.getAsJsonArray("assets");
            String expectedAssetName = "FraMCit-" + version + ".jar";
            JsonObject selected = null;
            JsonObject signatureAsset = null;
            for (JsonElement element : assets) {
                JsonObject asset = element.getAsJsonObject();
                if (expectedAssetName.equals(asset.get("name").getAsString())) {
                    if (selected != null) {
                        throw new IOException("La release contiene más de un JAR con el nombre esperado.");
                    }
                    selected = asset;
                } else if ((expectedAssetName + ".sig").equals(asset.get("name").getAsString())) {
                    if (signatureAsset != null) {
                        throw new IOException("La release contiene más de una firma para el JAR.");
                    }
                    signatureAsset = asset;
                }
            }
            if (selected == null) {
                throw new IOException("La release no contiene el asset esperado: " + expectedAssetName);
            }

            long size = selected.get("size").getAsLong();
            if (size <= 0 || size > MAX_ASSET_BYTES) {
                throw new IOException("El tamaño del JAR publicado no es válido.");
            }
            String digest = selected.has("digest") && !selected.get("digest").isJsonNull()
                ? selected.get("digest").getAsString() : "";
            if (!digest.matches("(?i)sha256:[0-9a-f]{64}")) {
                throw new IOException("GitHub no publicó un digest SHA-256 válido para el JAR.");
            }
            URI assetUri = requireHttpsGithubUri(selected.get("browser_download_url").getAsString(), "github.com", RELEASES_PATH);
            URI signatureUri = null;
            long signatureSize = 0;
            String signatureSha256 = null;
            if (signatureAsset != null) {
                signatureSize = signatureAsset.get("size").getAsLong();
                if (signatureSize != 64 || signatureSize > MAX_SIGNATURE_BYTES) {
                    throw new IOException("La firma Ed25519 publicada tiene un tamaño inválido.");
                }
                String signatureDigest = signatureAsset.has("digest") && !signatureAsset.get("digest").isJsonNull()
                    ? signatureAsset.get("digest").getAsString() : "";
                if (!signatureDigest.matches("(?i)sha256:[0-9a-f]{64}")) {
                    throw new IOException("GitHub no publicó un digest SHA-256 válido para la firma.");
                }
                signatureSha256 = signatureDigest.substring("sha256:".length()).toLowerCase(Locale.ROOT);
                signatureUri = requireHttpsGithubUri(
                    signatureAsset.get("browser_download_url").getAsString(), "github.com", RELEASES_PATH);
            }
            return new Release(version, tag, releasePage, assetUri, size,
                digest.substring("sha256:".length()).toLowerCase(Locale.ROOT), signatureUri, signatureSize, signatureSha256);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("La respuesta de releases de GitHub no tiene el formato esperado.", e);
        }
    }

    private void downloadVerifiedAsset(Release release, Path temporaryFile) throws IOException, InterruptedException {
        downloadVerifiedFile(release.assetUri(), release.size(), release.sha256(), temporaryFile);
    }

    private void downloadVerifiedFile(URI assetUri, long expectedSize, String expectedSha256, Path temporaryFile)
        throws IOException, InterruptedException {
        URI uri = assetUri;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            requireAllowedAssetUri(uri);
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(90))
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "FraMCit-UpdateChecker")
                .GET()
                .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IOException("GitHub devolvió una redirección sin destino."));
                try (InputStream ignored = response.body()) {
                    uri = uri.resolve(location);
                }
                if (redirect == MAX_REDIRECTS) {
                    throw new IOException("Se excedió el límite de redirecciones al descargar el JAR.");
                }
                continue;
            }
            if (status != 200) {
                try (InputStream ignored = response.body()) {
                    throw new IOException("GitHub respondió con HTTP " + status + " al descargar el JAR.");
                }
            }
            try (InputStream body = response.body()) {
                String contentLength = response.headers().firstValue("Content-Length").orElse(null);
                if (contentLength != null && Long.parseLong(contentLength) != expectedSize) {
                    throw new IOException("El tamaño anunciado para la descarga no coincide con la release.");
                }
                verifyAndWrite(body, temporaryFile, expectedSize, expectedSha256);
            } catch (NumberFormatException e) {
                throw new IOException("GitHub devolvió un Content-Length inválido.", e);
            }
            return;
        }
        throw new IOException("No se pudo descargar el JAR de GitHub.");
    }

    static void verifyAndWrite(InputStream input, Path temporaryFile, Release release) throws IOException {
        verifyAndWrite(input, temporaryFile, release.size(), release.sha256());
    }

    private static void verifyAndWrite(InputStream input, Path temporaryFile, long expectedSize, String expectedSha256)
        throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 no está disponible en esta JVM.", e);
        }

        long total = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream output = Files.newOutputStream(temporaryFile)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > expectedSize || total > MAX_ASSET_BYTES) {
                    throw new IOException("La descarga superó el tamaño declarado para el JAR.");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (total != expectedSize) {
            throw new IOException("La descarga del JAR quedó incompleta.");
        }
        String actualDigest = HexFormat.of().formatHex(digest.digest());
        if (!MessageDigest.isEqual(actualDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            expectedSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IOException("El SHA-256 del JAR descargado no coincide con GitHub.");
        }
    }

    static void validatePluginJar(Path file, Release release) throws IOException {
        try (ZipFile jar = new ZipFile(file.toFile())) {
            ZipEntry descriptor = jar.getEntry("paper-plugin.yml");
            if (descriptor == null) {
                throw new IOException("El asset descargado no contiene paper-plugin.yml.");
            }
            try (Reader reader = new InputStreamReader(jar.getInputStream(descriptor), StandardCharsets.UTF_8)) {
                YamlConfiguration metadata = YamlConfiguration.loadConfiguration(reader);
                String pluginName = metadata.getString("name", "");
                String pluginVersion = metadata.getString("version", "");
                if (!"FraMCit".equals(pluginName)
                    || compareStableVersions(pluginVersion, release.version()) != 0) {
                    throw new IOException("El JAR no declara FraMCit " + release.version() + ".");
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("El JAR descargado no es un plugin Paper válido.", e);
        }
    }

    static void verifyJarSignature(Path jarFile, byte[] signatureBytes, PublicKey publicKey) throws IOException {
        if (signatureBytes == null || signatureBytes.length != 64) {
            throw new IOException("La firma Ed25519 del JAR no tiene un tamaño válido.");
        }
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            try (InputStream input = Files.newInputStream(jarFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    verifier.update(buffer, 0, read);
                }
            }
            if (!verifier.verify(signatureBytes)) {
                throw new IOException("La firma Ed25519 del JAR no coincide con la clave pública confiable.");
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("No se pudo verificar la firma Ed25519 del JAR.", e);
        }
    }

    static PublicKey loadPinnedPublicKey() throws IOException {
        try (InputStream input = GithubUpdateService.class.getResourceAsStream("/framcit-update-public-key.pem")) {
            if (input == null) {
                throw new IOException("Falta la clave pública de verificación incluida en FraMCit.");
            }
            String pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("La clave pública Ed25519 incluida en FraMCit no es válida.", e);
        }
    }

    private static URI requireHttpsGithubUri(String value, String expectedHost, String expectedPathPrefix) throws IOException {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !expectedHost.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || (expectedPathPrefix != null && !uri.getPath().startsWith(expectedPathPrefix))) {
                throw new IOException("La URL de la release no pertenece al repositorio esperado de GitHub.");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IOException("GitHub devolvió una URL de release inválida.", e);
        }
    }

    static void requireAllowedAssetUri(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || !GITHUB_ASSET_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
            || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IOException("La descarga fue redirigida fuera de los hosts HTTPS permitidos de GitHub.");
        }
    }

    static int compareStableVersions(String left, String right) throws IOException {
        long[] a = parseVersionParts(left);
        long[] b = parseVersionParts(right);
        for (int i = 0; i < a.length; i++) {
            int comparison = Long.compare(a[i], b[i]);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static String normalizeStableVersion(String value) throws IOException {
        parseVersionParts(value);
        return value.startsWith("v") || value.startsWith("V") ? value.substring(1) : value;
    }

    private static long[] parseVersionParts(String value) throws IOException {
        if (value == null) throw new IOException("La versión no está definida.");
        String normalized = value.startsWith("v") || value.startsWith("V") ? value.substring(1) : value;
        String[] parts = normalized.split("\\.", -1);
        if (parts.length != 3) throw new IOException("La versión no usa el formato estable MAJOR.MINOR.PATCH.");
        long[] result = new long[3];
        try {
            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].matches("0|[1-9][0-9]*")) throw new NumberFormatException("versión no estable");
                result[i] = Long.parseLong(parts[i]);
            }
        } catch (NumberFormatException e) {
            throw new IOException("La versión no usa el formato estable MAJOR.MINOR.PATCH.", e);
        }
        return result;
    }

    public record Release(
        String version,
        String tag,
        URI releasePage,
        URI assetUri,
        long size,
        String sha256,
        URI signatureUri,
        long signatureSize,
        String signatureSha256
    ) {}

    public record CheckResult(String currentVersion, Release release, boolean updateAvailable, boolean localAhead, boolean staged) {}
}
