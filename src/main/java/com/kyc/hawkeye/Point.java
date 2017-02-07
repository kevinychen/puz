package com.kyc.hawkeye;

final class Point {

    final int x;
    final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    double distance(Point other) {
        return Math.hypot(x - other.x, y - other.y);
    }

    double headingTo(Point other) {
        return Math.toDegrees(Math.atan2(other.y - y, other.x - x));
    }
}
