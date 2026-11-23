package com.lab.board3d;

/**
 * مربع على الرقعة.
 *  file: العمود 0..7  (0 = a ، 7 = h)
 *  rank: الصف   0..7  (0 = الصف 1 ، 7 = الصف 8)
 *
 * مثال: new Square(4, 1) أو Square.of("e2")  (كلاهما نفس المربع e2)
 */
public record Square(int file, int rank) {

    public Square {
        if (!isValid(file, rank)) {
            throw new IllegalArgumentException("مربع خارج الرقعة: file=" + file + ", rank=" + rank);
        }
    }

    public static boolean isValid(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }

    /** من نص مثل "e4". */
    public static Square of(String name) {
        if (name == null || name.length() != 2) {
            throw new IllegalArgumentException("اسم مربع غير صالح: " + name);
        }
        char f = Character.toLowerCase(name.charAt(0));
        char r = name.charAt(1);
        return new Square(f - 'a', r - '1');
    }

    @Override
    public String toString() {
        return "" + (char) ('a' + file) + (char) ('1' + rank);
    }
}
