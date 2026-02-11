package qupath.ui.javadocviewer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A collection of utility functions to work with {@link URI}.
 */
public class UriUtils {

    private static final List<String> WEBSITE_SCHEMES = List.of("http", "https");
    private static final int REQUEST_TIMEOUT_SECONDS = 10;
    private static final Logger logger = LoggerFactory.getLogger(UriUtils.class);

    private UriUtils() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Indicate whether the provided URI links to a website.
     *
     * @param uri the URI to check
     * @return whether the provided URI links to a website
     */
    public static boolean doesUriLinkToWebsite(URI uri) {
        return uri.getScheme() != null && WEBSITE_SCHEMES.contains(uri.getScheme());
    }

    /**
     * Indicate whether the provided URI links to a jar file or a file contained in a jar file.
     *
     * @param uri the URI to check
     * @return whether the provided URI links to a jar file or a file contained in a jar file
     */
    public static boolean doesUriLinkToJar(URI uri) {
        return uri.getScheme() != null && uri.getScheme().contains("jar");
    }

    /**
     * Attempt to read the resource pointed by the provided URI.
     * <p>
     * This function supports reading http or https links, files contained in local jars, and local files.
     * <p>
     * The returned CompletableFuture may complete exceptionally.
     *
     * @param uri the URI pointing to the resource to read
     * @return the resource pointed by the provided URI, or a failed CompletableFuture if the reading did not succeed
     */
    public static CompletableFuture<String> getContentOfUri(URI uri) {
        if (doesUriLinkToWebsite(uri)) {
            return getContentOfHttpUri(uri);
        } else if (doesUriLinkToJar(uri)) {
            return CompletableFuture.supplyAsync(() -> getContentOfJarUri(uri));
        } else if (uri.getScheme().contains("file")) {
            return CompletableFuture.supplyAsync(() -> getContentOfFileUri(uri));
        } else {
            return CompletableFuture.failedFuture(new IllegalArgumentException(String.format(
                    "The provided URI %s does not point to a website, jar or file",
                    uri
            )));
        }
    }

    private static CompletableFuture<String> getContentOfHttpUri(URI uri) {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        logger.debug("Sending GET request to {}...", uri);

        return httpClient.sendAsync(
                HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.of(REQUEST_TIMEOUT_SECONDS, ChronoUnit.SECONDS))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {
            logger.debug("Got response {} from {}", response, uri);
            return response.body();
        }).whenComplete((b, e) -> httpClient.close());
    }

    private static String getContentOfJarUri(URI uri) {
        // The provided URI is expected to be like: jar:file:/path/to/some-javadoc.jar!/index.html#someParameters with some
        // HTML encoding for special characters
        String jarUri = URLDecoder.decode(
                uri.toString().substring(
                        uri.toString().indexOf('/'),
                        uri.toString().lastIndexOf('!')
                ),
                StandardCharsets.UTF_8
        );
        logger.debug("Opening {} jar file to read the content of {}...", jarUri, uri);

        try (ZipFile zipFile = new ZipFile(jarUri)) {
            String entryName = uri.toString().substring(
                    uri.toString().lastIndexOf("!/") + 2,
                    uri.toString().lastIndexOf('#') == -1 ? uri.toString().length() : uri.toString().lastIndexOf('#')
            );
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                throw new IllegalArgumentException(String.format("%s not found in %s", entryName, jarUri));
            }

            try (
                    InputStream inputStream = zipFile.getInputStream(entry);
                    Scanner scanner = new Scanner(inputStream)
            ) {
                StringBuilder lines = new StringBuilder();
                while (scanner.hasNextLine()) {
                    lines.append(scanner.nextLine());
                }
                return lines.toString();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getContentOfFileUri(URI uri) {
        logger.debug("Reading {} file...", uri);

        URI uriWithoutFragment;
        try {
            uriWithoutFragment = new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        try (Stream<String> lines = Files.lines(Paths.get(uriWithoutFragment))) {
            return lines.collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
