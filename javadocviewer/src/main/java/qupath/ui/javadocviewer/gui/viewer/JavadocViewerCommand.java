package qupath.ui.javadocviewer.gui.viewer;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.ResourceBundle;

/**
 * A command to start a {@link JavadocViewer} in a standalone window.
 * Only one instance of the viewer will be created.
 */
public class JavadocViewerCommand implements Runnable {

    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ui.javadocviewer.strings");
    private final Stage owner;
    private final ReadOnlyStringProperty stylesheet;
    private final List<URI> urisToSearch;
    private final int searchDepth;
    private Stage stage;
    private JavadocViewer javadocViewer;

    /**
     * Create the command. This will not create the viewer until either the command is run or {@link #getJavadocViewer()}
     * is called.
     *
     * @param owner the stage that should own the viewer window. Can be null
     * @param stylesheet a property containing a link to a stylesheet which should be applied to the viewer. Can be null
     * @param urisToSearch URIs to search for Javadocs. See {@link JavadocViewer#JavadocViewer(ReadOnlyStringProperty, List, int)}
     * @param searchDepth if one of the provided URI points to a local directory, indicate how deep to search for Javadocs
     *                    inside that directory
     */
    public JavadocViewerCommand(Stage owner, ReadOnlyStringProperty stylesheet, List<URI> urisToSearch, int searchDepth) {
        this.owner = owner;
        this.stylesheet = stylesheet;
        this.urisToSearch = List.copyOf(urisToSearch);
        this.searchDepth = searchDepth;
    }

    /**
     * Get a reference to the singleton {@link JavadocViewer}, creating it if required.
     *
     * @return the singleton {@link JavadocViewer}
     * @throws RuntimeException if the JavadocViewer cannot be initialized
     */
    public JavadocViewer getJavadocViewer() {
        if (javadocViewer == null) {
            try {
                javadocViewer = new JavadocViewer(stylesheet, urisToSearch, searchDepth);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return javadocViewer;
    }

    @Override
    public void run() {
        if (stage == null) {
            stage = new Stage();
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.setTitle(resources.getString("JavadocViewer.title"));

            javadocViewer = getJavadocViewer();

            Scene scene = new Scene(javadocViewer);
            stage.setScene(scene);
            stage.show();
        }

        stage.show();
        stage.requestFocus();
    }
}
