package com.lab.board3d;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/** نافذة اختيار حجر الترقية. تُستدعى من خيط JavaFX (من داخل نقرة الماوس). */
public final class PromotionDialog {

    private PromotionDialog() {}

    public static PieceType ask() {
        ButtonType queen = new ButtonType("وزير");
        ButtonType rook = new ButtonType("قلعة");
        ButtonType bishop = new ButtonType("فيل");
        ButtonType knight = new ButtonType("حصان");

        Alert dialog = new Alert(Alert.AlertType.NONE);
        dialog.setTitle("ترقية البيدق");
        dialog.setHeaderText("اختر الحجر الذي يتحول إليه البيدق");
        dialog.getButtonTypes().setAll(queen, rook, bishop, knight);

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isPresent()) {
            if (choice.get() == rook) return PieceType.ROOK;
            if (choice.get() == bishop) return PieceType.BISHOP;
            if (choice.get() == knight) return PieceType.KNIGHT;
        }
        return PieceType.QUEEN;
    }
}
