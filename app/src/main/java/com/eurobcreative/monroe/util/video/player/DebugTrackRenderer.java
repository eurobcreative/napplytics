package com.eurobcreative.monroe.util.video.player;

import android.widget.TextView;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.Format;

/**
 * A {@link TrackRenderer} that periodically updates debugging information displayed by a {@link TextView}.
 */
class DebugTrackRenderer extends TrackRenderer implements Runnable {

    private final TextView textView;
    private final MediaCodecTrackRenderer renderer;
    private final ChunkSampleSource videoSampleSource;

    private volatile boolean pendingFailure;
    private volatile long currentPositionUs;

    public DebugTrackRenderer(TextView textView, MediaCodecTrackRenderer renderer) {
        this(textView, renderer, null);
    }

    public DebugTrackRenderer(TextView textView, MediaCodecTrackRenderer renderer, ChunkSampleSource videoSampleSource) {
        this.textView = textView;
        this.renderer = renderer;
        this.videoSampleSource = videoSampleSource;
    }

    public void injectFailure() {
        pendingFailure = true;
    }

    @Override
    protected boolean isEnded() {
        return true;
    }

    @Override
    protected boolean isReady() {
        return true;
    }

    @Override
    protected int doPrepare() throws ExoPlaybackException {
        maybeFail();
        return STATE_PREPARED;
    }

    @Override
    protected void doSomeWork(long timeUs) throws ExoPlaybackException {
        maybeFail();
        if (timeUs < currentPositionUs || timeUs > currentPositionUs + 1000000) {
            currentPositionUs = timeUs;
            textView.post(this);
        }
    }

    @Override
    public void run() {
        textView.setText(getRenderString());
    }

    private String getRenderString() {
        return "ms(" + (currentPositionUs / 1000) + "), " + getQualityString() + ", " + renderer.codecCounters.getDebugString();
    }

    private String getQualityString() {
        Format format = videoSampleSource == null ? null : videoSampleSource.getFormat();
        return format == null ? "null" : "height(" + format.height + "), itag(" + format.id + ")";
    }

    @Override
    protected long getCurrentPositionUs() {
        return currentPositionUs;
    }

    @Override
    protected long getDurationUs() {
        return TrackRenderer.MATCH_LONGEST_US;
    }

    @Override
    protected long getBufferedPositionUs() {
        return TrackRenderer.END_OF_TRACK_US;
    }

    @Override
    protected void seekTo(long timeUs) {
        currentPositionUs = timeUs;
    }

    private void maybeFail() throws ExoPlaybackException {
        if (pendingFailure) {
            pendingFailure = false;
            throw new ExoPlaybackException("fail() was called on DebugTrackRenderer");
        }
    }
}