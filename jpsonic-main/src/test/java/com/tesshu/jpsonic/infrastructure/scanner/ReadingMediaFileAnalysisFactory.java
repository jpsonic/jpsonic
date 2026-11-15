package com.tesshu.jpsonic.infrastructure.scanner;

import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;

public class ReadingMediaFileAnalysisFactory {

    private ReadingMediaFileAnalysisFactory() {
        //
    }

    public static StrictReadingMediaFileAnalysis createStrictReadingMediaFileAnalysis(
            MetadataReadingProcessor readingProcessor) {
        return readingProcessor::analyzeStrictReadingMeta;
    }

}
