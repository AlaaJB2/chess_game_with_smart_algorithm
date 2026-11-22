package com.lab.board3d;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * الخطوة 4: الرقعة + 32 حجراً (ماهوغني وكريمي)، والنقر على حجر ثم على مربع لتحريكه.
 *
 * التحكم:
 *  - سحب بزر الماوس الأيسر : تدوير
 *  - سحب بزر الماوس الأيمن : تحريك (Pan)
 *  - عجلة الماوس            : تقريب / تبعيد
 *  - نقرة على حجر ثم نقرة على مربع : تحريك الحجر
 *
 * ملاحظة: في JavaFX محور Y يتجه للأسفل، لذلك ما هو "فوق" الرقعة له قيمة Y سالبة.
 */
public class Main extends Application {

    private static final double WIDTH = 1100;
    private static final double HEIGHT = 750;

    // ---- ملفات STL (داخل مجلد models بجانب pom.xml) ----
    private static final Path MODELS_DIR = Path.of("models");
    private static final String BOARD_FILE = "_chiquier_final.stl";

    // ---- إعدادات الرقعة ----
    private static final double BOARD_SCALE = 3.0;      // 256 وحدة في الملف -> 768 في المشهد
    private static final boolean DARK_ON_A1 = true;     // المربع a1 داكن (false = فاتح)

    private final Rotate rotateX = new Rotate(-45, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
    private final Translate pan = new Translate(0, 0, 0);

    private double anchorX, anchorY;
    private double anchorAngleX, anchorAngleY;
    private double anchorPanX, anchorPanY;

    private GameController game;
    private PythonEngine python;

    private SoundPlayer sounds;
    private boolean choosingColor;
    @Override
    public void start(Stage stage) {
        // ---- الإضاءة ----
        AmbientLight ambient = new AmbientLight(Color.rgb(75, 75, 80));

        PointLight key = new PointLight(Color.rgb(150, 150, 150));
        key.setTranslateX(-500);
        key.setTranslateY(-1000);
        key.setTranslateZ(-700);

        PointLight fill = new PointLight(Color.rgb(60, 64, 76));
        fill.setTranslateX(700);
        fill.setTranslateY(-700);
        fill.setTranslateZ(600);

        Group world = new Group(ambient, key, fill);

        // ---- الرقعة والأحجار ----
        BoardView view = null;
        try {
            view = new BoardView(MODELS_DIR, BOARD_FILE, BOARD_SCALE, DARK_ON_A1);
            world.getChildren().add(view.getNode());
            game = new GameController(view);
            game.setStatusListener(s -> stage.setTitle("Chess - " + s + "   [N = لعبة جديدة | M = كتم]"));
            python = new PythonEngine("python", Path.of("python", "engine_server.py"), 10_000);
            game.setEngine(python);
            game.setPromotionChooser(PromotionDialog::ask);
            sounds = new SoundPlayer(Path.of("sounds"));
            game.setSoundListener(sounds::play);
            game.setOrientationListener(color -> rotateY.setAngle(color == PieceColor.WHITE ? 0 : 180));
            python.warmUp();
            stage.setOnCloseRequest(e -> { python.close(); sounds.close(); });
        } catch (IOException ex) {
            System.out.println("تعذّر تحميل الرقعة/الأحجار من: " + MODELS_DIR.toAbsolutePath());
            ex.printStackTrace();
        }

        // ---- الكاميرا ----
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(1);
        camera.setFarClip(10000);
        camera.setTranslateZ(-1500);

        Group cameraPivot = new Group(camera);
        // الترتيب: يُطبَّق rotateX أولاً ثم rotateY ثم pan
        cameraPivot.getTransforms().addAll(pan, rotateY, rotateX);

        Group root = new Group(world, cameraPivot);

        Scene scene = new Scene(root, WIDTH, HEIGHT, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.rgb(30, 34, 42));
        scene.setCamera(camera);

        installMouseControls(scene, camera);
        scene.setOnKeyPressed(e -> {
            if (game == null) return;
            if (e.getCode() == KeyCode.N && !choosingColor) {
                choosingColor = true;
                try { game.newGame(ColorDialog.ask()); } finally { choosingColor = false; }
            } else if (e.getCode() == KeyCode.M && sounds != null) {
                sounds.toggleMute();
            }
        });
        if (view != null) view.install(scene);

        stage.setTitle("Board 3D - Step 4");
        stage.setScene(scene);
        stage.show();
        if (game != null) Platform.runLater(() -> game.start(ColorDialog.ask()));
    }

    private void installMouseControls(Scene scene, PerspectiveCamera camera) {
        scene.setOnMousePressed(e -> {
            anchorX = e.getSceneX();
            anchorY = e.getSceneY();
            anchorAngleX = rotateX.getAngle();
            anchorAngleY = rotateY.getAngle();
            anchorPanX = pan.getX();
            anchorPanY = pan.getZ();
        });

        scene.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - anchorX;
            double dy = e.getSceneY() - anchorY;

            if (e.getButton() == MouseButton.PRIMARY) {
                rotateY.setAngle(anchorAngleY + dx * 0.4);
                double newX = anchorAngleX - dy * 0.4;
                rotateX.setAngle(Math.max(-89, Math.min(10, newX)));
            } else if (e.getButton() == MouseButton.SECONDARY) {
                pan.setX(anchorPanX - dx);
                pan.setZ(anchorPanY + dy);
            }
        });

        scene.setOnScroll(e -> {
            double z = camera.getTranslateZ() + e.getDeltaY() * 2;
            camera.setTranslateZ(Math.max(-4000, Math.min(-200, z)));
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
