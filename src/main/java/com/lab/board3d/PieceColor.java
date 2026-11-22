package com.lab.board3d;

/** لون الحجر: WHITE يبدأ من الصف 1 (الأقرب للكاميرا)، BLACK من الصف 8. */
public enum PieceColor {
    WHITE, BLACK;

    public PieceColor opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
