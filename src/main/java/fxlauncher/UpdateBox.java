package fxlauncher;

import fxlauncher.gui.JFXSpinner;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;


public class UpdateBox implements UIProvider {
    private ProgressBar progressBar;
    private Stage stage;
    private BorderPane root;
    private Label header;
    private StringProperty phase;

    @Override
    public void init(Stage stage, StringProperty phaseProperty) {
        this.stage = stage;
        this.phase = phaseProperty;
        stage.getScene().getStylesheets().add(getClass().getResource("/main.css").toExternalForm());
    }

    @Override
    public Parent createLoader() {
        root = new BorderPane();
        root.setPrefSize(300, 300);
        root.getStyleClass().add("updater");
        header = new Label("Uncle小说");
        header.getStyleClass().add("h1");
        root.setTop(header);
        Label label = new Label("检测更新中...");
        label.getStyleClass().add("phase");
        label.textProperty().bind(phase);
        HBox box = new HBox();
        JFXSpinner spinner = new JFXSpinner();
        spinner.setRadius(5);
        box.getChildren().addAll(spinner, label);
        box.setAlignment(Pos.CENTER);
        root.setBottom(box);
        BorderPane.setAlignment(label, Pos.CENTER);
        BorderPane.setAlignment(header, Pos.CENTER);
        return root;
    }

    @Override
    public Parent createUpdater(FXManifest manifest) {
        stage.setTitle("更新中...");
        progressBar = new ProgressBar();
        root.getChildren().clear();
        ScrollPane topPane = new ScrollPane();
        topPane.getStyleClass().add("top-scroll");
        topPane.setFitToWidth(true);
        Label content = new Label();
        content.setText(manifest.getWhatNew());
        topPane.setContent(content);
        content.getStyleClass().add("what-new");
        root.setTop(topPane);
        root.setPadding(new Insets(0));
        Label label = new Label(manifest.updateText);
        label.getStyleClass().add("update-text-label");
        root.setCenter(label);
        root.setBottom(progressBar);
        BorderPane.setAlignment(progressBar, Pos.CENTER);
        Timeline tl = new Timeline(
                new KeyFrame(Duration.seconds(4), new KeyValue(header.scaleXProperty(), 1.5)),
                new KeyFrame(Duration.seconds(4), new KeyValue(header.scaleYProperty(), 1.5))
        );
        tl.play();
        return root;
    }

    @Override
    public void updateProgress(double progress) {
        progressBar.setProgress(progress);
    }


}