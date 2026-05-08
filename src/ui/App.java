package ui;

import access.PermissionManager;
import auth.AuthService;
import auth.Session;
import logs.AuditLogger;
import storage.CloudManager;
import storage.FileMetadata;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class App extends Application {
    private static final long SESSION_TIMEOUT_MS = 10 * 60 * 1000L;

    private AuthService authService;
    private CloudManager cloudManager;
    private AuditLogger auditLogger;
    private Session session;
    private Timeline sessionWatchdog;

    private Stage primaryStage;
    private Scene loginScene;
    private Scene dashboardScene;
    private Label userLabel;
    private ComboBox<FileMetadata> verifyFileBox;
    private TabPane dashboardTabs;
    private TabPane authTabs;
    private Tab loginTab;
    private Tab registerTab;
    private TextField loginUsernameField;
    private PasswordField loginPasswordField;
    private TextField registerUsernameField;
    private TextField registerEmailField;
    private PasswordField registerPasswordField;
    private TextArea activityArea;

    private final ListView<FileMetadata> myFilesList = new ListView<>();
    private final ListView<FileMetadata> sharedFilesList = new ListView<>();

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        Path dataDir = Path.of("data");
        this.auditLogger = new AuditLogger(dataDir.resolve("logs.txt"));
        this.authService = new AuthService(dataDir.resolve("users.txt"), auditLogger);
        this.cloudManager = new CloudManager(dataDir, auditLogger, new PermissionManager());
        cloudManager.ensureDataLayout();
        authService.ensureDataLayout();

        this.loginScene = createLoginScene();
        this.dashboardScene = createDashboardScene();

        primaryStage.setTitle("SecureCloud");
        primaryStage.setScene(loginScene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    private Scene createLoginScene() {
        authTabs = new TabPane();
        loginTab = createLoginTab();
        registerTab = createRegisterTab();
        authTabs.getTabs().add(loginTab);
        authTabs.getTabs().add(registerTab);

        BorderPane root = new BorderPane();
        root.setCenter(authTabs);
        root.getStyleClass().add("root");

        Scene scene = new Scene(root, 900, 600);
        addStyles(scene);
        return scene;
    }

    private Tab createLoginTab() {
        Tab tab = new Tab("Login");
        tab.setClosable(false);

        Label title = new Label("SecureCloud Login");
        title.getStyleClass().add("title");

        loginUsernameField = new TextField();
        loginUsernameField.setPromptText("Username");
        loginPasswordField = new PasswordField();
        loginPasswordField.setPromptText("Password");

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("primary");
        loginBtn.setOnAction(e -> attemptLogin(loginUsernameField.getText(), loginPasswordField.getText()));

        VBox box = new VBox(12, title, loginUsernameField, loginPasswordField, loginBtn);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));
        box.getStyleClass().add("card");

        BorderPane pane = new BorderPane();
        pane.setCenter(box);
        pane.getStyleClass().add("root");

        tab.setContent(pane);
        return tab;
    }

    private Tab createRegisterTab() {
        Tab tab = new Tab("Register");
        tab.setClosable(false);

        Label title = new Label("Create Account");
        title.getStyleClass().add("title");

        registerUsernameField = new TextField();
        registerUsernameField.setPromptText("Username");
        registerEmailField = new TextField();
        registerEmailField.setPromptText("Email");
        registerPasswordField = new PasswordField();
        registerPasswordField.setPromptText("Password");

        Button registerBtn = new Button("Register");
        registerBtn.getStyleClass().add("primary");
        registerBtn.setOnAction(e -> {
            boolean ok = authService.register(
                    registerUsernameField.getText(),
                    registerEmailField.getText(),
                    registerPasswordField.getText()
            );
            if (ok) {
                showInfo("Registered", "Account created. You can login now.");
                registerUsernameField.clear();
                registerEmailField.clear();
                registerPasswordField.clear();
                if (authTabs != null && loginTab != null) {
                    authTabs.getSelectionModel().select(loginTab);
                }
            } else {
                showError("Registration failed", "Username already exists or invalid input.");
            }
        });

        VBox box = new VBox(12, title, registerUsernameField, registerEmailField, registerPasswordField, registerBtn);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));
        box.getStyleClass().add("card");

        BorderPane pane = new BorderPane();
        pane.setCenter(box);
        pane.getStyleClass().add("root");

        tab.setContent(pane);
        return tab;
    }

    private Scene createDashboardScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        userLabel = new Label();
        userLabel.getStyleClass().add("subtitle");

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> logout());

        HBox header = new HBox(12, userLabel, new Region(), logoutBtn);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.getStyleClass().add("header");

        dashboardTabs = new TabPane();
        dashboardTabs.getTabs().add(createMyFilesTab());
        dashboardTabs.getTabs().add(createSharedTab());
        dashboardTabs.getTabs().add(createUploadTab());
        dashboardTabs.getTabs().add(createVerifyTab());
        dashboardTabs.getTabs().add(createActivityTab());

        root.setTop(header);
        root.setCenter(dashboardTabs);

        Scene scene = new Scene(root, 1000, 700);
        addStyles(scene);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                logout();
            }
        });

        return scene;
    }

    private Tab createMyFilesTab() {
        Tab tab = new Tab("My Files");
        tab.setClosable(false);

        myFilesList.setCellFactory(list -> new FileMetaCell());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshLists());

        Button downloadBtn = new Button("Download");
        downloadBtn.getStyleClass().add("primary");
        downloadBtn.setOnAction(e -> downloadSelected(myFilesList));

        Button shareBtn = new Button("Share");
        shareBtn.setOnAction(e -> shareSelected(myFilesList));

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("danger");
        deleteBtn.setOnAction(e -> deleteSelected(myFilesList));

        HBox actions = new HBox(10, refreshBtn, downloadBtn, shareBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, actions, myFilesList);
        content.setPadding(new Insets(16));

        tab.setContent(content);
        return tab;
    }

    private Tab createSharedTab() {
        Tab tab = new Tab("Shared With Me");
        tab.setClosable(false);

        sharedFilesList.setCellFactory(list -> new FileMetaCell());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshLists());

        Button downloadBtn = new Button("Download");
        downloadBtn.getStyleClass().add("primary");
        downloadBtn.setOnAction(e -> downloadSelected(sharedFilesList));

        VBox content = new VBox(12, new HBox(10, refreshBtn, downloadBtn), sharedFilesList);
        content.setPadding(new Insets(16));

        tab.setContent(content);
        return tab;
    }

    private Tab createUploadTab() {
        Tab tab = new Tab("Upload");
        tab.setClosable(false);

        Label title = new Label("Upload a file");
        title.getStyleClass().add("subtitle");

        TextField pathField = new TextField();
        pathField.setPromptText("File path");
        pathField.setEditable(false);

        Button browseBtn = new Button("Browse");
        browseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            File file = chooser.showOpenDialog(primaryStage);
            if (file != null) {
                pathField.setText(file.getAbsolutePath());
            }
        });

        ComboBox<String> permBox = new ComboBox<>();
        permBox.getItems().addAll("private", "shared", "public");
        permBox.setValue("private");

        TextField folderField = new TextField();
        folderField.setPromptText("Folder (e.g., Documents)");

        Button uploadBtn = new Button("Upload");
        uploadBtn.getStyleClass().add("primary");
        uploadBtn.setOnAction(e -> {
            touchSession();
            boolean ok = cloudManager.uploadFile(session.getUsername(), pathField.getText(),
                    permBox.getValue(), folderField.getText());
            if (ok) {
                showInfo("Upload", "Upload complete.");
                refreshLists();
                pathField.clear();
                folderField.clear();
                permBox.setValue("private");
            } else {
                showError("Upload failed", "Check quota, permission, and file path.");
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.add(new Label("File"), 0, 0);
        grid.add(pathField, 1, 0);
        grid.add(browseBtn, 2, 0);
        grid.add(new Label("Permission"), 0, 1);
        grid.add(permBox, 1, 1);
        grid.add(new Label("Folder"), 0, 2);
        grid.add(folderField, 1, 2);
        GridPane.setHgrow(pathField, Priority.ALWAYS);
        GridPane.setHgrow(folderField, Priority.ALWAYS);

        VBox content = new VBox(16, title, grid, uploadBtn);
        content.setPadding(new Insets(24));
        content.getStyleClass().add("card");

        BorderPane pane = new BorderPane();
        pane.setCenter(content);
        pane.setPadding(new Insets(16));

        tab.setContent(pane);
        return tab;
    }

    private Tab createVerifyTab() {
        Tab tab = new Tab("Verify");
        tab.setClosable(false);

        Label title = new Label("Verify integrity and signature");
        title.getStyleClass().add("subtitle");

        verifyFileBox = new ComboBox<>();
        verifyFileBox.setPromptText("Select a file");
        verifyFileBox.setCellFactory(list -> new FileMetaCell());
        verifyFileBox.setButtonCell(new FileMetaCell());

        Button verifyBtn = new Button("Verify");
        verifyBtn.getStyleClass().add("primary");
        verifyBtn.setOnAction(e -> {
            FileMetadata meta = verifyFileBox.getValue();
            if (meta == null) {
                showError("Verify", "Select a file first.");
                return;
            }
            touchSession();
            CloudManager.VerifyResult result = cloudManager.verifyFile(meta.getId());
            if (result == null) {
                showError("Verify", "Verification failed.");
                return;
            }
            String status = "Integrity: " + (result.integrityOk ? "OK" : "FAILED") +
                    "\nSignature: " + (result.signatureOk ? "OK" : "FAILED") +
                    "\nOwner: " + result.owner;
            if (result.error != null) {
                showError("Verify", result.error + "\n\n" + status);
            } else {
                showInfo("Verified", status);
            }
        });

        Button refreshBtn = new Button("Refresh list");
        refreshBtn.setOnAction(e -> fillVerifyList());

        VBox content = new VBox(12, title, verifyFileBox, new HBox(10, verifyBtn, refreshBtn));
        content.setPadding(new Insets(24));
        content.getStyleClass().add("card");

        BorderPane pane = new BorderPane();
        pane.setCenter(content);
        pane.setPadding(new Insets(16));

        tab.setContent(pane);
        return tab;
    }

    private Tab createActivityTab() {
        Tab tab = new Tab("My Activity");
        tab.setClosable(false);

        activityArea = new TextArea();
        activityArea.setEditable(false);
        activityArea.setWrapText(true);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshActivity());

        VBox content = new VBox(12, refreshBtn, activityArea);
        content.setPadding(new Insets(16));

        tab.setContent(content);
        return tab;
    }

    private void attemptLogin(String username, String password) {
        Session created = authService.login(username, password);
        if (created == null) {
            showError("Login failed", "Invalid credentials or account locked.");
            return;
        }
        this.session = created;
        userLabel.setText("User: " + session.getUsername());
        startSessionWatchdog();
        refreshLists();
        fillVerifyList();
        refreshActivity();
        primaryStage.setScene(dashboardScene);
    }

    private void logout() {
        stopSessionWatchdog();
        session = null;
        userLabel.setText("");
        myFilesList.getItems().clear();
        sharedFilesList.getItems().clear();
        if (verifyFileBox != null) {
            verifyFileBox.getItems().clear();
        }
        if (activityArea != null) {
            activityArea.clear();
        }
        if (loginUsernameField != null) {
            loginUsernameField.clear();
        }
        if (loginPasswordField != null) {
            loginPasswordField.clear();
        }
        primaryStage.setScene(loginScene);
    }

    private void startSessionWatchdog() {
        stopSessionWatchdog();
        sessionWatchdog = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (session == null) {
                return;
            }
            long idle = System.currentTimeMillis() - session.getLastActiveEpochMs();
            if (idle > SESSION_TIMEOUT_MS) {
                showInfo("Session", "Session timed out. Please login again.");
                logout();
            }
        }));
        sessionWatchdog.setCycleCount(Timeline.INDEFINITE);
        sessionWatchdog.play();
    }

    private void stopSessionWatchdog() {
        if (sessionWatchdog != null) {
            sessionWatchdog.stop();
            sessionWatchdog = null;
        }
    }

    private void refreshLists() {
        if (session == null) {
            return;
        }
        myFilesList.getItems().setAll(cloudManager.listFilesOwnedBy(session.getUsername()));
        sharedFilesList.getItems().setAll(cloudManager.listFilesSharedWith(session.getUsername()));
        fillVerifyList();
        myFilesList.refresh();
        sharedFilesList.refresh();
        refreshActivity();
    }

    private void refreshActivity() {
        if (session == null || activityArea == null) {
            return;
        }
        Path logPath = Path.of("data").resolve("logs.txt");
        if (!Files.exists(logPath)) {
            activityArea.setText("");
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                String[] parts = line.split("\\|", 3);
                if (parts.length < 3) {
                    continue;
                }
                if (parts[1].equalsIgnoreCase(session.getUsername())) {
                    sb.append(parts[0]).append(" | ").append(parts[2]).append(System.lineSeparator());
                }
            }
            activityArea.setText(sb.toString());
        } catch (Exception ex) {
            activityArea.setText("Failed to read activity logs.");
        }
    }

    private void fillVerifyList() {
        if (session == null || verifyFileBox == null) {
            return;
        }
        List<FileMetadata> all = cloudManager.listFilesOwnedBy(session.getUsername());
        all.addAll(cloudManager.listFilesSharedWith(session.getUsername()));
        verifyFileBox.getItems().setAll(all);
    }

    private void downloadSelected(ListView<FileMetadata> list) {
        FileMetadata meta = list.getSelectionModel().getSelectedItem();
        if (meta == null) {
            showError("Download", "Select a file first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(meta.getOriginalName());
        File dest = chooser.showSaveDialog(primaryStage);
        if (dest == null) {
            return;
        }
        touchSession();
        boolean ok = cloudManager.downloadFile(session.getUsername(), meta.getId(), dest.getAbsolutePath());
        if (ok) {
            showInfo("Download", "File saved.");
        } else {
            showError("Download", "Failed to download.");
        }
    }

    private void shareSelected(ListView<FileMetadata> list) {
        FileMetadata meta = list.getSelectionModel().getSelectedItem();
        if (meta == null) {
            showError("Share", "Select a file first.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Share File");
        dialog.setHeaderText("Share with username");
        dialog.setContentText("Username:");
        dialog.showAndWait().ifPresent(target -> {
            touchSession();
            boolean ok = cloudManager.shareFile(session.getUsername(), meta.getId(), target.trim());
            if (ok) {
                showInfo("Share", "Shared with " + target + ".");
                refreshLists();
            } else {
                showError("Share", "Share failed.");
            }
        });
    }

    private void deleteSelected(ListView<FileMetadata> list) {
        FileMetadata meta = list.getSelectionModel().getSelectedItem();
        if (meta == null) {
            showError("Delete", "Select a file first.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete");
        confirm.setHeaderText("Delete file?");
        confirm.setContentText(meta.getOriginalName());
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                touchSession();
                boolean ok = cloudManager.deleteFile(session.getUsername(), meta.getId());
                if (ok) {
                    showInfo("Delete", "File deleted.");
                    refreshLists();
                } else {
                    showError("Delete", "Delete failed.");
                }
            }
        });
    }

    private void touchSession() {
        if (session != null) {
            session.touch();
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void addStyles(Scene scene) {
        Path css = Path.of("src", "ui", "style.css");
        if (Files.exists(css)) {
            scene.getStylesheets().add(css.toUri().toString());
        }
    }

    private static class FileMetaCell extends ListCell<FileMetadata> {
        private final VBox container = new VBox(2);
        private final Label name = new Label();
        private final Label owner = new Label();
        private final Label meta = new Label();

        public FileMetaCell() {
            name.getStyleClass().add("file-name");
            owner.getStyleClass().add("file-owner");
            meta.getStyleClass().add("file-meta");
            container.getChildren().addAll(name, owner, meta);
        }

        @Override
        protected void updateItem(FileMetadata item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                name.setText(item.getOriginalName());
                owner.setText("Owner: " + item.getOwner());
                meta.setText("ID: " + item.getId() + " | Perm: " + item.getPermission() +
                        " | Folder: " + item.getFolder());
                setGraphic(container);
            }
        }
    }
}
