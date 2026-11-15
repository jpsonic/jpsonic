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

import java.io.IOException;
import java.util.List;

import com.tesshu.jpsonic.infrastructure.search.criteria.HttpSearchCriteria;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import org.apache.lucene.search.Query;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * Director class for constructing search criteria from HTTP-based search
 * requests.
 * <p>
 * This class primarily handles the interpretation of user input and delegates
 * the creation of Lucene {@link Query} objects to the {@link QueryFactory}. By
 * doing so, it separates the concerns of search criteria construction and query
 * generation, promoting better modularity and maintainability.
 * </p>
 *
 * <p>
 * The {@code @DependsOn} annotation ensures that this component is initialized
 * only after the {@code QueryFactory} and {@code SettingsFacade} beans have
 * been fully initialized, maintaining correct dependency initialization order
 * within the Spring container.
 * </p>
 *
 * <p>
 * Acting as an entry point to the search subsystem, this class builds
 * {@link HttpSearchCriteria} objects by encapsulating the parsed query along
 * with user-defined parameters such as offset, result count, composer
 * inclusion, and target music folders.
 * </p>
 */
@Component
@DependsOn({ "queryFactory", "settingsFacade" })
public class HttpSearchCriteriaDirector {

    private final QueryFactory queryFactory;

    public HttpSearchCriteriaDirector(QueryFactory queryFactory) {
        super();
        this.queryFactory = queryFactory;
    }

    public HttpSearchCriteria construct(String input, int offset, int count,
            boolean includeComposer, List<MusicFolder> musicFolders, IndexType indexType)
            throws IOException {
        Query parsedQuery = queryFactory
            .searchByPhrase(input, includeComposer, musicFolders, indexType);
        return new HttpSearchCriteria(input, parsedQuery, offset, count, indexType,
                includeComposer);
    }
}
