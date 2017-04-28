package com.eurobcreative.monroe.util.video;

import android.app.Service;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import com.eurobcreative.monroe.UpdateIntent;
import com.eurobcreative.monroe.util.Logger;
import com.eurobcreative.monroe.util.video.player.DashVodRendererBuilder;
import com.eurobcreative.monroe.util.video.player.DefaultRendererBuilder;
import com.eurobcreative.monroe.util.video.player.DemoPlayer;
import com.eurobcreative.monroe.util.video.util.DemoUtil;
import com.google.android.exoplayer.ExoPlayer;

/**
 * An activity that plays media using {@link DemoPlayer}.
 */
public class VideoPlayerService extends Service implements //SurfaceHolder.Callback,
        DemoPlayer.Listener {

    private static final int MENU_GROUP_TRACKS = 1;
    private static final int ID_OFFSET = 2;

    private EventLogger eventLogger;
   // private TextView debugTextView;
    //private TextView playerStateTextView;

    private DemoPlayer player;
    private boolean playerNeedsPrepare;

    private boolean autoPlay = true;
    private int playerPosition;
    private boolean enableBackgroundAudio = false;

    private Uri contentUri;
    private int contentType;
    private String contentId;

    private SurfaceTexture mTexture;
    private Surface mSurface;

    private boolean isResultSent;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.i("Video Player service started!");
        contentUri = intent.getData();
        contentType = intent.getIntExtra(DemoUtil.CONTENT_TYPE_EXTRA, DemoUtil.TYPE_PROGRESSIVE);
        contentId = intent.getStringExtra(DemoUtil.CONTENT_ID_EXTRA);

        this.isResultSent = false;
        preparePlayer();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releasePlayer(true);
    }

    // Internal methods
    private DemoPlayer.RendererBuilder getRendererBuilder() {
        String userAgent = DemoUtil.getUserAgent(this);
        if (this.contentType == DemoUtil.TYPE_DASH_VOD) {
            return new DashVodRendererBuilder(userAgent, contentUri.toString(), contentId,
                    new WidevineTestMediaDrmCallback(contentId), null, DashVodRendererBuilder.AdaptiveType.CBA);
        } else if (this.contentType == DemoUtil.TYPE_BBA) {
            return new DashVodRendererBuilder(userAgent, contentUri.toString(), contentId,
                    new WidevineTestMediaDrmCallback(contentId), null, DashVodRendererBuilder.AdaptiveType.BBA);
        } else if (this.contentType == DemoUtil.TYPE_PROGRESSIVE) {
            return new DefaultRendererBuilder(this, contentUri, null);
        } else {
            return null;
        }
    }

    private void preparePlayer() {
        if (player == null) {
            player = new DemoPlayer(getRendererBuilder());
            player.addListener(this);
            player.seekTo(playerPosition);
            playerNeedsPrepare = true;
            eventLogger = new EventLogger();
            eventLogger.startSession();
            player.addListener(eventLogger);
            player.setInfoListener(eventLogger);
            player.setInternalErrorListener(eventLogger);
        }
        if (playerNeedsPrepare) {
            player.prepare();
            playerNeedsPrepare = false;
        }
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int textureID = textures[0];
        Log.e("TextureId", "" + textureID);
        mTexture = new SurfaceTexture(textureID);
        mSurface = new Surface(mTexture);
        player.setSurface(mSurface);
        maybeStartPlayback();
    }

    private void maybeStartPlayback() {
        if (autoPlay) {
            player.setPlayWhenReady(true);
            autoPlay = false;
        }
    }

    private void releasePlayer(boolean isSucceed) {
        if (player != null) {
            playerPosition = player.getCurrentPosition();
            player.release();
            player = null;
            if (!isResultSent) {
                Intent videoResult = eventLogger.endSession();
                videoResult.putExtra(UpdateIntent.VIDEO_TASK_PAYLOAD_IS_SUCCEED, isSucceed);
                this.sendBroadcast(videoResult);
                isResultSent = true;
            }
            eventLogger = null;
        }
    }

    // DemoPlayer.Listener implementation
    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        String text = "playWhenReady=" + playWhenReady + ", playbackState=";
        switch (playbackState) {
            case ExoPlayer.STATE_BUFFERING:
                text += "buffering";
                break;
            case ExoPlayer.STATE_ENDED:
                text += "ended";
                break;
            case ExoPlayer.STATE_IDLE:
                text += "idle";
                break;
            case ExoPlayer.STATE_PREPARING:
                text += "preparing";
                break;
            case ExoPlayer.STATE_READY:
                text += "ready";
                break;
            default:
                text += "unknown";
                break;
        }
        Log.e("", text);

        if (playbackState == ExoPlayer.STATE_ENDED) {
            Log.e("", "Playback ended!");
            releasePlayer(true);
            this.stopSelf();
        }
//    playerStateTextView.setText(text);
    }

    @Override
    public void onError(Exception e) {
        Log.e("BgPlayerService", "Error occurs!");
        playerNeedsPrepare = true;
        releasePlayer(false);
        this.stopSelf();
    }

    @Override
    public void onVideoSizeChanged(int width, int height) {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}