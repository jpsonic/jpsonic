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

package com.tesshu.jpsonic.service;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.controller.VideoPlayerController;
import com.tesshu.jpsonic.domain.language.StringUtil;
import com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit;
import com.tesshu.jpsonic.feature.auth.jwt.JWTAuthenticationToken;
import com.tesshu.jpsonic.feature.stream.TranscodeInputStream;
import com.tesshu.jpsonic.feature.transcoding.Transcodings;
import com.tesshu.jpsonic.feature.upnp.UPnPSubnet;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.api.entity.Transcoding;
import com.tesshu.jpsonic.persistence.api.repository.TranscodingDao;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides services for transcoding media. Transcoding is the process of
 * converting a media file/stream to a different format and/or bit rate. The
 * latter is also called downsampling.
 *
 * @author Sindre Mehus
 *
 * @see TranscodeInputStream
 */
@Service("transcodingService")
@DependsOn("shortExecutor")
public class TranscodingService {

    private static final Logger LOG = LoggerFactory.getLogger(TranscodingService.class);
    public static final String FORMAT_RAW = "raw";
    public static final String FORMAT_FLAC = "flac";
    private static final Pattern SPLIT_PATTERN = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    private final SettingsFacade settingsFacade;
    private final UserService userService;
    private final UPnPSubnet subnet;
    private final TranscodingDao transcodingDao;
    private final PlayerService playerService;
    private final Executor shortExecutor;

    public TranscodingService(SettingsFacade settingsFacade, UserService userService,
            UPnPSubnet subnet, TranscodingDao transcodingDao, @Lazy PlayerService playerService,
            @Qualifier("shortExecutor") Executor shortExecutor) {
        super();
        this.settingsFacade = settingsFacade;
        this.userService = userService;
        this.subnet = subnet;
        this.transcodingDao = transcodingDao;
        this.playerService = playerService;
        this.shortExecutor = shortExecutor;
    }

    /**
     * Returns all transcodings.
     *
     * @return Possibly empty list of all transcodings.
     */
    public List<Transcoding> getAllTranscodings() {
        return transcodingDao.getAllTranscodings();
    }

    /**
     * Returns all active transcodings for the given player. Only enabled
     * transcodings are returned.
     *
     * @param player The player.
     *
     * @return All active transcodings for the player.
     */
    public List<Transcoding> getTranscodingsForPlayer(@NonNull Player player) {
        return transcodingDao.getTranscodingsForPlayer(player.getId());
    }

    /**
     * Sets the list of active transcodings for the given player.
     *
     * @param player         The player.
     * @param transcodingIds ID's of the active transcodings.
     */
    public void setTranscodingsForPlayer(@NonNull Player player, int... transcodingIds) {
        if (transcodingIds.length == 0) {
            UserSettings userSettings = userService
                .getUserSettings(
                        JWTAuthenticationToken.USERNAME_ANONYMOUS.equals(player.getUsername())
                                ? User.USERNAME_GUEST
                                : player.getUsername());
            player.setBitRateLimit(userSettings.getBitRateLimit());
            playerService.updatePlayer(player);
        }
        transcodingDao.setTranscodingsForPlayer(player.getId(), transcodingIds);
    }

    /**
     * Sets the list of active transcodings for the given player.
     *
     * @param player       The player.
     * @param transcodings The active transcodings.
     */
    public void setTranscodingsForPlayer(@NonNull Player player,
            @NonNull List<Transcoding> transcodings) {
        int[] transcodingIds = new int[transcodings.size()];
        for (int i = 0; i < transcodingIds.length; i++) {
            transcodingIds[i] = transcodings.get(i).getId();
        }
        setTranscodingsForPlayer(player, transcodingIds);
    }

    /**
     * Creates a new transcoding.
     *
     * @param transcoding The transcoding to create.
     */
    public void createTranscoding(@NonNull Transcoding transcoding) {
        transcodingDao.createTranscoding(transcoding);

        // Activate this transcoding for all players?
        if (transcoding.isDefaultActive()) {
            playerService.getAllPlayers().forEach(player -> {
                List<Transcoding> transcodings = getTranscodingsForPlayer(player);
                transcodings.add(transcoding);
                setTranscodingsForPlayer(player, transcodings);
            });
        }
    }

    /**
     * Deletes the transcoding with the given ID.
     *
     * @param id The transcoding ID.
     */
    public void deleteTranscoding(Integer id) {
        transcodingDao.deleteTranscoding(id);
    }

    /**
     * Updates the given transcoding.
     *
     * @param transcoding The transcoding to update.
     */
    public void updateTranscoding(Transcoding transcoding) {
        transcodingDao.updateTranscoding(transcoding);
    }

    /**
     * Returns whether transcoding is required for the given media file and player
     * combination.
     *
     * @param mediaFile The media file.
     * @param player    The player.
     *
     * @return Whether transcoding will be performed if invoking the
     *         {@link #getTranscodedInputStream} method with the same arguments.
     */
    public boolean isTranscodingRequired(@NonNull MediaFile mediaFile, @NonNull Player player) {
        return getTranscoding(mediaFile, player, null, false) != null;
    }

    /**
     * Returns the suffix for the given player and media file, taking transcodings
     * into account.
     *
     * @param player                The player in question.
     * @param file                  The media file.
     * @param preferredTargetFormat Used to select among multiple applicable
     *                              transcodings. May be {@code null}.
     *
     * @return The file suffix, e.g., "mp3".
     */
    public String getSuffix(@NonNull Player player, @NonNull MediaFile file,
            @Nullable String preferredTargetFormat) {
        Transcoding transcoding = getTranscoding(file, player, preferredTargetFormat, false);
        return transcoding == null ? file.getFormat() : transcoding.getTargetFormat();
    }

    /**
     * Returns an applicable transcoding for the given file and player, or
     * <code>null</code> if no transcoding should be done.
     */
    @Nullable
    Transcoding getTranscoding(@NonNull MediaFile mediaFile, @NonNull Player player,
            @Nullable String preferredTargetFormat, boolean hls) {

        if (FORMAT_RAW.equals(preferredTargetFormat)) {
            return null;
        }

        if (hls) {
            return new Transcoding(null, "hls", mediaFile.getFormat(), "ts",
                    settingsFacade.get(SKeys.transcoding.hlsCommand), null, null, true);
        }

        List<Transcoding> applicableTranscodings = new ArrayList<>();
        String suffix = mediaFile.getFormat();

        // This is what I'd like todo, but this will most likely break video transcoding
        // as video transcoding is
        // never expected to be null
        // if(StringUtils.equalsIgnoreCase(preferredTargetFormat, suffix)) {
        // LOG.debug("Target formats are the same, returning no transcoding");
        // return null;
        // }

        List<Transcoding> transcodingsForPlayer = getTranscodingsForPlayer(player);
        for (Transcoding transcoding : transcodingsForPlayer) {
            // special case for now as video must have a transcoding
            if (mediaFile.isVideo() && StringUtil
                .equalsIgnoreCase(preferredTargetFormat, transcoding.getTargetFormat())) {
                // Detected source to target format match for video
                return transcoding;
            }
            transcoding
                .getSourceFormatsAsList()
                .stream()
                .filter(sourceFormat -> sourceFormat.equalsIgnoreCase(suffix))
                .filter(sourceFormat -> isTranscoderInstalled(transcoding))
                .forEach(s -> applicableTranscodings.add(transcoding));
        }

        if (applicableTranscodings.isEmpty()) {
            return null;
        }

        for (Transcoding transcoding : applicableTranscodings) {
            if (transcoding.getTargetFormat().equalsIgnoreCase(preferredTargetFormat)) {
                return transcoding;
            }
        }

        return applicableTranscodings.get(0);
    }

    /**
     * Returns a possibly transcoded or downsampled input stream for the given music
     * file and player combination.
     * <p/>
     * A transcoding is applied if it is applicable for the format of the given
     * file, and is activated for the given player.
     * <p/>
     * If no transcoding is applicable, the file may still be downsampled, given
     * that the player is configured with a bit rate limit which is higher than the
     * actual bit rate of the file.
     * <p/>
     * Otherwise, a normal input stream to the original file is returned.
     *
     * @param parameters As returned by {@link #getParameters}.
     *
     * @return A possible transcoded or downsampled input stream.
     *
     * @throws IOException If an I/O error occurs.
     */
    public @NonNull InputStream getTranscodedInputStream(@NonNull Parameters parameters)
            throws IOException {
        try {
            if (parameters.getTranscoding() != null) {
                return createTranscodedInputStream(parameters);
            }
        } catch (IOException e) {
            // IOException : Process failure or Windows limited process(createTempFile, File
            // copy)
            throw new IOException("Transcoder failed: " + parameters.getMediaFile().toPath(), e);
        }
        // IOException : InvalidPathException
        return Files.newInputStream(parameters.getMediaFile().toPath());
    }

    /**
     * Returns an input stream by applying the given transcoding to the given music
     * file.
     *
     * @param parameters Transcoding parameters.
     *
     * @return The transcoded input stream.
     *
     * @throws IOException If an I/O error occurs.
     */
    @SuppressWarnings("PMD.ConfusingTernary") // false positive
    InputStream createTranscodedInputStream(@NonNull Parameters parameters) throws IOException {

        Transcoding transcoding = parameters.getTranscoding();
        Integer maxBitRate = parameters.getMaxBitRate();
        VideoTranscodingSettings videoTranscodingSettings = parameters
            .getVideoTranscodingSettings();
        MediaFile mediaFile = parameters.getMediaFile();

        if (!isEmpty(transcoding.getStep2()) && !isEmpty(transcoding.getStep3())) {
            return createTranscodeInputStream(transcoding.getStep3(), maxBitRate,
                    videoTranscodingSettings, mediaFile,
                    createTranscodeInputStream(transcoding.getStep2(), maxBitRate,
                            videoTranscodingSettings, mediaFile,
                            createTranscodeInputStream(transcoding.getStep1(), maxBitRate,
                                    videoTranscodingSettings, mediaFile, null)));
        } else if (!isEmpty(transcoding.getStep2())) {
            return createTranscodeInputStream(transcoding.getStep2(), maxBitRate,
                    videoTranscodingSettings, mediaFile,
                    createTranscodeInputStream(transcoding.getStep1(), maxBitRate,
                            videoTranscodingSettings, mediaFile, null));
        } else if (!isEmpty(transcoding.getStep3())) {
            return createTranscodeInputStream(transcoding.getStep3(), maxBitRate,
                    videoTranscodingSettings, mediaFile,
                    createTranscodeInputStream(transcoding.getStep1(), maxBitRate,
                            videoTranscodingSettings, mediaFile, null));
        }

        return createTranscodeInputStream(transcoding.getStep1(), maxBitRate,
                videoTranscodingSettings, mediaFile, null);
    }

    @NonNull
    String[] splitCommand(String command) {
        if (command == null) {
            return new String[0];
        }

        List<String> result = new ArrayList<>();
        Matcher m = SPLIT_PATTERN.matcher(command);
        while (m.find()) {
            if (m.group(1) == null) {
                result.add(m.group(2)); // unquoted string
            } else {
                result.add("\"".concat(m.group(1).concat("\""))); // quoted string
            }
        }

        return result.toArray(new String[0]);
    }

    /**
     * Creates a transcoded input stream by interpreting the given command line
     * string. This includes the following:
     * <ul>
     * <li>Splitting the command line string to an array.</li>
     * <li>Replacing occurrences of "%s" with the path of the given music file.</li>
     * <li>Replacing occurrences of "%t" with the title of the given music
     * file.</li>
     * <li>Replacing occurrences of "%l" with the album name of the given music
     * file.</li>
     * <li>Replacing occurrences of "%a" with the artist name of the given music
     * file.</li>
     * <li>Replacing occurrcences of "%b" with the max bitrate.</li>
     * <li>Replacing occurrcences of "%o" with the video time offset (used for
     * scrubbing).</li>
     * <li>Replacing occurrcences of "%d" with the video duration (used for
     * HLS).</li>
     * <li>Replacing occurrcences of "%w" with the video image width.</li>
     * <li>Replacing occurrcences of "%h" with the video image height.</li>
     * <li>Prepending the path of the transcoder directory if the transcoder is
     * found there.</li>
     * </ul>
     *
     * @param command    The command line string.
     * @param maxBitRate The maximum bitrate to use. May not be {@code null}.
     * @param vts        Parameters used when transcoding video. May be
     *                   {@code null}.
     * @param mediaFile  The media file.
     * @param in         Data to feed to the process. May be {@code null}. @return
     *                   The newly created input stream.
     */
    TranscodeInputStream createTranscodeInputStream(@NonNull String command, Integer maxBitRate,
            VideoTranscodingSettings vts, @NonNull MediaFile mediaFile, @Nullable InputStream in)
            throws IOException {

        String title = mediaFile.getTitle();
        String album = mediaFile.getAlbumName();
        String artist = mediaFile.getArtist();

        if (title == null) {
            title = "Unknown Song";
        }
        if (album == null) {
            album = "Unknown Album";
        }
        if (artist == null) {
            artist = "Unknown Artist";
        }

        List<String> commands = Arrays.asList(splitCommand(command));
        String commandName = commands.get(0);
        String commandNameFullPath = EnvironmentProvider
            .getInstance()
            .getTranscodeDirectory()
            .resolve(commandName)
            .toString();
        commands.set(0, commandNameFullPath);

        for (int i = 1; i < commands.size(); i++) {
            String cmd = commands.get(i);
            cmd = mergeTransCommand(cmd, artist, album, title, maxBitRate, vts);
            if (cmd.contains("%s")) {
                Path path = mediaFile.toPath();
                cmd = cmd.replace("%s", path.toString());
            }
            commands.set(i, cmd);
        }
        ProcessBuilder pb = new ProcessBuilder();
        commands.forEach(op -> pb.command().add(op));
        return new TranscodeInputStream(pb, in, null, shortExecutor);
    }

    private String mergeTransCommand(@NonNull String transCommand, String artist, String album,
            String title, Integer maxBitRate, VideoTranscodingSettings vts) {
        String cmd = replaceIfcontains(transCommand, "%b", String.valueOf(maxBitRate));
        cmd = replaceIfcontains(cmd, "%t", title);
        cmd = replaceIfcontains(cmd, "%l", album);
        cmd = replaceIfcontains(cmd, "%a", artist);
        if (vts != null) {
            cmd = replaceIfcontains(cmd, "%o", String.valueOf(vts.getTimeOffset()));
            cmd = replaceIfcontains(cmd, "%d", String.valueOf(vts.getDuration()));
            cmd = replaceIfcontains(cmd, "%w", String.valueOf(vts.getWidth()));
            cmd = replaceIfcontains(cmd, "%h", String.valueOf(vts.getHeight()));
        }
        return cmd;
    }

    private String replaceIfcontains(@NonNull String line, @NonNull String target,
            @NonNull String value) {
        if (line.contains(target)) {
            return line.replace(target, value);
        }
        return line;
    }

    /**
     * Returns whether transcoding is supported (i.e. whether ffmpeg is installed or
     * not).
     *
     * @param mediaFile If not null, returns whether transcoding is supported for
     *                  this file.
     *
     * @return Whether transcoding is supported.
     */
    public boolean isTranscodingSupported(@Nullable MediaFile mediaFile) {
        List<Transcoding> transcodings = getAllTranscodings();
        for (Transcoding transcoding : transcodings) {
            if (mediaFile == null) {
                return true;
            }
            for (String sourceFormat : transcoding.getSourceFormatsAsList()) {
                if (sourceFormat.equalsIgnoreCase(mediaFile.getFormat())) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isTranscoderInstalled(@NonNull Transcoding transcoding) {
        return isTranscoderInstalled(transcoding.getStep1())
                && isTranscoderInstalled(transcoding.getStep2())
                && isTranscoderInstalled(transcoding.getStep3());
    }

    private boolean isTranscoderInstalled(String step) {

        Path transcodeDir = EnvironmentProvider.getInstance().getTranscodeDirectory();
        if (!Files.exists(transcodeDir)) {
            return false;
        }
        if (StringUtils.isEmpty(step)) {
            return true;
        }

        String executable = com.tesshu.jpsonic.util.StringUtil.split(step).get(0);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(transcodeDir)) {
            for (Path child : ds) {
                Path filename = child.getFileName();
                if (filename != null
                        && executable.equals(filename.toString().replaceAll("\\.exe$", ""))) {
                    return true;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return false;
    }

    /**
     * Creates parameters for a possibly transcoded input stream for the given media
     * file and player combination.
     * <p/>
     * A transcoding is applied if it is applicable for the format of the given
     * file, and is activated for the given player, and either the desired format or
     * bitrate needs changing.
     * <p/>
     * Otherwise, a normal input stream to the original file is returned.
     *
     * @param mediaFile                The media file.
     * @param player                   The player.
     * @param requestMaxBitRate        Overrides the per-player and per-user bitrate
     *                                 limit. May be {@code null}.
     * @param preferredTargetFormat    Used to select among multiple applicable
     *                                 transcodings. May be {@code null}.
     * @param videoTranscodingSettings Parameters used when transcoding video. May
     *                                 be {@code null}.
     *
     * @return Parameters to be used in the {@link #getTranscodedInputStream}
     *         method.
     */
    @SuppressLint(value = "NULL_DEREFERENCE", justification = "False positive. Transcoding is Nullable.")
    public Parameters getParameters(@NonNull MediaFile mediaFile, @NonNull Player player,
            @Nullable final Integer requestMaxBitRate, @Nullable String preferredTargetFormat,
            @Nullable VideoTranscodingSettings videoTranscodingSettings) {

        /*
         * If the player is for anonymous user and is in the same segment as the server,
         * the parameters will be generated using the player settings of the guest user.
         * Transcoding of guest users is managed by the administrator on the UPnP
         * settings page. Even if the connection is via Share, follow this rule if they
         * are in the same segment. In the case of annoymous connection from the outside
         * with different segments, the template such as format is the same as the
         * internal network, but some settings can be made on the player's setting page.
         */
        boolean useGuestPlayer = JWTAuthenticationToken.USERNAME_ANONYMOUS
            .equals(player.getUsername()) && subnet.isInUPnPRange(player.getIpAddress());
        final Player playerForTranscode = useGuestPlayer ? playerService.getGuestPlayer(null)
                : player;

        final BitRateLimit bitRateLimit = airsonicEsqueStrictest(
                getBitRateLimit(playerForTranscode),
                BitRateLimit.fromMaxBitRate(requestMaxBitRate));
        final boolean hls = videoTranscodingSettings != null && videoTranscodingSettings.isHls();
        @Nullable
        final Transcoding transcoding = getTranscoding(mediaFile, playerForTranscode,
                preferredTargetFormat, hls);
        final int mb = createMaxBitrate(bitRateLimit, mediaFile);
        final Integer bitRate = mediaFile.getBitRate();
        Parameters parameters = new Parameters(mediaFile, videoTranscodingSettings);
        if (isNeedTranscoding(transcoding, mb, bitRate, preferredTargetFormat, mediaFile)) {
            parameters.setTranscoding(transcoding);
        }

        parameters.setMaxBitRate(mb == 0 ? null : mb);
        parameters.setRangeAllowed(isRangeAllowed(parameters));
        parameters.setExpectedLength(getExpectedLength(parameters));
        return parameters;
    }

    /**
     * Returns the strictest transcoding scheme defined for the player and the user.
     */
    private BitRateLimit getBitRateLimit(@NonNull Player player) {
        String username = player.getUsername();
        if (username != null) {
            UserSettings userSettings = userService.getUserSettings(username);
            return airsonicEsqueStrictest(player.getBitRateLimit(), userSettings.getBitRateLimit());
        }

        return player.getBitRateLimit();
    }

    public BitRateLimit airsonicEsqueStrictest(@NonNull BitRateLimit base,
            @Nullable BitRateLimit other) {
        if (other == null || other == BitRateLimit.OFF) {
            return base;
        }

        if (base == BitRateLimit.OFF) {
            return other;
        }
        return base.getMaxBitRate() < other.getMaxBitRate() ? base : other;
    }

    @SuppressWarnings("OperatorPrecedence") // (sonatype-lift) Compete with PMD:UselessParentheses.
    int createBitrate(@NonNull MediaFile mediaFile, @Nullable Transcoding transcoding) {
        // If null assume unlimited bitrate
        Integer br = mediaFile.getBitRate();
        int bitRate = br == null ? BitRateLimit.OFF.getMaxBitRate() : br;
        if (!mediaFile.isVideo()) {
            if (mediaFile.isVariableBitRate() && transcoding == null || transcoding != null
                    && !FORMAT_FLAC.equalsIgnoreCase(transcoding.getTargetFormat())) {
                // Assume VBR needs approx 20% more bandwidth to maintain equivalent quality in
                // CBR
                bitRate = bitRate * 6 / 5;
            }
            // Make sure bitrate is quantized to valid values for CBR
            BitRateLimit quantized = BitRateLimit.fromMaxBitRate(bitRate);
            if (quantized != null) {
                bitRate = quantized.getMaxBitRate();
            }
        }
        return bitRate;
    }

    int createMaxBitrate(@NonNull BitRateLimit bitRateLimit, @NonNull MediaFile mediaFile) {
        final int mb = mediaFile.isVideo() ? VideoPlayerController.DEFAULT_BIT_RATE
                : bitRateLimit.getMaxBitRate();
        Integer bitRate = mediaFile.getBitRate();
        if (mb == 0) {
            return bitRate == null ? 0 : bitRate;
        } else if (bitRate != null && bitRate != 0 && bitRate < mb) {
            return bitRate;
        }
        return mb;
    }

    @SuppressWarnings("OperatorPrecedence") // (sonatype-lift) Compete with PMD:UselessParentheses.
    boolean isNeedTranscoding(@Nullable Transcoding transcoding, int mb, Integer bitRate,
            @Nullable String preferredTargetFormat, @NonNull MediaFile mediaFile) {
        if (bitRate == null) {
            return true;
        }
        boolean isNeedTranscoding = false;
        if (transcoding != null
                && (mb != 0 && (bitRate == 0 || bitRate > mb) || preferredTargetFormat != null
                        && !mediaFile.getFormat().equalsIgnoreCase(preferredTargetFormat))) {
            isNeedTranscoding = true;
        }
        return isNeedTranscoding;
    }

    boolean isRangeAllowed(@NonNull Parameters parameters) {
        Transcoding transcoding = parameters.getTranscoding();
        List<String> steps;
        if (transcoding == null) {
            return true; // not transcoding
        } else {
            steps = Arrays
                .asList(transcoding.getStep3(), transcoding.getStep2(), transcoding.getStep1());
        }

        // Verify that were able to predict the length
        if (parameters.getExpectedLength() == null) {
            return false;
        }

        // Check if last configured step uses the bitrate, if so, range should be pretty
        // safe
        for (String step : steps) {
            if (step != null) {
                return step.contains("%b");
            }
        }
        return false;
    }

    /**
     * Returns the length (or predicted/expected length) of a (possibly padded)
     * media stream
     */
    @Nullable
    Long getExpectedLength(@NonNull Parameters parameters) {

        MediaFile file = parameters.getMediaFile();
        if (!parameters.isTranscode()) {
            return file.getFileSize();
        }

        Integer duration = file.getDurationSeconds();
        if (duration == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Unknown duration for " + file + ". Unable to estimate transcoded size.");
            }
            return null;
        }

        Integer maxBitRate = parameters.getMaxBitRate();
        if (maxBitRate == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Unknown bit rate for " + file + ". Unable to estimate transcoded size.");
            }
            return null;
        }

        // Over-estimate size a bit (2 seconds) so don't cut off early in case of small
        // calculation differences
        return (duration + 2) * (long) maxBitRate * 1000L / 8L;
    }

    /**
     * Restore the specified transcode. The initial value of transcoding is set by
     * the initial data of the database, but it can be changed and consistency is
     * not guaranteed. Delete insert is done, in this method.
     */
    @Transactional
    public void restoreTranscoding(@NonNull Transcodings transcode) {
        if (transcode == null) {
            return;
        }
        Transcoding transcoding = switch (transcode) {
        case MP3 -> new Transcoding(null, Transcodings.MP3.getName(),
                "mp3 opus ogg oga aac m4a flac wav wma aif aiff ape mpc shn", "mp3",
                "ffmpeg -v fatal -i %s -map 0:a:0 -b:a %bk -id3v2_version 3 -f mp3 -", null, null,
                true);
        case OPUS -> new Transcoding(null, Transcodings.OPUS.getName(),
                "mp3 opus ogg oga aac m4a flac wav wma aif aiff ape mpc shn", "opus",
                "ffmpeg -v fatal -i %s -map 0:a:0 -c:a libopus -b:a %bk -vbr constrained -f ogg -",
                null, null, true);
        case FLAC -> new Transcoding(null, Transcodings.FLAC.getName(), FORMAT_FLAC, FORMAT_FLAC,
                "ffmpeg -v fatal -i %s -map 0:0 -vn -c:a flac -sample_fmt s16 -ar 44100 -ac 2 -f flac -",
                null, null, false);
        case PCM -> new Transcoding(null, Transcodings.PCM.getName(), FORMAT_FLAC, "wav",
                "ffmpeg -v fatal -nostats -i %s -vn -c:a pcm_s16le -f wav -", null, null, false);
        case FLV -> new Transcoding(null, Transcodings.FLV.getName(),
                "avi mpg mpeg mp4 m4v mkv mov wmv ogv divx m2ts", "flv",
                "ffmpeg -ss %o -i %s -async 1 -b %bk -s %wx%h -ar 44100 -ac 2 -v 0 -f flv -vcodec libx264 -preset superfast -threads 0 -",
                null, null, true);
        case MKV -> new Transcoding(null, Transcodings.MKV.getName(),
                "avi mpg mpeg mp4 m4v mkv mov wmv ogv divx m2ts", "mkv",
                "ffmpeg -ss %o -i %s -c:v libx264 -preset superfast -b:v %bk -c:a libvorbis -f matroska -threads 0 -",
                null, null, true);
        case MP4 -> new Transcoding(null, Transcodings.MP4.getName(),
                "avi flv mpg mpeg m4v mkv mov wmv ogv divx m2ts", "mp4",
                "ffmpeg -ss %o -i %s -async 1 -b %bk -s %wx%h -ar 44100 -ac 2 -v 0 -f mp4 -vcodec libx264 -preset superfast -threads 0 -movflags frag_keyframe+empty_moov -",
                null, null, true);
        };

        // Newly created transcode
        int registered = transcodingDao.createTranscoding(transcoding);
        transcoding = transcodingDao
            .getAllTranscodings()
            .stream()
            .filter(t -> t.getId() == registered)
            .collect(Collectors.toList())
            .get(0);

        // Exclude transcodes with the same name and add new transcodes
        for (Player player : playerService.getAllPlayers()) {
            List<Transcoding> transcodings = getTranscodingsForPlayer(player)
                .stream()
                .filter(t -> !transcode.getName().equals(t.getName()))
                .collect(Collectors.toList());
            if (transcoding.isDefaultActive()) {
                transcodings.add(transcoding);
            }
            setTranscodingsForPlayer(player, transcodings);
        }

        // Delete old transcode with the same name
        List<Transcoding> toBeDeleted = transcodingDao
            .getAllTranscodings()
            .stream()
            .filter(t -> t.getId() != registered)
            .filter(t -> transcode.getName().equals(t.getName()))
            .collect(Collectors.toList());
        for (Transcoding old : toBeDeleted) {
            deleteTranscoding(old.getId());
        }
    }

    public static class Parameters {
        private Long expectedLength;
        private boolean rangeAllowed;
        private final MediaFile mediaFile;
        private final VideoTranscodingSettings videoTranscodingSettings;
        private Integer maxBitRate;
        private Transcoding transcoding;

        public Parameters(MediaFile mediaFile, VideoTranscodingSettings videoTranscodingSettings) {
            this.mediaFile = mediaFile;
            this.videoTranscodingSettings = videoTranscodingSettings;
        }

        public void setMaxBitRate(Integer maxBitRate) {
            this.maxBitRate = maxBitRate;
        }

        public boolean isTranscode() {
            return transcoding != null;
        }

        public boolean isRangeAllowed() {
            return this.rangeAllowed;
        }

        public void setRangeAllowed(boolean rangeAllowed) {
            this.rangeAllowed = rangeAllowed;
        }

        public Long getExpectedLength() {
            return this.expectedLength;
        }

        public void setExpectedLength(Long expectedLength) {
            this.expectedLength = expectedLength;
        }

        public void setTranscoding(Transcoding transcoding) {
            this.transcoding = transcoding;
        }

        public Transcoding getTranscoding() {
            return transcoding;
        }

        public MediaFile getMediaFile() {
            return mediaFile;
        }

        public Integer getMaxBitRate() {
            return maxBitRate;
        }

        public VideoTranscodingSettings getVideoTranscodingSettings() {
            return videoTranscodingSettings;
        }
    }

    // VO
    /**
     * Parameters used when transcoding videos.
     *
     * @author Sindre Mehus
     */
    public static final class VideoTranscodingSettings {

        private final int width;
        private final int height;
        private final int timeOffset;
        private final int duration;
        private final boolean hls;

        public VideoTranscodingSettings(int width, int height, int timeOffset, int duration,
                boolean hls) {
            this.width = width;
            this.height = height;
            this.timeOffset = timeOffset;
            this.duration = duration;
            this.hls = hls;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getTimeOffset() {
            return timeOffset;
        }

        public int getDuration() {
            return duration;
        }

        public boolean isHls() {
            return hls;
        }
    }
}
