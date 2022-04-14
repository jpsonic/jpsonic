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

package com.tesshu.jpsonic.util;

import java.io.File;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Miscellaneous file utility methods.
 *
 * @author Sindre Mehus
 */
public final class FileUtil {

    private static final Logger LOG = LoggerFactory.getLogger(FileUtil.class);

    /**
     * Disallow external instantiation.
     */
    private FileUtil() {
    }

    public static boolean isFile(final File file) {
        return timed(new FileTask<Boolean>("isFile", file) {
            @Override
            public Boolean execute() {
                return file.isFile();
            }
        });
    }

    public static boolean isDirectory(final File file) {
        return timed(new FileTask<Boolean>("isDirectory", file) {
            @Override
            public Boolean execute() {
                return file.isDirectory();
            }
        });
    }

    public static boolean exists(final File file) {
        return timed(new FileTask<Boolean>("exists", file) {
            @Override
            public Boolean execute() {
                return file.exists();
            }
        });
    }

    public static boolean exists(String path) {
        return exists(new File(path));
    }

    public static long lastModified(final File file) {
        return timed(new FileTask<Long>("lastModified", file) {
            @Override
            public Long execute() {
                return file.lastModified();
            }
        });
    }

    public static long length(final File file) {
        return timed(new FileTask<Long>("length", file) {
            @Override
            public Long execute() {
                return file.length();
            }
        });
    }

    /**
     * Similar to {@link File#listFiles()}, but never returns null. Instead a warning is logged, and an empty array is
     * returned.
     */
    public static File[] listFiles(final File dir) {
        File[] files = timed(new FileTask<File[]>("listFiles", dir) {
            @Override
            public File[] execute() {
                return dir.listFiles();
            }
        });

        if (files == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to list children for " + dir.getPath());
            }
            return new File[0];
        }
        return files;
    }

    /**
     * Returns a short path for the given file. The path consists of the name of the parent directory and the given
     * file.
     */
    public static String getShortPath(Path path) {
        if (path == null) {
            return null;
        }
        Path parent = path.getParent();
        if (parent == null) {
            return path.getFileName().toString();
        }
        return parent.getFileName().toString() + File.separator + path.getFileName().toString();
    }

    private static <T> T timed(FileTask<T> task) {
        return task.execute();
    }

    private abstract static class FileTask<T> {

        private final String name;
        private final File file;

        public FileTask(String name, File file) {
            this.name = name;
            this.file = file;
        }

        public abstract T execute();

        @Override
        public String toString() {
            return name + ", " + file;
        }
    }
}
