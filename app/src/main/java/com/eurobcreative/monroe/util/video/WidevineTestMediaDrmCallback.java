package com.eurobcreative.monroe.util.video;

import android.annotation.TargetApi;
import android.media.MediaDrm.KeyRequest;
import android.media.MediaDrm.ProvisionRequest;
import android.text.TextUtils;

import com.eurobcreative.monroe.util.video.util.DemoUtil;
import com.google.android.exoplayer.drm.MediaDrmCallback;

import java.io.IOException;
import java.util.UUID;

/**
 * A {@link MediaDrmCallback} for Widevine test content.
 */
@TargetApi(18)
public class WidevineTestMediaDrmCallback implements MediaDrmCallback {

    private static final String WIDEVINE_GTS_DEFAULT_BASE_URI = "http://wv-staging-proxy.appspot.com/proxy?provider=YouTube&video_id=";

    private final String defaultUri;

    public WidevineTestMediaDrmCallback(String videoId) {
        defaultUri = WIDEVINE_GTS_DEFAULT_BASE_URI + videoId;
    }

    @Override
    public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request) throws IOException {
        String url = request.getDefaultUrl() + "&signedRequest=" + new String(request.getData());
        return DemoUtil.executePost(url, null, null);
    }

    @Override
    public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws IOException {
        String url = request.getDefaultUrl();
        if (TextUtils.isEmpty(url)) {
            url = defaultUri;
        }
        return DemoUtil.executePost(url, request.getData(), null);
    }
}