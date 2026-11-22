package com.lab.board3d;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/** نافذة اختيار لون اللاعب في بداية كل لعبة. تُستدعى من خيط JavaFX. */
public final class ColorDialog {

    private ColorDialog() {}

    public static PieceColor ask() {
        ButtonType white = new ButtonType("أبيض (تبدأ أنت)");
        ButtonType black = new ButtonType("أسود (يبدأ الحاسوب)");

        Alert dialog = new Alert(Alert.AlertType.NONE);
        dialog.setTitle("لعبة جديدة");
        dialog.setHeaderText("بأي لون تريد أن تلعب؟");
        dialog.getButtonTypes().setAll(white, black);

        Optional<ButtonType> choice = dialog.showAndWait();
        return choice.isPresent() && choice.get() == black ? PieceColor.BLACK : PieceColor.WHITE;
    }
}
