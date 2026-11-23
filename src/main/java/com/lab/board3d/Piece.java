package com.lab.board3d;

import javafx.scene.Group;

/** حجر موجود على الرقعة (للقراءة فقط من جهة الخوارزمية). */
public final class Piece {

    private final PieceType type;
    private final PieceColor color;
    private Square square;
    final Group node;

    Piece(PieceType type, PieceColor color, Square square, Group node) {
        this.type = type;
        this.color = color;
        this.square = square;
        this.node = node;
    }

    public PieceType getType()   { return type; }
    public PieceColor getColor() { return color; }
    public Square getSquare()    { return square; }

    /** مثل WHITE_KNIGHT */
    public String getName()      { return color + "_" + type; }

    void setSquare(Square s)     { this.square = s; }

    @Override
    public String toString() {
        return getName() + "@" + square;
    }
}
