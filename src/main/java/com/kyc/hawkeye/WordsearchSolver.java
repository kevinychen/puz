
package com.kyc.hawkeye;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class WordsearchSolver {

    private final BufferedImage image;
    private final WordsearchSolverConfiguration config;
    private final int width, height;
    private final Set<String> dictionary;

    public WordsearchSolver(BufferedImage image, WordsearchSolverConfiguration config) throws IOException {
        this.image = image;
        this.config = config;
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.dictionary = Files.lines(Paths.get(config.dictionaryPath)).map(s -> s.trim().toUpperCase()).collect(Collectors.toSet());
    }

    /**
     * Returns a map from the word (duplicates are removed) to the list of points in the image that
     * compose the word, in the format [x1, y1, x2, y2, x3, y3, ... x_n, y_n].
     */
    public Map<String, List<Integer>> search() throws TesseractException {
        boolean[][] darkPixels = getDarkPixels();
        List<Blob> blobs = findBlobs(darkPixels);
        List<Blob> cleanedBlobs = getCleanedBlobs(blobs);
        Multimap<Blob, Blob> blobGraph = getBlobGraph(cleanedBlobs);
        Map<Blob, Character> letters = getLetters(cleanedBlobs);
        List<List<Blob>> validWords = findValidWords(blobGraph, letters);
        List<List<Blob>> cleanedValidWords = getCleanedValidWords(validWords);
        return cleanedValidWords.stream()
                .collect(Collectors.toMap(
                    word -> Joiner.on("").join(Lists.transform(word, letters::get)),
                    word -> word.stream()
                            .flatMap(b -> b.points.stream().flatMap(p -> Stream.of(p.x, p.y)))
                            .collect(Collectors.toList()),
                    (word1, word2) -> word1));
    }

    boolean[][] getDarkPixels() {
        int[] rgbs = image.getRGB(0, 0, width, height, null, 0, width);
        boolean[][] isDark = new boolean[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++) {
                Color color = new Color(rgbs[y * width + x]);
                isDark[x][y] = color.getRed() + color.getGreen() + color.getBlue() < config.darkCutoff;
                image.setRGB(x, y, (isDark[x][y] ? Color.BLACK : Color.WHITE).getRGB());
            }
        return isDark;
    }

    List<Blob> findBlobs(boolean[][] darkPixels) {
        List<Blob> blobs = new ArrayList<>();
        boolean[][] seen = new boolean[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                if (darkPixels[x][y] && !seen[x][y]) {
                    ArrayDeque<Point> q = new ArrayDeque<Point>();
                    q.add(new Point(x, y));
                    List<Point> points = new ArrayList<>();
                    while (!q.isEmpty()) {
                        Point p = q.pop();
                        if (p.x >= 0 && p.x < width && p.y >= 0 && p.y < height && darkPixels[p.x][p.y] && !seen[p.x][p.y]) {
                            seen[p.x][p.y] = true;
                            points.add(p);
                            for (int dx = -1; dx <= 1; dx++)
                                for (int dy = -1; dy <= 1; dy++)
                                    q.push(new Point(p.x + dx, p.y + dy));
                        }
                    }
                    blobs.add(new Blob(points));
                }

        return blobs;
    }

    List<Blob> getCleanedBlobs(List<Blob> blobs) {
        Set<Blob> cleanedBlobs = new HashSet<>();
        Function<Blob, Optional<Blob>> findCloseBlob = blob -> {
            for (Blob closeBlob : cleanedBlobs)
                if (blob.minX < closeBlob.maxX + config.minBlobSeparation && closeBlob.minX < blob.maxX + config.minBlobSeparation
                        && blob.minY < closeBlob.maxY + config.minBlobSeparation && closeBlob.minY < blob.maxY + config.minBlobSeparation) {
                    return Optional.of(closeBlob);
                }
            return Optional.empty();
        };
        for (Blob blob : blobs) {
            Optional<Blob> closeBlob;
            while ((closeBlob = findCloseBlob.apply(blob)).isPresent()) {
                cleanedBlobs.remove(closeBlob.get());
                blob = blob.merge(closeBlob.get());
            }
            cleanedBlobs.add(blob);
        }
        double minBlobSize = config.minBlobSizeRelative * Math.max(width, height);
        return cleanedBlobs.stream()
            .filter(blob -> blob.getWidth() > minBlobSize && blob.getHeight() > minBlobSize)
            .collect(Collectors.toList());
    }

    Multimap<Blob, Blob> getBlobGraph(List<Blob> blobs) {
        Multimap<Blob, Blob> blobGraph = ArrayListMultimap.create();
        for (Blob blob : blobs) {
            double minDist = blobs.stream()
                    .filter(neighborBlob -> !blob.equals(neighborBlob))
                    .mapToDouble(neighborBlob -> blob.center.distance(neighborBlob.center))
                    .min()
                    .getAsDouble();
            blobs.stream()
                    .filter(neighborBlob -> !blob.equals(neighborBlob))
                    .filter(neighborBlob -> blob.center.distance(neighborBlob.center) < minDist * config.maxBlobSeparationRatio)
                    .forEach(neighborBlob -> blobGraph.put(blob, neighborBlob));
        }
        return blobGraph;
    }

    Map<Blob, Character> getLetters(List<Blob> blobs) throws TesseractException {
        int border = config.blobBorderForOCR;

        Tesseract ocr = Tesseract.getInstance();
        ocr.setDatapath(config.datapathForOCR);
        ocr.setPageSegMode(7);
        ocr.setTessVariable("tessedit_char_whitelist", config.allowedCharsForOCR);
        ocr.setTessVariable("load_system_dawg", "0");
        ocr.setTessVariable("load_freq_dawg", "0");

        Map<Blob, Character> letters = new HashMap<>();
        for (Blob blob : blobs)
            if (!letters.containsKey(blob)) {
                List<Blob> similarHeight = new ArrayList<>();
                int totalWidth = 0, totalHeight = 0;
                for (Blob similarBlob : blobs)
                    if (!letters.containsKey(similarBlob) && Math.abs(blob.getHeight() - similarBlob.getHeight()) <= border) {
                        similarHeight.add(similarBlob);
                        totalWidth += similarBlob.getWidth() + 2 * border;
                        totalHeight = Math.max(totalHeight, similarBlob.getHeight() + 2 * border);
                        if (similarHeight.size() == config.maxBatchForOCR)
                            break;
                    }
                BufferedImage buffer = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
                Graphics g = buffer.getGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, totalWidth, totalHeight);
                int x = 0;
                for (Blob similarBlob : similarHeight) {
                    BufferedImage letter =
                            image.getSubimage(similarBlob.minX, similarBlob.minY, similarBlob.getWidth(), similarBlob.getHeight());
                    g.drawImage(letter, x + border, border, null);
                    x += similarBlob.getWidth() + 2 * border;
                }
                String text = "";
                for (char c : ocr.doOCR(buffer).toCharArray())
                    if (config.allowedCharsForOCR.contains(c + ""))
                        text += c;
                for (int i = 0; i < similarHeight.size(); i++)
                    letters.put(similarHeight.get(i), i < text.length() ? text.charAt(i) : ' ');
            }

        return letters;
    }

    List<List<Blob>> findValidWords(Multimap<Blob, Blob> blobGraph, Map<Blob, Character> letters) {
        List<List<Blob>> validWords = new ArrayList<>();
        for (Blob startBlob : blobGraph.keySet())
            findWordsHelper(Lists.newArrayList(startBlob), blobGraph, letters, validWords);
        return validWords;
    }

    private void findWordsHelper(
            List<Blob> word, Multimap<Blob, Blob> blobGraph, Map<Blob, Character> letters, List<List<Blob>> validWords) {
        if (dictionary.contains(Joiner.on("").join(Lists.transform(word, letters::get))))
            validWords.add(ImmutableList.copyOf(word));
        Blob blob = word.get(word.size() - 1);
        for (Blob neighborBlob : blobGraph.get(blob)) {
            boolean good = word.size() == 1;
            if (!good) {
                double bendAngle =
                        Math.abs(word.get(word.size() - 2).center.headingTo(blob.center) - blob.center.headingTo(neighborBlob.center));
                if (bendAngle < config.maxWordBendAngle || bendAngle > 360 - config.maxWordBendAngle)
                    good = true;
            }
            if (good) {
                word.add(neighborBlob);
                findWordsHelper(word, blobGraph, letters, validWords);
                word.remove(word.size() - 1);
            }
        }
    }

    List<List<Blob>> getCleanedValidWords(List<List<Blob>> validWords) {
        List<List<Blob>> cleanedValidWords = new ArrayList<>();
        for (List<Blob> word : validWords) {
            Predicate<List<Blob>> isOuterWord = outerWord -> word.stream().allMatch(blob -> outerWord.contains(blob));
            if (word.size() >= config.minWordLength && validWords.stream()
                    .filter(outerWord -> !word.equals(outerWord))
                    .allMatch(outerWord -> !isOuterWord.apply(outerWord)))
                cleanedValidWords.add(word);
        }
        return cleanedValidWords;
    }
}
