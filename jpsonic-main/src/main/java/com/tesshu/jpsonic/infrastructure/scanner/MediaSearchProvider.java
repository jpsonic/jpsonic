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

import static org.springframework.util.ObjectUtils.isEmpty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.domain.model.SearchResult;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.infrastructure.search.LegacySearchResult;
import com.tesshu.jpsonic.infrastructure.search.criteria.HttpSearchCriteria;
import com.tesshu.jpsonic.infrastructure.search.criteria.UPnPSearchCriteria;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.param.ShuffleSelectionParam;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of the SearchService interface providing various search
 * capabilities over music data indexed with Lucene.
 * 
 * <p>
 * This service handles executing Lucene queries, converting search results into
 * domain entities, caching random search results, and managing pagination and
 * filtering based on music folders, genres, artists, and media types.
 * </p>
 * 
 * <p>
 * Dependencies include:
 * <ul>
 * <li>Lucene utilities for search execution and query building</li>
 * <li>Data access objects (DAOs) for Albums and MediaFiles</li>
 * <li>Application-specific utilities and settings services</li>
 * </ul>
 * </p>
 * 
 * <p>
 * The service supports both standard web search criteria and UPnP search
 * criteria, offering random selection capabilities with caching to optimize
 * performance.
 * </p>
 * 
 * <p>
 * Exception handling ensures that IO and Lucene-related errors are logged
 * without crashing the service, and resources such as IndexSearcher are
 * properly released.
 * </p>
 * 
 * @see SearchService
 * @see QueryFactory
 * @see IndexManager
 */
@Service
public class MediaSearchProvider implements LegacySearch {

    private static final Logger LOG = LoggerFactory.getLogger(MediaSearchProvider.class);

    private final QueryFactory queryFactory;
    private final IndexManager indexManager;
    private final SearchServiceUtilities util;
    private final SettingsFacade settingsFacade;
    private final MediaFileDao mediaFileDao;
    private final AlbumDao albumDao;
    private final InternalSearchCache cache;
    private final GenreMasterProvider genreMasterProvider;

    MediaSearchProvider(QueryFactory queryFactory, IndexManager indexManager,
            SearchServiceUtilities util, SettingsFacade settingsFacade, MediaFileDao mediaFileDao,
            AlbumDao albumDao, InternalSearchCache cache, GenreMasterProvider genreMasterProvider) {
        super();
        this.queryFactory = queryFactory;
        this.indexManager = indexManager;
        this.util = util;
        this.settingsFacade = settingsFacade;
        this.mediaFileDao = mediaFileDao;
        this.albumDao = albumDao;
        this.cache = cache;
        this.genreMasterProvider = genreMasterProvider;
    }

    // Logs the search query if settings and log level allow it
    private void logSearchQueryIfNeeded(HttpSearchCriteria criteria) {
        if (settingsFacade.get(SKeys.general.search.outputSearchQuery) && LOG.isInfoEnabled()) {
            LOG
                .info("Web: Multi-field search : {} -> query:{}, offset:{}, count:{}",
                        criteria.targetType(), criteria.input(), criteria.offset(),
                        criteria.count());
        }
    }

    @Override
    public LegacySearchResult search(HttpSearchCriteria criteria) {
        LegacySearchResult result = new LegacySearchResult();
        int offset = criteria.offset();
        int count = criteria.count();
        result.setOffset(offset);

        // Return early if count is less than or equal to zero
        if (count <= 0) {
            return result;
        }

        // Get the IndexSearcher for the given target type
        IndexSearcher searcher = indexManager.getSearcher(criteria.targetType());
        if (searcher == null) {
            return result;
        }

        try {
            // Execute search with offset + count to allow paging
            TopDocs topDocs = searcher.search(criteria.parsedQuery(), offset + count);

            // Get the rounded total number of hits
            int totalHits = util.round(topDocs.totalHits.value());
            result.setTotalHits(totalHits);

            // Calculate the result range (start to end) safely within bounds
            int start = Math.min(offset, totalHits);
            int end = Math.min(start + count, totalHits);

            // Add documents in the specified range to the result if they match
            for (int i = start; i < end; i++) {
                util
                    .addIfAnyMatch(result, criteria.targetType(),
                            searcher.storedFields().document(topDocs.scoreDocs[i].doc));
            }

            // Log the search query info if logging is enabled
            logSearchQueryIfNeeded(criteria);

        } catch (IOException e) {
            // Handle search failure
            LOG.error("Failed to execute Lucene search.", e);
        } finally {
            // Always release the searcher to avoid resource leaks
            indexManager.release(criteria.targetType(), searcher);
        }

        return result;
    }

    @SuppressLint(value = "NULL_DEREFERENCE", justification = "False positive. #1585")
    public <T> SearchResult<T> search(UPnPSearchCriteria criteria) {
        int offset = criteria.offset();
        int count = criteria.count();

        IndexType indexType = criteria.targetType();

        // Early return if count is invalid or index type is null
        if (count <= 0 || indexType == null) {
            return new SearchResult<>(offset);
        }

        IndexSearcher searcher = indexManager.getSearcher(indexType);
        if (searcher == null) {
            return new SearchResult<>(offset);
        }

        // Optionally log search info
        writeUPnPSerchLog(indexType, criteria);

        try {
            // Execute Lucene search with enough results for paging
            TopDocs topDocs = searcher.search(criteria.parsedQuery(), offset + count);

            // Calculate result bounds
            int totalHits = util.round(topDocs.totalHits.value());
            int start = Math.min(offset, totalHits);
            int end = Math.min(start + count, totalHits);

            // Dispatch by index type
            if (IndexType.ARTIST_ID3 == indexType) {
                return (SearchResult<T>) processDocuments(searcher, topDocs, start, end, indexType,
                        com.tesshu.jpsonic.domain.model.Artist.class);
            } else if (IndexType.ALBUM_ID3 == indexType) {
                return (SearchResult<T>) processDocuments(searcher, topDocs, start, end, indexType,
                        com.tesshu.jpsonic.domain.model.Album.class);
            } else {
                return (SearchResult<T>) processDocuments(searcher, topDocs, start, end, indexType,
                        com.tesshu.jpsonic.domain.model.MediaFile.class);
            }
        } catch (IOException e) {
            LOG.error("Failed to execute Lucene search.", e);
        } finally {
            // Always release resources
            indexManager.release(indexType, searcher);
        }
        return new SearchResult<>(criteria.offset());
    }

    /**
     * Generic method to extract documents, convert them to desired type, and add to
     * result.
     */
    private <T> SearchResult<T> processDocuments(IndexSearcher searcher, TopDocs topDocs, int start,
            int end, IndexType indexType, Class<T> clazz) throws IOException {
        List<T> dist = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Document doc = searcher.storedFields().document(topDocs.scoreDocs[i].doc);
            util.addEntityIfPresent(dist, indexType, util.getId(doc), clazz);
        }
        int totalHits = util.round(topDocs.totalHits.value());
        return new SearchResult<>(dist, start, totalHits);
    }

    private void writeUPnPSerchLog(IndexType indexType, UPnPSearchCriteria criteria) {
        if (settingsFacade.get(SKeys.general.search.outputSearchQuery) && LOG.isInfoEnabled()) {
            LOG
                .info("UpnP: UpnP-compliant field search : {} -> query:{}, offset:{}, count:{}",
                        indexType, criteria.input(), criteria.offset(), criteria.count());
        }
    }

    /**
     * Common processing of random method.
     *
     * @param count            Number of albums to return.
     * @param idToListCallback Callback to get D from id and store it in List
     */
    private <D> List<D> createRandomDocsList(long count, IndexSearcher searcher, Query query,
            BiConsumer<List<D>, Integer> idToListCallback) throws IOException {

        // Get all matching document IDs from Lucene
        List<Integer> docIds = Arrays
            .stream(searcher.search(query, Integer.MAX_VALUE).scoreDocs)
            .map(sd -> sd.doc)
            .collect(Collectors.toList());

        List<D> result = new ArrayList<>();

        // Randomly pick documents until the desired count is reached or list is
        // exhausted
        while (!docIds.isEmpty() && result.size() < count) {
            int randomIndex = util.nextInt(docIds.size());

            // Fetch the document at the random position
            Document document = searcher.storedFields().document(docIds.get(randomIndex));

            // Convert the document ID and add to result via the callback
            idToListCallback.accept(result, util.getId(document));

            // Remove selected doc to avoid duplicates
            docIds.remove(randomIndex);
        }

        return result;
    }

    @SuppressWarnings("PMD.LambdaCanBeMethodReference") // false positive
    @Override
    public List<MediaFile> getRandomSongs(ShuffleSelectionParam criteria) {
        IndexSearcher searcher = indexManager.getSearcher(IndexType.SONG);

        // Return empty list if no searcher is available (e.g., on first startup)
        if (searcher == null) {
            return Collections.emptyList();
        }

        try {
            // Build query to fetch candidate songs for random selection
            Query query = queryFactory.getRandomSongs(criteria);

            // Create a random list of MediaFile objects using the callback
            return createRandomDocsList(criteria.getCount(), searcher, query,
                    (resultList, id) -> util.addMediaFileIfAnyMatch(resultList, id));

        } catch (IOException e) {
            // Log any Lucene or IO-related failures
            LOG.error("Failed to search for random songs.", e);
        } finally {
            // Always release the searcher to prevent resource leaks
            indexManager.release(IndexType.SONG, searcher);
        }

        // Return empty list if something went wrong
        return Collections.emptyList();
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> getRandomSongs(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> musicFolders, long offset, long count,
            int cacheMax, String... genres) {

        final List<com.tesshu.jpsonic.domain.model.MediaFile> result = new ArrayList<>();

        // Callback to add a sublist of IDs (after offset & limit) to the result list
        Consumer<List<Integer>> addSubsetToResult = ids -> ids
            .stream()
            .skip(offset)
            .limit(count)
            .map(mediaFileDao::getDomainMediaFile)
            .filter(Objects::nonNull)
            .forEach(result::add);

        // Try to get cached IDs first
        cache
            .getIntCache(InternalSearchCacheKeys.random.song, cacheMax, musicFolders, genres)
            .ifPresent(addSubsetToResult);
        if (!result.isEmpty()) {
            return result; // Return if cache hit
        }

        // Get Lucene IndexSearcher for songs
        IndexSearcher searcher = indexManager.getSearcher(IndexType.SONG);
        if (searcher == null) {
            return result;
        }

        try {
            // Build query based on folders and genres
            Query query = queryFactory.getRandomSongs(musicFolders, genres);

            // Fetch all matching Lucene documents
            List<Integer> docIds = Arrays
                .stream(searcher.search(query, Integer.MAX_VALUE).scoreDocs)
                .map(sd -> sd.doc)
                .collect(Collectors.toList());

            // Select up to `cacheMax` unique random IDs
            List<Integer> ids = new ArrayList<>();
            while (!docIds.isEmpty() && ids.size() < cacheMax) {
                int randomIndex = util.nextInt(docIds.size());
                Document doc = searcher.storedFields().document(docIds.get(randomIndex));
                ids.add(util.getId(doc));
                docIds.remove(randomIndex);
            }

            // Store the randomly selected IDs in cache
            cache
                .putIntCache(InternalSearchCacheKeys.random.song, cacheMax, musicFolders, ids,
                        genres);

            // Apply offset & limit, and add to result
            addSubsetToResult.accept(ids);

        } catch (IOException e) {
            LOG.error("Failed to search for random songs.", e);
        } finally {
            // Always release searcher to avoid resource leak
            indexManager.release(IndexType.SONG, searcher);
        }

        return result;
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> getRandomSongsByArtist(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders,
            com.tesshu.jpsonic.domain.model.Artist artist, long offset, long count, int cacheMax) {

        final List<com.tesshu.jpsonic.domain.model.MediaFile> result = new ArrayList<>();

        // Define logic to extract a sublist (offset + count) from the source list
        Consumer<List<com.tesshu.jpsonic.domain.model.MediaFile>> addSubsetToResult = files -> files
            .stream()
            .skip(offset)
            .limit(count)
            .forEach(result::add);

        // Try retrieving from cache first
        cache
            .getCache(InternalSearchCacheKeys.random.songByArtist, cacheMax, folders, artist.name())
            .ifPresent(addSubsetToResult);

        if (!result.isEmpty()) {
            return result; // Return if cached result exists
        }

        // Get random songs for the artist from the database
        List<com.tesshu.jpsonic.domain.model.MediaFile> songs = mediaFileDao
            .getDomainRandomSongsForAlbumArtist(cacheMax, artist.name(), folders,
                    (range, limit) -> {
                        // Generate a list of unique random integers within [0, range)
                        List<Integer> randomIndices = new ArrayList<>();
                        while (randomIndices.size() < Math.min(limit, range)) {
                            int random = util.nextInt(range);
                            if (!randomIndices.contains(random)) {
                                randomIndices.add(random);
                            }
                        }
                        return randomIndices;
                    });

        // Cache the retrieved songs for future access
        cache
            .putCache(InternalSearchCacheKeys.random.songByArtist, cacheMax, folders, songs,
                    artist.name());

        // Add the subset (offset + count) to the result
        addSubsetToResult.accept(songs);

        return result;
    }

    @SuppressWarnings("PMD.LambdaCanBeMethodReference") // false positive
    @Override
    public List<MediaFile> getRandomAlbums(int count, List<MusicFolder> musicFolders) {
        // Get Lucene IndexSearcher for albums
        IndexSearcher searcher = indexManager.getSearcher(IndexType.ALBUM);

        // Return empty list if searcher is unavailable (e.g., index not ready)
        if (searcher == null) {
            return Collections.emptyList();
        }

        try {
            // Create query to retrieve albums from the given folders
            Query query = queryFactory.getRandomAlbums(musicFolders);

            // Select random documents and convert them to MediaFile objects
            return createRandomDocsList(count, searcher, query,
                    (resultList, id) -> util.addMediaFileIfAnyMatch(resultList, id));

        } catch (IOException e) {
            // Log error if Lucene search fails
            LOG.error("Failed to search for random albums.", e);

        } finally {
            // Ensure resources are properly released
            indexManager.release(IndexType.ALBUM, searcher);
        }

        // Fallback: return empty list if exception occurs
        return Collections.emptyList();
    }

    @SuppressWarnings("PMD.LambdaCanBeMethodReference") // false positive
    @Override
    public List<Album> getRandomAlbumsId3(int count, List<MusicFolder> musicFolders) {
        // Obtain IndexSearcher for ID3-based albums
        IndexSearcher searcher = indexManager.getSearcher(IndexType.ALBUM_ID3);

        // Return empty list if index is not available
        if (searcher == null) {
            return Collections.emptyList();
        }

        try {
            // Build query to search for random albums (ID3-based) within the given folders
            Query query = queryFactory.getRandomAlbumsId3(musicFolders);

            // Fetch random album documents and convert them to Album entities
            return createRandomDocsList(count, searcher, query,
                    (resultList, id) -> util.addAlbumId3IfAnyMatch(resultList, id));

        } catch (IOException e) {
            // Log any IO or Lucene search error
            LOG.error("Failed to search for random albums (ID3).", e);

        } finally {
            // Ensure the IndexSearcher is always released
            indexManager.release(IndexType.ALBUM_ID3, searcher);
        }

        // Fallback in case of errors
        return Collections.emptyList();
    }

    public List<com.tesshu.jpsonic.domain.model.Album> getRandomAlbumsId3(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, long offset, long count,
            int cacheMax) {

        final List<com.tesshu.jpsonic.domain.model.Album> result = new ArrayList<>();

        // Define logic to extract a sublist (offset + count) from the source list
        Consumer<List<com.tesshu.jpsonic.domain.model.Album>> addSubsetToResult = files -> files
            .stream()
            .skip(offset)
            .limit(count)
            .forEach(result::add);

        // Try retrieving from cache first
        final Optional<List<com.tesshu.jpsonic.domain.model.Album>> cachedAlbums = cache
            .getAlbumCache(InternalSearchCacheKeys.random.album, cacheMax, folders);
        if (cachedAlbums.isPresent()) {
            addSubsetToResult.accept(cachedAlbums.get());
            return result;
        }

        // Obtain IndexSearcher for ID3-based albums
        IndexSearcher searcher = indexManager.getSearcher(IndexType.ALBUM_ID3);

        // Return empty list if index is not available
        if (searcher == null) {
            return Collections.emptyList();
        }

        List<com.tesshu.jpsonic.domain.model.Album> albums = new ArrayList<>();
        try {
            // Build query to search for random albums (ID3-based) within the given folders
            Query query = queryFactory.getRandomAlbumsId3(folders);

            // Fetch random album documents and convert them to Album entities
            albums
                .addAll(createRandomDocsList(cacheMax, searcher, query,
                        util::addAlbumId3IfAnyMatch));

        } catch (IOException e) {
            // Log any IO or Lucene search error
            LOG.error("Failed to search for random albums (ID3).", e);
        } finally {
            // Ensure the IndexSearcher is always released
            indexManager.release(IndexType.ALBUM_ID3, searcher);
        }

        // Cache the retrieved albums for future access
        cache.putAlbumCache(InternalSearchCacheKeys.random.album, cacheMax, folders, albums);

        // Add the subset (offset + count) to the result
        addSubsetToResult.accept(albums);

        return result;
    }

    @Override
    public List<MediaFile> getAlbumsByGenres(String genres, long offset, long count,
            List<MusicFolder> musicFolders) {
        if (isEmpty(genres)) {
            return Collections.emptyList();
        }
        List<String> preAnalyzedGenres = genreMasterProvider
            .findPreAnalyzedGenres(Arrays.asList(genres), true);
        return mediaFileDao.getAlbumsByGenre(offset, count, preAnalyzedGenres, musicFolders);
    }

    @Override
    public List<Album> getAlbumId3sByGenres(String genres, long offset, long count,
            List<MusicFolder> musicFolders) {
        if (isEmpty(genres)) {
            return Collections.emptyList();
        }
        List<String> preAnalyzedGenres = genreMasterProvider
            .findPreAnalyzedGenres(Arrays.asList(genres), false);
        return albumDao.getAlbumsByGenre(offset, count, preAnalyzedGenres, musicFolders);
    }

    @Override
    public List<MediaFile> getSongsByGenres(String genres, long offset, long count,
            List<MusicFolder> musicFolders, MediaFile.MediaType... types) {
        // Return empty list if genres string is null or empty
        if (isEmpty(genres)) {
            return Collections.emptyList();
        }

        // Convert input genres string (already processed as a list) into pre-analyzed
        // genre tokens
        List<String> preAnalyzedGenres = genreMasterProvider
            .findPreAnalyzedGenres(Arrays.asList(genres), true);

        // Use provided media types or default to MUSIC and AUDIOBOOK
        List<MediaType> targetTypes = (types.length == 0)
                ? Arrays.asList(MediaType.MUSIC, MediaType.AUDIOBOOK)
                : Arrays.asList(types);
        // Delegate search to DAO
        return mediaFileDao
            .getSongsByGenre(preAnalyzedGenres, offset, count, musicFolders, targetTypes);
    }
}
