/*
 * This file is part of Jpsonic.
 *
 * Jpsonic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Jpsonic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General License for more details.
 *
 * You should have received a copy of the GNU General License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.tesshu.jpsonic.infrastructure.search.query.PhraseSearchQueryFactory;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.param.ShuffleSelectionParam;
import jakarta.annotation.Nonnull;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Component;

/**
 * Factory class for constructing Lucene queries for music search. This class
 * delegates Lucene-specific query construction to {@link LuceneQueryBuilder},
 * and manages application-specific concerns like index scheme and composer
 * field inclusion.
 */
@Component("queryFactory")
class QueryFactory implements PhraseSearchQueryFactory {

    private final SettingsFacade settingsFacade;
    private final LuceneQueryBuilder luceneQueryBuilder;

    private static final String MEDIA_TYPE = SearchIndexFields.mediaType.value();

    /**
     * Constructs a QueryFactory.
     *
     * @param settingsFacade  the settings
     * @param analyzerFactory the analyzer factory
     */
    QueryFactory(SettingsFacade settingsFacade, AnalyzerFactory analyzerFactory) {
        this.settingsFacade = settingsFacade;
        this.luceneQueryBuilder = new LuceneQueryBuilder(analyzerFactory, settingsFacade);
    }

    @Nonnull
    Query createFolderQuery(@Nonnull Boolean isId3, @Nonnull List<MusicFolder> folders) {
        String field = isId3 ? SearchIndexFields.folderId.value()
                : SearchIndexFields.folder.value();
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        folders
            .stream()
            .map(folder -> isId3 ? folder.getId().toString() : folder.getPathString())
            .map(value -> new TermQuery(new Term(field, value)))
            .forEach(query -> builder.add(query, BooleanClause.Occur.SHOULD));
        return builder.build();
    }

    @Override
    public Query createFolderQuery(boolean isId3,
            Collection<com.tesshu.jpsonic.domain.model.MusicFolder> folders) {
        return luceneQueryBuilder.buildFolderQuery(isId3, folders);
    }

    /**
     * Creates a year range query.
     *
     * @param from the start year (nullable)
     * @param to   the end year (nullable)
     * @return range query
     */
    @Nonnull
    Query createYearRangeQuery(@Nullable Integer from, @Nullable Integer to) {
        return luceneQueryBuilder.buildYearRangeQuery(from, to);
    }

    /**
     * Creates a phrase query with optional composer fields.
     *
     * @param targetFields the fields to search
     * @param queryString  the phrase to search
     * @param indexType    the index type
     * @return an optional Lucene query
     * @throws IOException if tokenization fails
     */
    @Override
    public Optional<Query> createPhraseQuery(List<String> targetFields, @Nonnull String queryString,
            @Nonnull IndexType indexType) {
        boolean includeComposer = settingsFacade.get(SKeys.general.search.searchComposer);
        return luceneQueryBuilder
            .buildMultiFieldQueryWithBoost(targetFields, queryString, indexType.getBoosts(),
                    includeComposer);
    }

    /**
     * Constructs a Lucene query for phrase-based search with folder filtering.
     *
     * @param searchInput     user input
     * @param includeComposer whether to include composer fields
     * @param musicFolders    target folders
     * @param indexType       index type
     * @return Lucene query
     * @throws IOException if query construction fails
     */
    Query searchByPhrase(@Nonnull String searchInput, boolean includeComposer,
            @Nonnull List<MusicFolder> musicFolders, @Nonnull IndexType indexType)
            throws IOException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean composerIncluded = includeComposer
                || settingsFacade.get(SKeys.general.search.searchComposer);

        Optional<Query> textQuery = luceneQueryBuilder
            .buildMultiFieldQueryWithBoost(indexType.getFields(), searchInput,
                    indexType.getBoosts(), composerIncluded);
        textQuery.ifPresent(q -> builder.add(q, BooleanClause.Occur.MUST));

        boolean isId3 = indexType == IndexType.ALBUM_ID3 || indexType == IndexType.ARTIST_ID3;
        Query folderQuery = createFolderQuery(isId3, musicFolders);
        builder.add(folderQuery, BooleanClause.Occur.MUST);

        return builder.build();
    }

    Query getRandomSongs(@Nonnull ShuffleSelectionParam criteria) throws IOException {
        BooleanQuery.Builder query = new BooleanQuery.Builder();

        query
            .add(new TermQuery(new Term(MEDIA_TYPE, MediaType.MUSIC.name())),
                    BooleanClause.Occur.MUST);

        if (criteria.getGenres() != null && !criteria.getGenres().isEmpty()) {
            query
                .add(luceneQueryBuilder.buildGenreQuery(criteria.getGenres()),
                        BooleanClause.Occur.MUST);
        }

        if (criteria.getFromYear() != null || criteria.getToYear() != null) {
            query
                .add(createYearRangeQuery(criteria.getFromYear(), criteria.getToYear()),
                        BooleanClause.Occur.MUST);
        }

        query.add(createFolderQuery(false, criteria.getMusicFolders()), BooleanClause.Occur.MUST);

        return query.build();
    }

    Query getRandomSongs(@Nonnull List<com.tesshu.jpsonic.domain.model.MusicFolder> musicFolders,
            String... genres) throws IOException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder()
            .add(new TermQuery(new Term(MEDIA_TYPE, MediaType.MUSIC.name())),
                    BooleanClause.Occur.MUST)
            .add(createFolderQuery(false, musicFolders), BooleanClause.Occur.MUST);

        if (genres.length > 0) {
            builder
                .add(luceneQueryBuilder.buildGenreQuery(List.of(genres)), BooleanClause.Occur.MUST);
        }

        return builder.build();
    }

    Query getRandomAlbums(@Nonnull List<MusicFolder> musicFolders) {
        return new BooleanQuery.Builder()
            .add(createFolderQuery(false, musicFolders), BooleanClause.Occur.SHOULD)
            .build();
    }

    Query getRandomAlbumsId3(@Nonnull List<MusicFolder> musicFolders) {
        return new BooleanQuery.Builder()
            .add(createFolderQuery(true, musicFolders), BooleanClause.Occur.SHOULD)
            .build();
    }

    Query getRandomAlbumsId3(
            @Nonnull Collection<com.tesshu.jpsonic.domain.model.MusicFolder> musicFolders) {
        return new BooleanQuery.Builder()
            .add(createFolderQuery(true, musicFolders), BooleanClause.Occur.SHOULD)
            .build();
    }

    Query getAlbumId3sByGenres(@Nullable String genres, @Nonnull List<MusicFolder> folders)
            throws IOException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        if (genres != null && !genres.isEmpty()) {
            builder
                .add(luceneQueryBuilder.buildGenreQuery(List.of(genres)), BooleanClause.Occur.MUST);
        }

        builder.add(createFolderQuery(true, folders), BooleanClause.Occur.MUST);
        return builder.build();
    }

    Query getMediasByGenres(@Nullable String genres, @Nonnull List<MusicFolder> folders)
            throws IOException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        if (genres != null && !genres.isEmpty()) {
            builder
                .add(luceneQueryBuilder.buildGenreQuery(List.of(genres)), BooleanClause.Occur.MUST);
        }

        builder.add(createFolderQuery(false, folders), BooleanClause.Occur.MUST);
        return builder.build();
    }

}
