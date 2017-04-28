package com.eurobcreative.monroe.util.video.player;

import android.content.Context;
import android.media.MediaCodec;
import android.net.Uri;
import android.widget.TextView;

import com.google.android.exoplayer.FrameworkSampleSource;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;

/**
 * A {@link DemoPlayer.RendererBuilder} for streams that can be read using
 * {@link android.media.MediaExtractor}.
 */
public class DefaultRendererBuilder implements DemoPlayer.RendererBuilder {

  private final Context context;
  private final Uri uri;
  private final TextView debugTextView;

  public DefaultRendererBuilder(Context context, Uri uri, TextView debugTextView) {
    this.context = context;
    this.uri = uri;
    this.debugTextView = debugTextView;
  }

  @Override
  public void buildRenderers(DemoPlayer player, DemoPlayer.RendererBuilderCallback callback) {
    // Build the video and audio renderers.
    FrameworkSampleSource sampleSource = new FrameworkSampleSource(context, uri, null, 2);
    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
        null, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, player.getMainHandler(), player, 1);
    MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource, null, true, player.getMainHandler(), player);

    // Build the debug renderer.
    TrackRenderer debugRenderer = debugTextView != null ? new DebugTrackRenderer(debugTextView, videoRenderer) : null;

    // Invoke the callback.
    TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
    renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
    renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
    renderers[DemoPlayer.TYPE_DEBUG] = debugRenderer;
    callback.onRenderers(null, null, renderers);
  }
}