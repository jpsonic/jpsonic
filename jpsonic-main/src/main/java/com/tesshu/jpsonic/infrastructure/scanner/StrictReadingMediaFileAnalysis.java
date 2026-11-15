package com.tesshu.jpsonic.infrastructure.scanner;

import com.tesshu.jpsonic.domain.contract.StrictReadingMetaAnalysis;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;

/**
 * Analyzes a {@link MediaFile} and sets its sort and reading values.
 *
 * <p>
 * This is the initial analysis step in the metadata processing pipeline. The
 * normalized sort values are used to derive {@code Reading}, which is used for
 * subsequent sorting and indexing. When no sort tag is available, the reading
 * is derived from the media file name and used as the basis for the
 * corresponding sort value.
 */
@FunctionalInterface
interface StrictReadingMediaFileAnalysis extends StrictReadingMetaAnalysis {

    // spotless:off
    default void analyze(MediaFile m) {
        analyzeStrictReadingMeta(
                m.getArtist(),
                m.getArtistSort(),
                m::setArtistSort,
                m::setArtistReading);
        analyzeStrictReadingMeta(
                m.getAlbumArtist(),
                m.getAlbumArtistSort(),
                m::setAlbumArtistSort,
                m::setAlbumArtistReading);
        analyzeStrictReadingMeta(
                m.getAlbumName(),
                m.getAlbumSort(),
                m::setAlbumSort,
                m::setAlbumReading);
    }
    // spotless:on
}
