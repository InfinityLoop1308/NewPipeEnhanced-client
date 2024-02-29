package us.shandian.giga.get;

import android.content.Context;
import android.net.Uri;
import android.util.SparseArray;
import androidx.preference.PreferenceManager;
import icepick.State;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.SecondaryStreamHelper;
import org.schabi.newpipe.util.StreamItemAdapter;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class DirectDownloader {

    Context context;
    @State
    StreamInfo currentInfo;
    @State
    StreamItemAdapter.StreamSizeWrapper<AudioStream> wrappedAudioStreams = StreamItemAdapter.StreamSizeWrapper.empty();
    @State
    StreamItemAdapter.StreamSizeWrapper<VideoStream> wrappedVideoStreams = StreamItemAdapter.StreamSizeWrapper.empty();
    @State
    StreamItemAdapter.StreamSizeWrapper<SubtitlesStream> wrappedSubtitleStreams = StreamItemAdapter.StreamSizeWrapper.empty();
    @State
    int selectedVideoIndex = 0;
    @State
    int selectedAudioIndex = 0;
    @State
    int selectedSubtitleIndex = 0;
    private StoredDirectoryHelper mainStorageAudio = null;
    private StoredDirectoryHelper mainStorageVideo = null;
    private DownloadManager downloadManager = null;

    private StreamItemAdapter<AudioStream, Stream> audioStreamsAdapter;
    private StreamItemAdapter<VideoStream, AudioStream> videoStreamsAdapter;
    private StreamItemAdapter<SubtitlesStream, Stream> subtitleStreamsAdapter;

    public enum DownloadType {
        AUDIO,
        VIDEO,
        SUBTITLES
    }

    private DownloadType type;

    public DirectDownloader(final Context context, final StreamInfo info, DownloadType type) {
        final ArrayList<VideoStream> streamsList = new ArrayList<>(ListHelper
                .getSortedStreamVideosList(context, info.getVideoStreams(),
                        info.getVideoOnlyStreams(), false, false));
        final int selectedStreamIndex = ListHelper.getDefaultResolutionIndex(context, streamsList);

        this.setVideoStreams(streamsList);
        this.setSelectedVideoStream(selectedStreamIndex);
        this.setAudioStreams(info.getAudioStreams());
        this.setSubtitleStreams(info.getSubtitles());
        this.setInfo(info);
        this.type = type;
        this.context = context;
        init();
    }

    private void setInfo(final StreamInfo info) {
        this.currentInfo = info;
    }

    public void setAudioStreams(final List<AudioStream> audioStreams) {
        setAudioStreams(new StreamItemAdapter.StreamSizeWrapper<>(audioStreams, context));
    }

    public void setAudioStreams(final StreamItemAdapter.StreamSizeWrapper<AudioStream> was) {
        this.wrappedAudioStreams = was;
    }

    public void setVideoStreams(final List<VideoStream> videoStreams) {
        setVideoStreams(new StreamItemAdapter.StreamSizeWrapper<>(videoStreams, context));
    }

    public void setVideoStreams(final StreamItemAdapter.StreamSizeWrapper<VideoStream> wvs) {
        this.wrappedVideoStreams = wvs;
    }

    public void setSubtitleStreams(final List<SubtitlesStream> subtitleStreams) {
        setSubtitleStreams(new StreamItemAdapter.StreamSizeWrapper<>(subtitleStreams, context));
    }

    public void setSubtitleStreams(
            final StreamItemAdapter.StreamSizeWrapper<SubtitlesStream> wss) {
        this.wrappedSubtitleStreams = wss;
    }

    public void setSelectedVideoStream(final int svi) {
        this.selectedVideoIndex = svi;
    }

    public void setSelectedAudioStream(final int sai) {
        this.selectedAudioIndex = sai;
    }

    public void setSelectedSubtitleStream(final int ssi) {
        this.selectedSubtitleIndex = ssi;
    }

    public void init(){
        final SparseArray<SecondaryStreamHelper<AudioStream>> secondaryStreams
                = new SparseArray<>(4);
        final List<VideoStream> videoStreams = wrappedVideoStreams.getStreamsList();

        for (int i = 0; i < videoStreams.size(); i++) {
            if (!videoStreams.get(i).isVideoOnly()) {
                continue;
            }
            final AudioStream audioStream = SecondaryStreamHelper
                    .getAudioStreamFor(wrappedAudioStreams.getStreamsList(), videoStreams.get(i));

            if (audioStream != null) {
                secondaryStreams
                        .append(i, new SecondaryStreamHelper<>(wrappedAudioStreams, audioStream));
            }
        }

        this.videoStreamsAdapter = new StreamItemAdapter<>(context, wrappedVideoStreams,
                secondaryStreams);
        this.audioStreamsAdapter = new StreamItemAdapter<>(context, wrappedAudioStreams);
        this.subtitleStreamsAdapter = new StreamItemAdapter<>(context, wrappedSubtitleStreams);

        String filenameTmp = currentInfo.getName().concat(".");
        StoredDirectoryHelper mainStorage;
        MediaFormat format;
        String mimeTmp;
        String uri;
        boolean shouldUseAudioStorage = false;
        if(audioStreamsAdapter.getAll().size() == 0) {
            type = DownloadType.VIDEO;
            shouldUseAudioStorage = true;
        }
        switch (type) {
            case AUDIO:
                format = audioStreamsAdapter.getItem(selectedAudioIndex).getFormat();
                if (format == MediaFormat.WEBMA_OPUS) {
                    mimeTmp = "audio/ogg";
                    filenameTmp += "opus";
                } else {
                    mimeTmp = format.mimeType;
                    filenameTmp += format.suffix;
                }
                uri = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.download_path_audio_key), "");
                if (uri.isEmpty()) {
                    throw new RuntimeException("No download path selected");
                }
                try {
                    mainStorage = new StoredDirectoryHelper(context, Uri.parse(uri), DownloadManager.TAG_AUDIO);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            case VIDEO:
                format = videoStreamsAdapter.getItem(selectedVideoIndex).getFormat();
                mimeTmp = format.mimeType;
                filenameTmp += format.suffix;
                uri = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.download_path_video_key), "");
                if (uri.isEmpty()) {
                    throw new RuntimeException("No download path selected");
                }
                try {
                    mainStorage = new StoredDirectoryHelper(context, Uri.parse(uri), shouldUseAudioStorage? DownloadManager.TAG_AUDIO: DownloadManager.TAG_VIDEO);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            case SUBTITLES:
                format = subtitleStreamsAdapter.getItem(selectedSubtitleIndex).getFormat();
                mimeTmp = format.mimeType;
                filenameTmp += (format == MediaFormat.TTML ? MediaFormat.SRT : format).suffix;
                uri = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.download_path_video_key), "");
                if (uri.isEmpty()) {
                    throw new RuntimeException("No download path selected");
                }
                try {
                    mainStorage = new StoredDirectoryHelper(context, Uri.parse(uri), DownloadManager.TAG_VIDEO);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            default:
                throw new RuntimeException("Unknown download type"); // should never happen
        }
        Uri targetFile = mainStorage.findFile(filenameTmp);
        StoredFileHelper storage;
        String filename = filenameTmp;
        String mime = mimeTmp;
        try {
            if (targetFile == null) {
                // the file does not exist, but it is probably used in a pending download
                storage = new StoredFileHelper(mainStorage.getUri(), filename, mime,
                        mainStorage.getTag());
            } else {
                return; // we don't re-download files that already exist
            }
        } catch (final Exception e) {
            ErrorUtil.createNotification(context,
                    new ErrorInfo(e, UserAction.DOWNLOAD_FAILED, "Getting storage"));
        }
        if (!mainStorage.mkdirs()) {
            // the directory does not exist and we can't create it
            throw new RuntimeException("Directory does not exist");
        }

        storage = mainStorage.createFile(filename, mime);
        if (storage == null || !storage.canWrite()) {
            throw new RuntimeException("Can't write to file");
        }

        if(currentInfo.getServiceId() == ServiceList.BiliBili.getServiceId() && type == DownloadType.VIDEO){
            mainStorage.createFile(filename.replace(".mp4", ".tmp.mp4"), "video/mp4");
            mainStorage.createFile(filename.replace(".mp4", ".tmp"), String.valueOf(MediaFormat.M4A));
        }

        startDownload(storage);
    }

    public void startDownload(StoredFileHelper storage) {
        final Stream selectedStream;
        Stream secondaryStream = null;
        final char kind;
        int threads = 4;
        final String[] urls;
        final MissionRecoveryInfo[] recoveryInfo;
        String psName = null;
        String[] psArgs = null;
        long nearLength = 0;
        switch (type) {
            case AUDIO:
                kind = 'a';
                selectedStream = audioStreamsAdapter.getItem(selectedAudioIndex);

                if (selectedStream.getFormat() == MediaFormat.M4A && currentInfo.getServiceId() != ServiceList.BiliBili.getServiceId()) {
                    psName = Postprocessing.ALGORITHM_M4A_NO_DASH;
                } else if (selectedStream.getFormat() == MediaFormat.WEBMA_OPUS) {
                    psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER;
                }
                break;
            case VIDEO:
                kind = 'v';
                selectedStream = videoStreamsAdapter.getItem(selectedVideoIndex);

                final SecondaryStreamHelper<AudioStream> secondary = videoStreamsAdapter
                        .getAllSecondary()
                        .get(wrappedVideoStreams.getStreamsList().indexOf(selectedStream));

                if (secondary != null) {
                    secondaryStream = secondary.getStream();

                    if(currentInfo.getServiceId() == ServiceList.BiliBili.getServiceId()) {
                        psName = Postprocessing.BILIBILI_MUXER;
                    } else if (currentInfo.getService() == ServiceList.NicoNico) {
                        psName = Postprocessing.NICONICO_MUXER;
                    } else {
                        if (selectedStream.getFormat() == MediaFormat.MPEG_4) {
                            psName = Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER;
                        } else {
                            psName = Postprocessing.ALGORITHM_WEBM_MUXER;
                        }
                    }

                    psArgs = null;
                    final long videoSize = wrappedVideoStreams
                            .getSizeInBytes((VideoStream) selectedStream);

                    // set nearLength, only, if both sizes are fetched or known. This probably
                    // does not work on slow networks but is later updated in the downloader
                    if (secondary.getSizeInBytes() > 0 && videoSize > 0) {
                        nearLength = secondary.getSizeInBytes() + videoSize;
                    }
                }
                break;
            case SUBTITLES:
                threads = 1; // use unique thread for subtitles due small file size
                kind = 's';
                selectedStream = subtitleStreamsAdapter.getItem(selectedSubtitleIndex);
                if(currentInfo.getServiceId() == ServiceList.BiliBili.getServiceId()){
                    try {
                        OutputStream outputStream = storage.context.getContentResolver().openOutputStream(storage.getUri());
                        outputStream.write(selectedStream.getContent().getBytes());
                        outputStream.close();
                        return;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (selectedStream.getFormat() == MediaFormat.TTML) {
                    psName = Postprocessing.ALGORITHM_TTML_CONVERTER;
                    psArgs = new String[]{
                            selectedStream.getFormat().getSuffix(),
                            "false" // ignore empty frames
                    };
                }
                break;
            default:
                throw new RuntimeException("Unknown download type"); // should never happen
        }
        if (secondaryStream == null) {
            urls = new String[]{
                    selectedStream.getContent()
            };
            recoveryInfo = new MissionRecoveryInfo[]{
                    new MissionRecoveryInfo(selectedStream)
            };
        } else {
            urls = new String[]{
                    selectedStream.getContent(),
                    secondaryStream.getContent()
            };
            recoveryInfo = new MissionRecoveryInfo[]{new MissionRecoveryInfo(selectedStream),
                    new MissionRecoveryInfo(secondaryStream)};
        }

        DownloadManagerService.startMission(context, urls, storage, kind, threads,
                currentInfo.getUrl(), psName, psArgs, nearLength, recoveryInfo);
    }
}
