package com.ecpay.evolution.internal.parser;

class DelimiterDetector {

    static final char[] CANDIDATES = {';', ',', '\t', '|'};

    static char detect(String sampleLine) {
        return findMostFrequentCandidate(sampleLine);
    }

    private static char findMostFrequentCandidate(String line) {
        char best = ',';
        long max = 0;
        for (char candidate : CANDIDATES) {
            long count = countOccurrencesInLine(line, candidate);
            if (count > max) {
                max = count;
                best = candidate;
            }
        }
        return best;
    }

    private static long countOccurrencesInLine(String line, char character) {
        return line.chars().filter(ch -> ch == character).count();
    }
}
