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
 * (C) 2009 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.tesshu.jpsonic.SuppressFBWarnings;
import com.tesshu.jpsonic.infrastructure.search.LegacySearchResult;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.ArtistDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import jakarta.annotation.PostConstruct;
import org.apache.lucene.document.Document;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utility class used by SearchService.
 *
 * <p>
 * This class primarily supports SearchService, which performs searches via
 * Lucene indexes, by providing the following:
 * </p>
 *
 * <h2>Main Responsibilities</h2>
 * <ul>
 * <li>Entity retrieval according to IndexType</li>
 * <li>Conditional addition to search results (LegacySearchResult,
 * ParamLegacySearchResult)</li>
 * <li>Cache management (Ehcache) for genres and random elements</li>
 * </ul>
 *
 * <h2>Supporting Utility Functions</h2>
 * <p>
 * In addition to the main responsibilities, this class provides generic
 * utilities such as:
 * </p>
 * <ul>
 * <li>Use of SecureRandom mainly to reduce bias and improve randomness in
 * random song selection</li>
 * <li>ID extraction from Lucene Documents</li>
 * <li>Type-safe generic entity fetching (fetchEntity)</li>
 * <li>Null-safe and duplicate-safe addition to collections</li>
 * <li>Cache key generation based on folders and criteria</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <p>
 * This class is designed to be mostly stateless for reusability and simplicity.
 * It does not handle exceptions internally but delegates them to callers.
 * </p>
 */
@Component
public class SearchServiceUtilities {

    private static final Logger LOG = LoggerFactory.getLogger(SearchServiceUtilities.class);

    private final MediaFileDao mediaFileDao;
    private final ArtistDao artistDao;
    private final AlbumDao albumDao;

    private final Map<IndexType, Function<Integer, Object>> entityFetchers;
    private final Map<IndexType, BiConsumer<LegacySearchResult, Integer>> indexTypeActions;

    private Random random;

    public SearchServiceUtilities(ArtistDao artistDao, AlbumDao albumDao,
            MediaFileDao mediaFileDao) {
        super();
        this.artistDao = artistDao;
        this.albumDao = albumDao;
        this.mediaFileDao = mediaFileDao;

        // spotless:off
        entityFetchers = Map.of(
                IndexType.SONG, mediaFileDao::getDomainMediaFile,
                IndexType.ALBUM, mediaFileDao::getDomainMediaFile,
                IndexType.ARTIST, mediaFileDao::getDomainMediaFile,
                IndexType.ARTIST_ID3, artistDao::getDomainArtist,
                IndexType.ALBUM_ID3, albumDao::getDomainAlbum);

        indexTypeActions = Map.of(
                IndexType.ARTIST, (dist, id) -> addMediaFileIfAnyMatch(dist.getMediaFiles(), id),
                IndexType.ALBUM, (dist, id) -> addMediaFileIfAnyMatch(dist.getMediaFiles(), id),
                IndexType.SONG, (dist, id) -> addMediaFileIfAnyMatch(dist.getMediaFiles(), id),
                IndexType.ARTIST_ID3, (dist, id) -> addArtistId3IfAnyMatch(dist.getArtists(), id),
                IndexType.ALBUM_ID3, (dist, id) -> addAlbumId3IfAnyMatch(dist.getAlbums(), id));
        // spotless:on
    }

    /**
     * Generates a random integer between 0 (inclusive) and range (exclusive).
     */
    public int nextInt(int range) {
        return random.nextInt(range);
    }

    /**
     * Converts a Long value to int by truncation.
     */
    public int round(long value) {
        return (int) value;
    }

    /**
     * Extracts the document ID as an integer from the Lucene Document.
     */
    public int getId(@NonNull Document document) {
        return Integer.parseInt(document.get(SearchIndexFields.id.value()));
    }

    /**
     * Initializes the random number generator securely.
     * <p>
     * This method is automatically called by Spring after dependency injection
     * (annotated with {@code @PostConstruct}).
     */
    @SuppressFBWarnings(value = "PREDICTABLE_RANDOM", justification = "Typically, SecureRandom is used.")
    @PostConstruct
    public void postConstruct() {
        try {
            random = SecureRandom.getInstance("NativePRNG");
            if (LOG.isInfoEnabled()) {
                LOG.info("NativePRNG is used to create a random list of songs.");
            }
        } catch (NoSuchAlgorithmException e) {
            try {
                random = SecureRandom.getInstance("SHA1PRNG");
                if (LOG.isInfoEnabled()) {
                    LOG.info("SHA1PRNG is used to create a random list of songs.");
                }
            } catch (NoSuchAlgorithmException e1) {
                random = new Random(Instant.now().toEpochMilli());
                if (LOG.isInfoEnabled()) {
                    LOG.info("NativePRNG and SHA1PRNG cannot be used on this platform.");
                }
            }
        }
    }

    /**
     * Adds a new item to the list if no existing element matches the given
     * predicate.
     *
     * @param targetList The list to which the item may be added.
     * @param newItem    The item to add if absent.
     * @param matcher    Predicate used to test for duplication.
     * @return true if the item was added; false otherwise.
     */
    private static <T> boolean addIfAbsent(List<T> targetList, T newItem, Predicate<T> matcher) {
        if (newItem == null) {
            return false;
        }
        if (targetList.stream().noneMatch(matcher)) {
            targetList.add(newItem);
            return true;
        }
        return false;
    }

    private static <T> boolean addIfAbsent(Collection<T> targetList, T newItem,
            Predicate<T> matcher) {
        if (newItem == null) {
            return false;
        }
        if (targetList.stream().noneMatch(matcher)) {
            targetList.add(newItem);
            return true;
        }
        return false;
    }

    /**
     * Fetches an entity by ID and adds it to the list if it matches the predicate.
     *
     * @param dist    Destination list.
     * @param id      Entity ID.
     * @param fetcher Function to fetch entity.
     * @param matcher Predicate to check for duplication.
     */
    private static <T> void addEntityIfPresent(List<T> dist, Integer id,
            Function<Integer, T> fetcher, Predicate<T> matcher) {
        T entity = fetcher.apply(id);
        if (entity != null) {
            addIfAbsent(dist, entity, matcher);
        }
    }

    private static <T> void addEntityIfPresent(Collection<T> dist, Integer id,
            Function<Integer, T> fetcher, Predicate<T> matcher) {
        T entity = fetcher.apply(id);
        if (entity != null) {
            addIfAbsent(dist, entity, matcher);
        }
    }

    /**
     * Adds an entity to the ParamLegacySearchResult items if the entity is present.
     *
     * @param dist         The ParamLegacySearchResult destination.
     * @param indexType    Index type to fetch entity.
     * @param subjectId    ID of the subject entity.
     * @param subjectClass Expected class of the entity.
     * @param <T>          Type of the entity.
     */
    public final <T> void addEntityIfPresent(Collection<T> dist, IndexType indexType, int subjectId,
            Class<T> subjectClass) {
        fetchEntity(indexType, subjectId, subjectClass)
            .ifPresent(entity -> addIgnoreNull(dist, entity));
    }

    /**
     * Adds a MediaFile to the list if its ID matches and is not already present.
     */
    @Deprecated
    public final void addMediaFileIfAnyMatch(List<MediaFile> dist, Integer id) {
        addEntityIfPresent(dist, id, mediaFileDao::getMediaFile, m -> m.getId() == id);
    }

    /**
     * Adds an Artist to the list if its ID matches and is not already present.
     */
    @Deprecated
    public final void addArtistId3IfAnyMatch(List<Artist> dist, Integer id) {
        addEntityIfPresent(dist, id, artistDao::getArtist, a -> a.getId() == id);
    }

    /**
     * Adds an Album to the list if its ID matches and is not already present.
     */
    @Deprecated
    public final void addAlbumId3IfAnyMatch(List<Album> dist, Integer id) {
        addEntityIfPresent(dist, id, albumDao::getAlbum, a -> a.getId() == id);
    }

    public final void addAlbumId3IfAnyMatch(Collection<com.tesshu.jpsonic.domain.model.Album> dist,
            Integer id) {
        addEntityIfPresent(dist, id, albumDao::getDomainAlbum, a -> a.id() == id);
    }

    /**
     * Adds an object to a collection only if it is not null.
     *
     * @param collection The target collection.
     * @param object     The object to add.
     * @return true if the object was added; false otherwise.
     */
    public final <T> boolean addIgnoreNull(Collection<T> collection, T object) {
        return object != null && collection.add(object);
    }

    /**
     * Fetches an entity of the expected type from the entity fetchers map.
     *
     * @param indexType    The index type to use.
     * @param subjectId    The ID of the entity.
     * @param expectedType The expected class type.
     * @param <T>          Type of the expected object.
     * @return Optional of the cast entity if found.
     */
    private <T> Optional<T> fetchEntity(IndexType indexType, int subjectId, Class<T> expectedType) {
        Object raw = entityFetchers.getOrDefault(indexType, id -> null).apply(subjectId);
        return Optional.ofNullable(raw).map(expectedType::cast);
    }

    /**
     * Adds the corresponding entity from Lucene document to the result object, only
     * if the document's ID is recognized in the indexTypeActions map.
     *
     * @param dist             The LegacySearchResult to which to add the entity.
     * @param subjectIndexType The index type.
     * @param subject          The Lucene document.
     */
    public final void addIfAnyMatch(@NonNull LegacySearchResult dist,
            @NonNull IndexType subjectIndexType, @NonNull Document subject) {
        int documentId = getId(subject);
        if (indexTypeActions.containsKey(subjectIndexType)) {
            indexTypeActions.get(subjectIndexType).accept(dist, documentId);
        }
    }
}
