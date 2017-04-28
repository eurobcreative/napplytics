package com.eurobcreative.monroe.util.video.player;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.UnsupportedSchemeException;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Pair;
import android.widget.TextView;

import com.eurobcreative.monroe.util.video.EventLogger;
import com.eurobcreative.monroe.util.video.util.DemoUtil;
import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.MultiTrackChunkSource;
import com.google.android.exoplayer.dash.DashChunkSource;
import com.google.android.exoplayer.dash.mpd.AdaptationSet;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescriptionFetcher;
import com.google.android.exoplayer.dash.mpd.Period;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.drm.DrmSessionManager;
import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.drm.StreamingDrmSessionManager;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.HttpDataSource;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import java.util.ArrayList;

/**
 * A {@link DemoPlayer.RendererBuilder} for DASH VOD.
 */
public class DashVodRendererBuilder implements DemoPlayer.RendererBuilder, ManifestCallback<MediaPresentationDescription> {

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    //  private static final int VIDEO_BUFFER_SEGMENTS = 200;
    private static final int VIDEO_BUFFER_SEGMENTS = 1000;
    private static final int AUDIO_BUFFER_SEGMENTS = 60;

    private static final int SECURITY_LEVEL_UNKNOWN = -1;
    private static final int SECURITY_LEVEL_1 = 1;
    private static final int SECURITY_LEVEL_3 = 3;

    public enum AdaptiveType {
        BBA, CBA
    }

    private final String userAgent;
    private final String url;
    private final String contentId;
    private final MediaDrmCallback drmCallback;
    private final TextView debugTextView;

    private DemoPlayer player;
    private DemoPlayer.RendererBuilderCallback callback;

    private AdaptiveType adaptiveType;

    public DashVodRendererBuilder(String userAgent, String url, String contentId, MediaDrmCallback drmCallback,
                                  TextView debugTextView, AdaptiveType adaptiveType) {
        this.userAgent = userAgent;
        this.url = url;
        this.contentId = contentId;
        this.drmCallback = drmCallback;
        this.debugTextView = debugTextView;
        this.adaptiveType = adaptiveType;
    }

    @Override
    public void buildRenderers(DemoPlayer player, DemoPlayer.RendererBuilderCallback callback) {
        this.player = player;
        this.callback = callback;
        MediaPresentationDescriptionFetcher mpdFetcher = new MediaPresentationDescriptionFetcher(this);
        mpdFetcher.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url, contentId);
    }

    @Override
    public void onManifestError(String contentId, Exception e) {
        callback.onRenderersError(e);
    }

    @Override
    public void onManifest(String contentId, MediaPresentationDescription manifest) {
        Handler mainHandler = player.getMainHandler();
        LoadControl loadControl = new DefaultLoadControl(new BufferPool(BUFFER_SEGMENT_SIZE));
//    DefaultBandwidthMeter videoBandwidthMeter = new DefaultBandwidthMeter(mainHandler, player);
        DefaultBandwidthMeter videoBandwidthMeter = new DefaultBandwidthMeter(mainHandler, player);
        DefaultBandwidthMeter audioBandwidthMeter = new DefaultBandwidthMeter(mainHandler, player);

        // Obtain Representations for playback.
        int maxDecodableFrameSize = MediaCodecUtil.maxH264DecodableFrameSize();
        ArrayList<Representation> audioRepresentationsList = new ArrayList<>();
        ArrayList<Representation> videoRepresentationsList = new ArrayList<>();
        Period period = manifest.periods.get(0);
        boolean hasContentProtection = false;
        for (int i = 0; i < period.adaptationSets.size(); i++) {
            AdaptationSet adaptationSet = period.adaptationSets.get(i);
            hasContentProtection |= adaptationSet.hasContentProtection();
            int adaptationSetType = adaptationSet.type;
            for (int j = 0; j < adaptationSet.representations.size(); j++) {
                Representation representation = adaptationSet.representations.get(j);
                if (adaptationSetType == AdaptationSet.TYPE_AUDIO) {
                    audioRepresentationsList.add(representation);
                } else if (adaptationSetType == AdaptationSet.TYPE_VIDEO) {
                    Format format = representation.format;
                    if (format.width * format.height <= maxDecodableFrameSize) {
                        videoRepresentationsList.add(representation);
                    } else {
                        // The device isn't capable of playing this stream.
                    }
                }
            }
        }
        Representation[] videoRepresentations = new Representation[videoRepresentationsList.size()];
        videoRepresentationsList.toArray(videoRepresentations);

        // Hongyi: create lookup table from video resolution and resource id to video bitrate
        for (Representation r : videoRepresentations) {
            EventLogger.Resolution2Bitrate.put(Pair.create(r.format.width, r.format.height), r.format.bitrate);
            EventLogger.Id2Bitrate.put(r.format.id, r.format.bitrate);
        }
        // Hongyi: create lookup table from audio resource id to audio bitrate
        for (Representation r : audioRepresentationsList) {
            EventLogger.Id2Bitrate.put(r.format.id, r.format.bitrate);
        }

        // Check drm support if necessary.
        DrmSessionManager drmSessionManager = null;
        if (hasContentProtection) {
            if (Util.SDK_INT < 18) {
                callback.onRenderersError(new UnsupportedOperationException("Protected content not supported on API level " + Util.SDK_INT));
                return;
            }
            try {
                Pair<DrmSessionManager, Boolean> drmSessionManagerData = V18Compat.getDrmSessionManagerData(player, drmCallback);
                drmSessionManager = drmSessionManagerData.first;
                if (!drmSessionManagerData.second) {
                    // HD streams require L1 security.
                    videoRepresentations = getSdRepresentations(videoRepresentations);
                }
            } catch (Exception e) {
                callback.onRenderersError(e);
                return;
            }
        }

        // Build the video renderer.
//    DataSource videoDataSource = new HttpDataSource(userAgent, null, bandwidthMeter);
        DataSource videoDataSource = new HttpDataSource(userAgent, null, videoBandwidthMeter);
        ChunkSource videoChunkSource;
        String mimeType = videoRepresentations[0].format.mimeType;
        if (mimeType.equals(MimeTypes.VIDEO_MP4) || mimeType.equals(MimeTypes.VIDEO_WEBM)) {
//      videoChunkSource = new DashChunkSource(videoDataSource,
//          new AdaptiveEvaluator(bandwidthMeter), videoRepresentations);
            if (adaptiveType == AdaptiveType.CBA) {
                videoChunkSource = new DashChunkSource(videoDataSource, new FormatEvaluator.AdaptiveEvaluator(videoBandwidthMeter), videoRepresentations);
            } else {
                //ALV videoChunkSource = new DashChunkSource(videoDataSource, new BufferBasedAdaptiveEvaluator(videoBandwidthMeter, manifest.duration, mainHandler, player ), videoRepresentations);
                throw new IllegalStateException("Unexpected mime type: " + mimeType);
            }
        } else {
            throw new IllegalStateException("Unexpected mime type: " + mimeType);
        }
        ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
                VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true, mainHandler, player, DemoPlayer.TYPE_VIDEO);
//    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(videoSampleSource,
//        drmSessionManager, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000,
//        mainHandler, player, 50);
        MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(videoSampleSource,
                drmSessionManager, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, mainHandler, player, 1);

        // Build the audio renderer.
        final String[] audioTrackNames;
        final MultiTrackChunkSource audioChunkSource;
        final MediaCodecAudioTrackRenderer audioRenderer;
        if (audioRepresentationsList.isEmpty()) {
            audioTrackNames = null;
            audioChunkSource = null;
            audioRenderer = null;
        } else {
//      DataSource audioDataSource = new HttpDataSource(userAgent, null, bandwidthMeter);
            DataSource audioDataSource = new HttpDataSource(userAgent, null, audioBandwidthMeter);
            audioTrackNames = new String[audioRepresentationsList.size()];
            ChunkSource[] audioChunkSources = new ChunkSource[audioRepresentationsList.size()];
            FormatEvaluator audioEvaluator = new FormatEvaluator.FixedEvaluator();
            for (int i = 0; i < audioRepresentationsList.size(); i++) {
                Representation representation = audioRepresentationsList.get(i);
                Format format = representation.format;
                audioTrackNames[i] = format.id + " (" + format.numChannels + "ch, " + format.audioSamplingRate + "Hz)";
                audioChunkSources[i] = new DashChunkSource(audioDataSource, audioEvaluator, representation);
            }
            audioChunkSource = new MultiTrackChunkSource(audioChunkSources);
            SampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource, loadControl,
                    AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true, mainHandler, player, DemoPlayer.TYPE_AUDIO);
            audioRenderer = new MediaCodecAudioTrackRenderer(audioSampleSource, drmSessionManager, true,
                    mainHandler, player);
        }

        // Build the debug renderer.
        TrackRenderer debugRenderer = debugTextView != null ? new DebugTrackRenderer(debugTextView, videoRenderer, videoSampleSource) : null;

        // Invoke the callback.
        String[][] trackNames = new String[DemoPlayer.RENDERER_COUNT][];
        trackNames[DemoPlayer.TYPE_AUDIO] = audioTrackNames;

        MultiTrackChunkSource[] multiTrackChunkSources = new MultiTrackChunkSource[DemoPlayer.RENDERER_COUNT];
        multiTrackChunkSources[DemoPlayer.TYPE_AUDIO] = audioChunkSource;

        TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
        renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
        renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
        renderers[DemoPlayer.TYPE_DEBUG] = debugRenderer;
        callback.onRenderers(trackNames, multiTrackChunkSources, renderers);
    }

    private Representation[] getSdRepresentations(Representation[] representations) {
        ArrayList<Representation> sdRepresentations = new ArrayList<>();
        for (int i = 0; i < representations.length; i++) {
            if (representations[i].format.height < 720 && representations[i].format.width < 1280) {
                sdRepresentations.add(representations[i]);
            }
        }
        Representation[] sdRepresentationArray = new Representation[sdRepresentations.size()];
        sdRepresentations.toArray(sdRepresentationArray);
        return sdRepresentationArray;
    }

    @TargetApi(18)
    private static class V18Compat {
        public static Pair<DrmSessionManager, Boolean> getDrmSessionManagerData(DemoPlayer player, MediaDrmCallback drmCallback) throws UnsupportedSchemeException {
            StreamingDrmSessionManager streamingDrmSessionManager = new StreamingDrmSessionManager(
                    DemoUtil.WIDEVINE_UUID, player.getPlaybackLooper(), drmCallback, player.getMainHandler(),
                    player);
            return Pair.create((DrmSessionManager) streamingDrmSessionManager, getWidevineSecurityLevel(streamingDrmSessionManager) == SECURITY_LEVEL_1);
        }

        private static int getWidevineSecurityLevel(StreamingDrmSessionManager sessionManager) {
            String securityLevelProperty = sessionManager.getPropertyString("securityLevel");
            return securityLevelProperty.equals("L1") ? SECURITY_LEVEL_1 : securityLevelProperty.equals("L3") ? SECURITY_LEVEL_3 : SECURITY_LEVEL_UNKNOWN;
        }
    }
}