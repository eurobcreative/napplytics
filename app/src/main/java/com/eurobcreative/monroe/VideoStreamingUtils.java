package com.eurobcreative.monroe;

public class VideoStreamingUtils {
    private static final String HTTP = "HTTP";
    private static final String RTSP = "RTSP";

    public static final String[] urlServerVideoHlsModeArray = {"https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"};
    public static final String[] urlVideoStreamingDashModeArray = {"http://dash.edgesuite.net/dash264/TestCasesMCA/dolby/5/11/Living_Room_720p_51_51_192k_320k_25fps.mpd"};
            //"http://www-itec.uni-klu.ac.at/ftp/datasets/DASHDataset2014/OfForestAndMen/15sec/OfForestAndMen_15s_simple_2014_05_09.mpd"};
    public static final String[] urlServerVideoMp4ModeArray = {"http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4"};
            /*"http://yt-dash-mse-test.commondatastorage.googleapis.com/media/car-20120827-85.mp4"),*/

    public static final int HSPA_NETWORK = 0;
    public static final int LTE_NETWORK = 1;

    public static final String[] networkTypeArray = {"HSPA", "LTE"};

    public static final int HLS_MODE = 0;
    public static final int DASH_MODE = 1;
    public static final int MP4_MODE = 2;

    // LTE
    public static float SIThroughputVideoStreamingLTE(long throughput /*Mbps*/) {
        if (throughput <= 5) {
            return 1.0f;

        } else if (throughput > 5 && throughput <= 8) {
            return ((float) (throughput - 5) / 3) + 1;

        } else if (throughput > 8 && throughput <= 11) {
            return (((float) (throughput - 8)) / 3) + 2;

        } else if (throughput > 11 && throughput <= 30) {
            return (((float) (throughput - 11)) / 19) + 3;

        } else if (throughput > 30 && throughput <= 55) {
            return (((float) (throughput - 30)) / 25) + 4;

        } else {
            return 5.0f;
        }
    }

    public static float SIRsrpVideoStreamingLTE(int rsrp) {
        if (rsrp <= -116) {
            return 1.0f;

        } else if (rsrp > -116 && rsrp <= -108) {
            return ((float) (rsrp + 116) / 8) + 1;

        } else if (rsrp > -108 && rsrp <= -101) {
            return (((float) (rsrp + 108)) / 7) + 2;

        } else if (rsrp > -101 && rsrp <= -95) {
            return (((float) (rsrp + 101)) / 6) + 3;

        } else if (rsrp > -95 && rsrp <= -89) {
            return (((float) (rsrp + 95)) / 6) + 4;

        } else {
            return 5.0f;
        }
    }

    public static float SIRssiVideoStreamingLTE(int rssi) {
        if (rssi <= -107) {
            return 1.0f;

        } else if (rssi > -107 && rssi <= -102) {
            return ((float) (rssi + 107) / 5) + 1;

        } else if (rssi > -102 && rssi <= -92) {
            return (((float) (rssi + 102)) / 10) + 2;

        } else if (rssi > -92 && rssi <= -84) {
            return (((float) (rssi + 92)) / 8) + 3;

        } else if (rssi > -84 && rssi <= -79) {
            return (((float) (rssi + 84)) / 5) + 4;

        } else {
            return 5.0f;
        }
    }


    public static double calculateVideoStreamingHttpLTE(float si_throughput, float si_rsrp, float si_rssi) {
        return (0.5215 * si_throughput) + (0.5495 * si_rsrp) + (0 * si_rssi);
    }

    public static double calculateVideoStreamingRTSPLTE(float si_throughput, float si_rsrp, float si_rssi) {
        return (0.8477 * si_throughput) + (0.2404 * si_rsrp) + (0 * si_rssi);
    }


    public static String getBetterVideoStreamingLTEService(float si_throughput, float si_rsrp, float si_rssi) {
        double http_lte = calculateVideoStreamingHttpLTE(si_throughput, si_rsrp, si_rssi);
        double rtsp_lte = calculateVideoStreamingRTSPLTE(si_throughput, si_rsrp, si_rssi);

        if (http_lte > rtsp_lte) {
            return HTTP;

        } else {
            return RTSP;
        }
    }


    // HSPA
    public static float SIThroughputVideoStreamingHSPA(long throughput /*Mbps*/) {
        if (throughput <= 1) {
            return 1.0f;

        } else if (throughput > 1 && throughput <= 2) {
            return (float) (throughput - 1) + 1;

        } else if (throughput > 2 && throughput <= 5) {
            return (((float) (throughput - 2)) / 3) + 2;

        } else if (throughput > 5 && throughput <= 7) {
            return (((float) (throughput - 5)) / 2) + 3;

        } else if (throughput > 7 && throughput <= 55) {
            return (((float) (throughput - 7)) / 2) + 4;

        } else {
            return 5.0f;
        }
    }

    public static float SIRsrpVideoStreamingHSPA(int rsrp) {
        if (rsrp <= -109) {
            return 1.0f;

        } else if (rsrp > -109 && rsrp <= -107) {
            return ((float) (rsrp + 109) / 2) + 1;

        } else if (rsrp > -107 && rsrp <= -100) {
            return (((float) (rsrp + 107)) / 7) + 2;

        } else if (rsrp > -100 && rsrp <= -96) {
            return (((float) (rsrp + 100)) / 4) + 3;

        } else if (rsrp > -96 && rsrp <= -86) {
            return (((float) (rsrp + 96)) / 10) + 4;

        } else {
            return 5.0f;
        }
    }

    public static float SIRssiVideoStreamingHSPA(int rssi) {
        if (rssi <= -108) {
            return 1.0f;

        } else if (rssi > -108 && rssi <= -107) {
            return (float) (rssi + 108) + 1;

        } else if (rssi > -107 && rssi <= -106) {
            return (float) (rssi + 107) + 2;

        } else if (rssi > -106 && rssi <= -104) {
            return (((float) (rssi + 106)) / 2) + 3;

        } else if (rssi > -104 && rssi <= -101) {
            return (((float) (rssi + 104)) / 3) + 4;

        } else {
            return 5.0f;
        }
    }


    public static double calculateVideoStreamingHttpHSPA(float si_throughput, float si_rsrp, float si_rssi) {
        return (0.6019 * si_throughput) + (0.5179 * si_rsrp) + (0 * si_rssi);
    }

    public static double calculateVideoStreamingRTSPHSPA(float si_throughput, float si_rsrp, float si_rssi) {
        return (0.3799 * si_throughput) + (0.3895 * si_rsrp) + (0 * si_rssi);
    }


    public static String getBetterVideoStreamingHSPAService(float si_throughput, float si_rsrp, float si_rssi) {
        double http_hspa = calculateVideoStreamingHttpHSPA(si_throughput, si_rsrp, si_rssi);
        double rtsp_hspa = calculateVideoStreamingRTSPHSPA(si_throughput, si_rsrp, si_rssi);

        if (http_hspa > rtsp_hspa) {
            return HTTP;

        } else {
            return RTSP;
        }
    }


    public static String calculateBetterVideoStreamingService(double http, double rtsp) {
        if (http > rtsp) {
            return HTTP;

        } else {
            return RTSP;
        }
    }
}