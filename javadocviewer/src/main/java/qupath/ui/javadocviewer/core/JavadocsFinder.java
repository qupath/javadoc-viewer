package qupath.ui.javadocviewer.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ui.javadocviewer.UriUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Utility class to search for Javadocs.
 */
public class JavadocsFinder {

    private static final Logger logger = LoggerFactory.getLogger(JavadocsFinder.class);
    private static final String JAVADOC_INDEX_FILE = "index.html";
    private static final List<String> ARCHIVE_EXTENSIONS = List.of(".jar", ".zip");
    private static final int SEARCH_DEPTH = 4;

    private JavadocsFinder() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Asynchronously search for Javadocs in the specified URIs.
     *
     * @param urisToSearch URIs to search for Javadocs. It can be a directory, an HTTP link,
     *                     a link to a jar file...
     * @return a CompletableFuture with the list of Javadocs found
     */
    public static CompletableFuture<List<Javadoc>> findJavadocs(URI... urisToSearch) {
        return CompletableFuture.supplyAsync(() -> Arrays.stream(urisToSearch)
                .map(JavadocsFinder::findJavadocUrisFromUri)
                .flatMap(List::stream)
                .map(uri -> {
                    try {
                        return Javadoc.create(uri).get();
                    } catch (InterruptedException | ExecutionException e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        logger.warn("Error when creating javadoc of {}. Skipping it", uri, e);

                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList()
        );
    }

    private static List<URI> findJavadocUrisFromUri(URI uri) {
        if (UriUtils.doesUriLinkToWebsite(uri)) {
            logger.debug("URI {} retrieved", uri);
            return List.of(uri);
        } else {
            try {
                return findJavadocUrisFromPath(Paths.get(uri));
            } catch (Exception e) {
                logger.debug("Could not convert URI {} to path", uri, e);
                return List.of();
            }
        }
    }

    private static List<URI> findJavadocUrisFromPath(Path path) {
        if (Files.isDirectory(path)) {
            return findJavadocUrisFromDirectory(path);
        } else {
            return findJavadocUrisFromFile(path).map(List::of).orElse(List.of());
        }
    }

    private static List<URI> findJavadocUrisFromDirectory(Path directory) {
        logger.debug("Searching for javadocs in {} directory with depth {}", directory, SEARCH_DEPTH);

        try (Stream<Path> walk = Files.walk(directory, JavadocsFinder.SEARCH_DEPTH)) {
            return walk
                    .map(JavadocsFinder::findJavadocUrisFromFile)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException e) {
            logger.debug("Exception while searching for javadoc URIs", e);
            return List.of();
        }
    }

    private static Optional<URI> findJavadocUrisFromFile(Path path) {
        logger.debug("Determining if {} contains Javadoc", path);

        File file = path.toFile();

        if (
                JAVADOC_INDEX_FILE.equalsIgnoreCase(file.getName()) &&
                List.of("javadoc", "javadocs", "docs").contains(file.getParentFile().getName().toLowerCase())
        ) {
            try (Stream<String> lines = Files.lines(path)) {
                if (lines.anyMatch(l -> l.contains("javadoc"))) {
                    logger.debug("{} points to a Javadoc index page", path);
                    return Optional.of(path.toUri());
                }
            } catch (IOException e) {
                logger.debug("Error while reading {}", path, e);
            }
        }

        Optional<String> extension = getExtension(file.toString());
        if (extension.isPresent() &&
                ARCHIVE_EXTENSIONS.contains(extension.get()) &&
                file.getName().toLowerCase().endsWith("javadoc" + extension.get())
        ) {
            try (ZipFile zipFile = new ZipFile(file)) {
                if (zipFile.getEntry(JAVADOC_INDEX_FILE) != null) {
                    String uri = String.format("jar:%s!/%s", file.toURI(), JAVADOC_INDEX_FILE);

                    try {
                        logger.debug("{} is an archive containing Javadoc", uri);
                        return Optional.of(new URI(uri));
                    } catch (URISyntaxException e) {
                        logger.warn("Error while creating URI {}", uri, e);
                    }
                }
            } catch (IOException e) {
                logger.warn("Error while reading {}", path, e);
            }
        }

        logger.debug("{} doesn't contain Javadoc", path);
        return Optional.empty();
    }

    private static Optional<String> getExtension(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".")));
    }
}
