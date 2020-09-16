package fxlauncher;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("unchecked")
public class Launcher extends Application {
    private static final Logger log = Logger.getLogger("Launcher");

    private Application app;
    private Stage primaryStage;
    private Stage stage;
    private UIProvider uiProvider;
    private StackPane root;
    private boolean update = false;

    private final AbstractLauncher superLauncher = new AbstractLauncher<Application>() {
        @Override
        protected Parameters getParameters() {
            return Launcher.this.getParameters();
        }

        @Override
        protected void updateProgress(double progress) {
            Platform.runLater(() -> uiProvider.updateProgress(progress));
        }

        @Override
        protected void createApplication(Class<Application> appClass) {
            runAndWait(() ->
            {
                try {
                    if (Application.class.isAssignableFrom(appClass)) {
                        app = appClass.newInstance();
                    } else {
                        throw new IllegalArgumentException(String.format("Supplied appClass %s was not a subclass of javafx.application.Application!", appClass));
                    }
                } catch (Throwable t) {
                    reportError("Error creating app class", t);
                }
            });
        }

        @Override
        protected void reportError(String title, Throwable error) {
            log.log(Level.WARNING, title, error);

            Platform.runLater(() ->
            {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(title);
                alert.setHeaderText(String.format("%s\n请查看日志", title));
                alert.getDialogPane().setPrefWidth(600);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(out);
                error.printStackTrace(writer);
                writer.close();
                TextArea text = new TextArea(out.toString());
                alert.getDialogPane().setContent(text);

                alert.showAndWait();
                Platform.exit();
            });
        }

        @Override
        protected void setupClassLoader(ClassLoader classLoader) {
            FXMLLoader.setDefaultClassLoader(classLoader);
            Platform.runLater(() -> Thread.currentThread().setContextClassLoader(classLoader));
        }


    };

    /**
     * Initialize the UI Provider by looking for an UIProvider inside the launcher
     * or fallback to the default UI.
     * <p>
     * A custom implementation must be embedded inside the launcher jar, and
     * /META-INF/services/fxlauncher.UIProvider must point to the new implementation class.
     * <p>
     * You must do this manually/in your build right around the "embed manifest" step.
     */
    @Override
    public void init() {
        Iterator<UIProvider> providers = ServiceLoader.load(UIProvider.class).iterator();
        uiProvider = providers.hasNext() ? providers.next() : new UpdateBox();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        stage = new Stage(StageStyle.TRANSPARENT);
        root = new StackPane();
        root.getStyleClass().add("container");
        final boolean[] filesUpdated = new boolean[1];

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);

        superLauncher.setupLogFile();
        superLauncher.checkSSLIgnoreflag();
        this.uiProvider.init(stage, superLauncher.phaseProptery());
        root.getChildren().add(uiProvider.createLoader());
        stage.show();

        new Thread(() -> {
            Thread.currentThread().setName("FXLauncher-Thread");
            try {
                update = superLauncher.updateManifest();
                if (update) {
                    createUpdateWrapper();
                }
                filesUpdated[0] = superLauncher.syncFiles();
            } catch (Exception ex) {
                log.log(Level.WARNING, String.format("Error during %s phase", superLauncher.getPhase()), ex);
                if (superLauncher.checkIgnoreUpdateErrorSetting()) {
                    superLauncher.reportError(String.format("Error during %s phase", superLauncher.getPhase()), ex);
                    System.exit(1);
                }
            }

            try {
                superLauncher.createApplicationEnvironment();
                launchAppFromManifest(filesUpdated[0]);
            } catch (Exception ex) {
                superLauncher.reportError(String.format("Error during %s phase", superLauncher.getPhase()), ex);
            }
        }).start();
    }

    private void launchAppFromManifest(boolean showWhatsnew) throws Exception {
        superLauncher.setPhase("正在初始化应用环境");

        try {
            initApplication();
        } catch (Throwable ex) {
            superLauncher.reportError("Error during app init", ex);
        }
        superLauncher.setPhase("正在启动中...");

        runAndWait(() ->
        {
            try {
                if (showWhatsnew && superLauncher.getManifest().whatsNewPage != null)
                    showWhatsNewDialog(superLauncher.getManifest().whatsNewPage);

                // Lingering update screen will close when primary stage is shown
                if (superLauncher.getManifest().lingeringUpdateScreen) {
                    primaryStage.showingProperty().addListener(observable -> {
                        if (stage.isShowing()) {
                            stage.close();
                        }
                    });
                } else {
                    stage.close();
                }

                startApplication();
            } catch (Throwable ex) {
                superLauncher.reportError("Failed to start application", ex);
            }
        });
    }

    private void showWhatsNewDialog(String whatsNewURL) {
        WebView view = new WebView();
        view.getEngine().load(whatsNewURL);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("What's new");
        alert.setHeaderText("New in this update");
        alert.getDialogPane().setContent(view);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void createUpdateWrapper() {
        superLauncher.setPhase("发现新版本,开始更新");

        Platform.runLater(() ->
        {
            Parent updater = uiProvider.createUpdater(superLauncher.getManifest());
            root.getChildren().clear();
            root.getChildren().add(updater);
        });
    }

    @Override
    public void stop() throws Exception {
        if (app != null)
            app.stop();
    }

    private void initApplication() throws Exception {
        if (app != null) {
            app.init();
        }
    }

    private void startApplication() throws Exception {
        if (app != null) {
            final LauncherParams params = new LauncherParams(getParameters(), superLauncher.getManifest());
            if (app.getParameters() != null) {
                app.getParameters().getNamed().putAll(params.getNamed());
                app.getParameters().getRaw().addAll(params.getRaw());
                app.getParameters().getUnnamed().addAll(params.getUnnamed());
            }
            primaryStage.setUserData(new UpdateInfo(superLauncher.getManifest().getWhatNew(), superLauncher.getManifest().getVersion(), update));
            log.info(primaryStage.getUserData().toString());
            PlatformImpl.setApplicationName(app.getClass());
            superLauncher.setPhase("应用初始化完成");
            app.start(primaryStage);
        } else {
            // Start any executable jar (i.E. Spring Boot);
            String firstFile = superLauncher.getManifest().files.get(0).file;
            log.info(String.format("No app class defined, starting first file (%s)", firstFile));
            Path cacheDir = superLauncher.getManifest().resolveCacheDir(getParameters().getNamed());
            String command = String.format("java -jar %s/%s", cacheDir.toAbsolutePath(), firstFile);
            log.info(String.format("Execute command '%s'", command));
            Runtime.getRuntime().exec(command);
        }
    }


    /**
     * Runs the specified {@link Runnable} on the
     * JavaFX application thread and waits for completion.
     *
     * @param action the {@link Runnable} to run
     * @throws NullPointerException if {@code action} is {@code null}
     */
    void runAndWait(Runnable action) {
        if (action == null)
            throw new NullPointerException("action");

        // run synchronously on JavaFX thread
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        // queue on JavaFX thread and wait for completion
        final CountDownLatch doneLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                doneLatch.countDown();
            }
        });

        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            // ignore exception
        }
    }
}
