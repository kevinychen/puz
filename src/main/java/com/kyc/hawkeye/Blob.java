package com.kyc.hawkeye;

import java.util.ArrayList;
import java.util.List;

final class Blob {

    final List<Point> points;
    final int minX, maxX, minY, maxY;
    final Point center;

    Blob(List<Point> points) {
        this.points = points;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Point p : points) {
            if (p.x < minX)
                minX = p.x;
            if (p.x > maxX)
                maxX = p.x;
            if (p.y < minY)
                minY = p.y;
            if (p.y > maxY)
                maxY = p.y;
        }
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.center = new Point((minX + maxX) / 2, (minY + maxY) / 2);
    }

    int getWidth() {
        return maxX - minX + 1;
    }

    int getHeight() {
        return maxY - minY + 1;
    }

    Blob merge(Blob other) {
        List<Point> mergedPoints = new ArrayList<>();
        mergedPoints.addAll(points);
        mergedPoints.addAll(other.points);
        return new Blob(mergedPoints);
    }
}
