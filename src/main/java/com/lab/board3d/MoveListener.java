package com.lab.board3d;

/**
 * يُستدعى عندما يضغط اللاعب على حجر ثم على مربع.
 * أعِد true للسماح بالحركة (تُنفَّذ بالأنيميشن، مع أكل الحجر الموجود إن وُجد)،
 * أو false لرفضها (يبقى الحجر مكانه).
 */
@FunctionalInterface
public interface MoveListener {
    boolean onMoveRequested(Piece piece, Square from, Square to);
}
