/*
 * This file is part of Jpsonic.
 *
 * Jpsonic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Jpsonic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.result.ArtistSortCandidate;
import com.tesshu.jpsonic.persistence.result.ArtistSortCandidate.TargetField;
import com.tesshu.jpsonic.persistence.result.DuplicateSort;
import com.tesshu.jpsonic.persistence.result.SortCandidate;
import org.apache.commons.lang3.exception.UncheckedException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

/**
 * A service that recalculates and reassigns the {@code sortOrder} for all media
 * files after the intermediate analysis phase of the scan process.
 *
 * <p>
 * Sort order is determined based on user-defined policies or system-default
 * rules. Tracks are grouped by album, and albums are grouped by artist, with
 * ordering applied within each group.
 * </p>
 *
 * <h3>Processing Steps</h3>
 * <ol>
 * <li>Retrieve all media files and group them by album and artist</li>
 * <li>Sort each group according to the specified comparison rules</li>
 * <li>Update each media file with a corresponding {@code sortOrder} value</li>
 * </ol>
 *
 * @see WritableMediaFileService
 */
@Service
@DependsOn({ "mediaFileDao", "metadataReadingProcessor", "musicIndexProviderImpl" })
public class SortProcedureService {

    private static final int REPEAT_WAIT_MILLISECONDS = 50;
    private static final int SINGLE_FIELD_UPDATE_FOR_ROW = 1;

    private final MediaFileDao mediaFileDao;
    private final MetadataReadingProcessor readingProcessor;
    private final MusicIndexProviderImpl musicIndexProvider;

    public SortProcedureService(MediaFileDao mediaFileDao,
            MetadataReadingProcessor readingProcessor, MusicIndexProviderImpl musicIndexProvider) {
        super();
        this.mediaFileDao = mediaFileDao;
        this.readingProcessor = readingProcessor;
        this.musicIndexProvider = musicIndexProvider;
    }

    void clearMemoryCache() {
        readingProcessor.clear();
    }

    private void repeatWait() {
        try {
            Thread.sleep(REPEAT_WAIT_MILLISECONDS);
        } catch (InterruptedException e) {
            throw new UncheckedException(e);
        }
    }

    List<Integer> compensateSortOfAlbum(List<MusicFolder> folders) {
        List<SortCandidate> cands = mediaFileDao.getNoSortAlbums(folders);
        cands
            .forEach(cand -> readingProcessor
                .analyzeSortCandidate(cand.getName(), cand.getSort(), cand::setSort,
                        cand::setReading));
        return updateAlbumSort(cands);
    }

    List<Integer> compensateSortOfArtist(List<MusicFolder> folders) {
        List<ArtistSortCandidate> cands = mediaFileDao.getNoSortPersons(folders);
        cands
            .forEach(cand -> readingProcessor
                .analyzeSortCandidate(cand.getName(), cand.getSort(), cand::setSort,
                        cand::setReading));
        return updateArtistSort(cands);
    }

    List<Integer> copySortOfAlbum(List<MusicFolder> folders) {
        List<SortCandidate> cands = mediaFileDao.getCopyableSortAlbums(folders);
        cands
            .forEach(cand -> readingProcessor
                .analyzeSortCandidate(cand.getName(), cand.getSort(), cand::setSort,
                        cand::setReading));
        return updateAlbumSort(cands);
    }

    List<Integer> copySortOfArtist(List<MusicFolder> folders) {
        List<ArtistSortCandidate> cands = mediaFileDao.getCopyableSortPersons(folders);
        cands
            .forEach(cand -> readingProcessor
                .analyzeSortCandidate(cand.getName(), cand.getSort(), cand::setSort,
                        cand::setReading));
        return updateArtistSort(cands);
    }

    List<Integer> mergeSortOfAlbum(List<MusicFolder> folders) {
        List<SortCandidate> cands = mediaFileDao.getDuplicateSortAlbums(folders);
        cands
            .forEach(cand -> readingProcessor
                .analyzeSortCandidate(cand.getName(), cand.getSort(), cand::setSort,
                        cand::setReading));
        return updateAlbumSort(cands);
    }

    List<Integer> mergeSortOfArtist(List<MusicFolder> folders) {
        List<DuplicateSort> dups = mediaFileDao.getDuplicateSortPersons(folders);
        if (dups.isEmpty()) {
            return Collections.emptyList();
        }
        List<ArtistSortCandidate> cands = mediaFileDao.getSortCandidatePersons(dups);
        cands
            .forEach(cand -> readingProcessor
                .analyzeSortCandidate(cand.getName(), cand.getSort(), cand::setSort,
                        cand::setReading));
        return updateArtistSort(cands);
    }

    private List<Integer> updateAlbumSort(@NonNull List<SortCandidate> cands) {
        if (cands.isEmpty()) {
            return Collections.emptyList();
        }
        for (int i = 0; i < cands.size(); i++) {
            if (i % 20_000 == 0) {
                repeatWait();
            }
            mediaFileDao.updateAlbumSort(cands.get(i));
        }
        return cands.stream().map(SortCandidate::getTargetId).collect(Collectors.toList());
    }

    private List<Integer> updateArtistSort(@NonNull List<ArtistSortCandidate> cands) {
        if (cands.isEmpty()) {
            return Collections.emptyList();
        }

        cands
            .stream()
            .filter(cand -> cand.getTargetType() == MediaType.DIRECTORY)
            .filter(cand -> cand.getTargetField() == TargetField.ARTIST)
            .forEach(cand -> cand
                .setMusicIndex(musicIndexProvider.getParser().getIndex(cand).index()));

        Map<Integer, List<ArtistSortCandidate>> idMap = new ConcurrentHashMap<>();
        cands.forEach(cand -> {
            if (!idMap.containsKey(cand.getTargetId())) {
                idMap.put(cand.getTargetId(), new ArrayList<>());
            }
            idMap.get(cand.getTargetId()).add(cand);
        });

        LongAdder count = new LongAdder();
        idMap.values().forEach(artistRow -> {
            count.increment();
            if (count.longValue() % 20_000 == 0) {
                repeatWait();
            }
            if (artistRow.size() == SINGLE_FIELD_UPDATE_FOR_ROW) {
                mediaFileDao.updateArtistSort(artistRow.get(0));
            } else {
                mediaFileDao.updateArtistSort(artistRow);
            }
        });

        return cands.stream().map(ArtistSortCandidate::getTargetId).collect(Collectors.toList());
    }
}
