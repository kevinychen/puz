package com.kyc.hawkeye;

public final class WordsearchSolverConfiguration {

    public int darkCutoff = 256;
    public int minBlobSeparation = 2;
    public double minBlobSizeRelative = 0.005;
    public double maxBlobSeparationRatio = 1.8;
    public int blobBorderForOCR = 5;
    public String datapathForOCR = "/usr/local/share/tessdata";
    public String allowedCharsForOCR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public int maxBatchForOCR = 50;
    public String dictionaryPath = "/usr/share/dict/words";
    public double maxWordBendAngle = 20;
    public int minWordLength = 3;
}
