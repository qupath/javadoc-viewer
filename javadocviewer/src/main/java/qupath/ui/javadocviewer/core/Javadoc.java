package qupath.ui.javadocviewer.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ui.javadocviewer.UriUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Javadoc specified by a {@link URI} and containing {@link JavadocElement JavadocElements}.
 * Elements are populated by looking at the {@link #INDEX_ALL_PAGE} page of the Javadoc.
 *
 * @param uri the URI of this Javadoc
 * @param elements an unmodifiable view of the elements of this Javadoc
 */
public record Javadoc(URI uri, List<JavadocElement> elements) {

    private static final Logger logger = LoggerFactory.getLogger(Javadoc.class);
    private static final String INDEX_PAGE = "index.html";
    private static final String INDEX_ALL_PAGE = "index-all.html";
    private static final Pattern ENTRY_PATTERN = Pattern.compile("<dt>(.*?)</dt>");
    private static final Pattern URI_PATTERN = Pattern.compile("href=\"(.+?)\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("<a .*?>(?:<span .*?>)?(.*?)(?:</span>)?</a>");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("</a> - (.+?) ");

    /**
     * Create a Javadoc from a URI and Javadoc elements. Take a look at {@link #create(URI)}
     * to create a Javadoc only from a URI.
     *
     * @param uri the URI pointing to this Javadoc
     * @param elements the elements of this Javadoc
     */
    public Javadoc(URI uri, List<JavadocElement> elements) {
        this.uri = uri;
        this.elements = Collections.unmodifiableList(elements);
    }

    /**
     * Asynchronously attempt to create a Javadoc from the specified URI.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may
     * complete exceptionally if the elements of the Javadocs cannot be retrieved for example).
     *
     * @param uri the URI of the Javadoc
     * @return a CompletableFuture (that may complete exceptionally) with the created Javadoc
     */
    public static CompletableFuture<Javadoc> create(URI uri) {
        return getIndexAllPage(uri).thenApply(indexAllPage -> new Javadoc(
                uri,
                parseJavadocIndexPage(
                        uri.toString().substring(0, uri.toString().lastIndexOf('/') + 1),
                        indexAllPage
                )
        ));
    }

    private static CompletableFuture<String> getIndexAllPage(URI javadocIndexURI) {
        String link = javadocIndexURI.toString().replace(INDEX_PAGE, INDEX_ALL_PAGE);
        URI indexAllUri;
        try {
            indexAllUri = new URI(link);
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return UriUtils.getContentOfUri(indexAllUri);
    }

    private static List<JavadocElement> parseJavadocIndexPage(String javadocURI, String indexHTMLPage) {
        List<JavadocElement> elements = new ArrayList<>();
        Matcher entryMatcher = ENTRY_PATTERN.matcher(indexHTMLPage);

        while (entryMatcher.find()) {
            if (entryMatcher.groupCount() > 0) {
                Matcher uriMatcher = URI_PATTERN.matcher(entryMatcher.group(1));
                Matcher nameMatcher = NAME_PATTERN.matcher(entryMatcher.group(1));
                Matcher categoryMatcher = CATEGORY_PATTERN.matcher(entryMatcher.group(1));

                if (uriMatcher.find() && uriMatcher.groupCount() > 0
                        && nameMatcher.find() && nameMatcher.groupCount() > 0
                        && categoryMatcher.find() && categoryMatcher.groupCount() > 0
                ) {
                    String name = nameMatcher.group(1).replace("&lt;", "<").replace("&gt;", ">");
                    if (nameMatcher.find() && nameMatcher.groupCount() > 0) {
                        name = nameMatcher.group(1) + "." + name;
                    }
                    String link = javadocURI + uriMatcher.group(1);
                    String category = categoryMatcher.group(1);

                    name = correctNameIfConstructor(name, category);

                    try {
                        elements.add(new JavadocElement(
                                new URI(link),
                                name,
                                category
                        ));
                    } catch (URISyntaxException e) {
                        logger.debug("Cannot create URI {} of Javadoc element", link, e);
                    }
                }
            }
        }

        return elements;
    }

    private static String correctNameIfConstructor(String name, String category) {
        // Constructor are usually written in the following way: "Class.Class(Parameter)"
        // This function transforms them into "Class(Parameter)"
        if (category.equals("Constructor")) {
            int pointIndex = name.indexOf(".");
            return name.substring(pointIndex + 1);
        } else {
            return name;
        }
    }
}
