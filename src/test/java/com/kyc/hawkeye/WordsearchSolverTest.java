package com.kyc.hawkeye;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

public class WordsearchSolverTest {

    @Test
    public void test() throws Exception {
        BufferedImage image = ImageIO.read(ClassLoader.getSystemResource("wordsearch.png"));
        WordsearchSolverConfiguration config = new WordsearchSolverConfiguration();
        WordsearchSolver solver = new WordsearchSolver(image, config);
        Map<String, List<Integer>> result = solver.search();
        Assert.assertTrue(result.containsKey("PLAINSMAN"));
        Assert.assertTrue(result.containsKey("FOOTBALL"));
        Assert.assertTrue(result.containsKey("FEARLESS"));
        Assert.assertFalse(result.containsKey("PLAINS"));
    }

    @Test
    public void play() throws Exception {
        BufferedImage image = ImageIO.read(ClassLoader.getSystemResource("wordsearch.png"));
        WordsearchSolverConfiguration config = new WordsearchSolverConfiguration();
        WordsearchSolver solver = new WordsearchSolver(image, config);

        boolean[][] darkPixels = solver.getDarkPixels();
        List<Blob> blobs = solver.findBlobs(darkPixels);
        List<Blob> cleanedBlobs = solver.getCleanedBlobs(blobs);
        Multimap<Blob, Blob> blobGraph = solver.getBlobGraph(cleanedBlobs);
        Map<Blob, Character> letters = solver.getLetters(cleanedBlobs);
        List<List<Blob>> validWords = solver.findValidWords(blobGraph, letters);
        List<List<Blob>> cleanedValidWords = solver.getCleanedValidWords(validWords);
        System.out.println("Found " + cleanedValidWords.size() + " words");

        annotateImage(image, Lists.transform(cleanedBlobs, blob -> blob.center), 2);
        drawGraph(image, blobGraph);
        annotateLetters(image, letters);
        openImage(image);
    }

    private void openImage(BufferedImage image) throws IOException {
        File outputImage = File.createTempFile("wordsearch", ".png");
        ImageIO.write(image, "png", outputImage);
        Desktop.getDesktop().open(outputImage);
    }

    private void annotateImage(BufferedImage image, List<Point> points, int thickness) throws IOException {
        for (Point p : points)
            for (int dx = -thickness; dx <= thickness; dx++)
                for (int dy = -thickness; dy <= thickness; dy++)
                    if (p.x + dx >= 0 && p.x + dx < image.getWidth() && p.y + dy >= 0 && p.y + dy < image.getHeight())
                        image.setRGB(p.x + dx, p.y + dy, Color.RED.getRGB());
    }

    private void drawGraph(BufferedImage image, Multimap<Blob, Blob> blobGraph) {
        Graphics g = image.getGraphics();
        for (Map.Entry<Blob, Blob> edge : blobGraph.entries()) {
            Point startCenter = edge.getKey().center, endCenter = edge.getValue().center;
            if (blobGraph.containsEntry(edge.getValue(), edge.getKey()))
                g.setColor(Color.RED);
            else if (edge.getKey().center.x < edge.getValue().center.x)
                g.setColor(Color.GREEN);
            else
                g.setColor(Color.BLUE);
            g.drawLine(startCenter.x, startCenter.y, endCenter.x, endCenter.y);
        }
    }

    private void annotateLetters(BufferedImage image, Map<Blob, Character> letters) {
        Graphics g = image.getGraphics();
        letters.forEach((blob, c) -> {
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 48));
            g.drawString(c.toString(), blob.center.x, blob.center.y);
        });
    }
}
