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

package com.tesshu.jpsonic.service.upnp.processor;

import static com.tesshu.jpsonic.util.PlayerUtils.subList;
import static org.springframework.util.ObjectUtils.isEmpty;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import com.tesshu.jpsonic.domain.MediaFile;
import com.tesshu.jpsonic.domain.MediaFile.MediaType;
import com.tesshu.jpsonic.domain.MusicFolderContent;
import com.tesshu.jpsonic.domain.MusicIndex;
import com.tesshu.jpsonic.service.JMediaFileService;
import com.tesshu.jpsonic.service.MusicIndexService;
import com.tesshu.jpsonic.service.upnp.UpnpProcessDispatcher;
import com.tesshu.jpsonic.spring.EhcacheConfiguration.IndexCacheKey;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.fourthline.cling.support.model.BrowseResult;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.PersonWithRole;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.container.GenreContainer;
import org.fourthline.cling.support.model.container.MusicAlbum;
import org.fourthline.cling.support.model.container.MusicArtist;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class IndexUpnpProcessor extends UpnpContentProcessor<MediaFile, MediaFile> {

    private static final AtomicInteger INDEX_IDS = new AtomicInteger(Integer.MIN_VALUE);
    // Only on write (because it can be explicitly reloaded on the client and is less risky)

    private final UpnpProcessorUtil util;
    private final JMediaFileService mediaFileService;
    private final MusicIndexService musicIndexService;
    private final Ehcache indexCache;
    private final Object lock = new Object();

    private MusicFolderContent content;
    private Map<Integer, MediaIndex> indexesMap;
    private List<MediaFile> topNodes;

    public IndexUpnpProcessor(@Lazy UpnpProcessDispatcher dispatcher, UpnpProcessorUtil util,
            JMediaFileService mediaFileService, MusicIndexService musicIndexService, Ehcache indexCache) {
        super(dispatcher, util);
        this.util = util;
        this.mediaFileService = mediaFileService;
        this.musicIndexService = musicIndexService;
        this.indexCache = indexCache;
        setRootId(UpnpProcessDispatcher.CONTAINER_ID_INDEX_PREFIX);
    }

    protected static final int getIDAndIncrement() {
        return INDEX_IDS.getAndIncrement();
    }

    @Override
    public void addChild(DIDLContent didl, MediaFile child) {
        if (child.isFile()) {
            didl.addItem(getDispatcher().getMediaFileProcessor().createItem(child));
        } else {
            didl.addContainer(createContainer(child));
        }
    }

    @Override
    public void addItem(DIDLContent didl, MediaFile item) {
        if (!item.isFile() || isIndex(item)) {
            didl.addContainer(createContainer(item));
        } else {
            didl.addItem(getDispatcher().getMediaFileProcessor().createItem(item));
        }
    }

    @Override
    public BrowseResult browseObjectMetadata(String id) throws ExecutionException {
        MediaFile item = getItemById(id);
        DIDLContent didl = new DIDLContent();
        addChild(didl, item);
        return createBrowseResult(didl, 1, 1);
    }

    @SuppressWarnings("PMD.ConfusingTernary") // false positive
    private void applyId(MediaFile item, Container container) {
        container.setId(UpnpProcessDispatcher.CONTAINER_ID_INDEX_PREFIX + UpnpProcessDispatcher.OBJECT_ID_SEPARATOR
                + item.getId());
        container.setTitle(item.getName());
        container.setChildCount(getChildSizeOf(item));
        if (!isIndex(item) && !mediaFileService.isRoot(item)) {
            MediaFile parent = mediaFileService.getParentOf(item);
            if (parent != null) {
                container.setParentID(String.valueOf(parent.getId()));
            }
        } else {
            container.setParentID(UpnpProcessDispatcher.CONTAINER_ID_INDEX_PREFIX);
        }
    }

    @Override
    public Container createContainer(MediaFile item) {
        if (item.isAlbum()) {
            MusicAlbum container = new MusicAlbum();
            container.setAlbumArtURIs(new URI[] { getDispatcher().getMediaFileProcessor().createAlbumArtURI(item) });
            if (item.getArtist() != null) {
                container.setArtists(getDispatcher().getAlbumProcessor().getAlbumArtists(item.getArtist())
                        .toArray(new PersonWithRole[0]));
            }
            container.setDescription(item.getComment());
            applyId(item, container);
            return container;
        } else if (isIndex(item.getId())) {
            GenreContainer container = new GenreContainer();
            applyId(item, container);
            return container;
        } else {
            MusicArtist container = new MusicArtist();
            applyId(item, container);
            return container;
        }
    }

    @Override
    public List<MediaFile> getChildren(MediaFile item, long offset, long maxResults) {
        if (isIndex(item)) {
            synchronized (lock) {
                MusicIndex index = indexesMap.get(item.getId()).getDeligate();
                return subList(content.getIndexedArtists().get(index).stream().flatMap(s -> s.getMediaFiles().stream())
                        .collect(Collectors.toList()), offset, maxResults);
            }
        }
        if (item.isAlbum()) {
            return mediaFileService.getSongsForAlbum(offset, maxResults, item);
        }
        if (MediaType.DIRECTORY == item.getMediaType()) {
            return mediaFileService.getChildrenOf(item, offset, maxResults, util.isSortAlbumsByYear(item.getArtist()));
        }
        return mediaFileService.getChildrenOf(item, offset, maxResults, false);
    }

    @Override
    public int getChildSizeOf(MediaFile item) {
        if (isIndex(item)) {
            synchronized (lock) {
                return content.getIndexedArtists().get(indexesMap.get(item.getId()).getDeligate()).size();
            }
        }
        return mediaFileService.getChildSizeOf(item);
    }

    @Override
    public MediaFile getItemById(String ids) {
        int id = Integer.parseInt(ids);
        if (isIndex(id)) {
            synchronized (lock) {
                return indexesMap.get(id);
            }
        }
        return mediaFileService.getMediaFileStrict(id);
    }

    @Override
    public int getItemCount() {
        synchronized (lock) {
            refreshIndex();
            return topNodes.size();
        }
    }

    @Override
    public List<MediaFile> getItems(long offset, long maxResults) {
        List<MediaFile> result = new ArrayList<>();
        if (offset < getItemCount()) {
            int count = min((int) (offset + maxResults), getItemCount());
            synchronized (lock) {
                for (int i = (int) offset; i < count; i++) {
                    result.add(topNodes.get(i));
                }
            }
        }
        return result;
    }

    @PostConstruct
    @Override
    public void initTitle() {
        setRootTitleWithResource("dlna.title.index");
    }

    void refreshIndex() {
        Element element = indexCache.getQuiet(IndexCacheKey.FILE_STRUCTURE);
        boolean expired = isEmpty(element) || indexCache.isExpired(element);
        synchronized (lock) {
            if (isEmpty(content) || 0 == content.getIndexedArtists().size() || expired) {
                INDEX_IDS.set(Integer.MIN_VALUE);
                content = musicIndexService.getMusicFolderContent(util.getGuestMusicFolders());
                indexCache.put(new Element(IndexCacheKey.FILE_STRUCTURE, content));
                List<MediaIndex> indexes = content.getIndexedArtists().keySet().stream().map(MediaIndex::new)
                        .collect(Collectors.toList());
                indexesMap = new ConcurrentHashMap<>();
                indexes.forEach(i -> indexesMap.put(i.getId(), i));
                topNodes = Stream.concat(indexes.stream(), content.getSingleSongs().stream())
                        .collect(Collectors.toList());
            }
        }
    }

    private boolean isIndex(int id) {
        return -1 > id;
    }

    private boolean isIndex(MediaFile item) {
        return isIndex(item.getId());
    }

    private int min(Integer... integers) {
        int min = Integer.MAX_VALUE;
        for (int i : integers) {
            min = Integer.min(min, i);
        }
        return min;
    }

    static class MediaIndex extends MediaFile {

        private final MusicIndex deligate;
        private final int id;

        public MediaIndex(MusicIndex deligate) {
            super();
            this.deligate = deligate;
            this.id = getIDAndIncrement();
        }

        public MusicIndex getDeligate() {
            return deligate;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public String getName() {
            return deligate.getIndex();
        }

    }

}
