package com.kyc.hawkeye;

public final class WordsearchSolverConfiguration {

    public final int darkCutoff = 256;
    public final double minBlobSeparation = 2;
    public final double minBlobSizeRelative = 0.005;
    public final double maxBlobSeparationRatio = 1.8;
    public final int blobBorderForOCR = 5;
    public final String datapathForOCR = "/usr/local/share/tessdata";
    public final String allowedCharsForOCR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public final int maxBatchForOCR = 50;
    public final String dictionaryPath = "/usr/share/dict/words";
    public final double maxWordBendAngle = 20;
    public final int minWordLength = 3;
}
