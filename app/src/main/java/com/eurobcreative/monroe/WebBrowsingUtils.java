package com.eurobcreative.monroe;

public class WebBrowsingUtils {
    private static final String HTTP1_1 = "HTTP1_1";
    private static final String HTTP2 = "HTTP2";
    private static final String HTTP1_1TLS = "HTTP1_1TLS";

    public static final String[] urlWebBrowsingArray = {"www.google.es", "www.google.it", "www.google.com"};
            //{"https://napplytics.eurobcreative.com", "http://145.239.34.118:8554", "https://napplytics.eurobcreative.com:8080"};
    //public static final String[] protocolsHttpArray = {"HTTP2.0", "HTTP1.1", "HTTP1.1/TLS"};

    public static float SIThroughtputWebBrowsing(long throughput) {
        if (throughput <= 0) {
            return 1.0f;

        } else if (throughput > 0 && throughput <= 500) {
            return ((float) throughput / 500) + 1;

        } else if (throughput > 500 && throughput <= 800) {
            return (((float) (throughput - 500)) / 300) + 2;

        } else if (throughput > 800 && throughput <= 900) {
            return (((float) (throughput - 800)) / 100) + 3;

        } else if (throughput > 900 && throughput <= 1300) {
            return (((float) (throughput - 900)) / 400) + 4;

        } else {
            return 5.0f;
        }
    }

    public static float SIRsrpWebBrowsing(int rsrp) {
        if (rsrp <= -109) {
            return 1.0f;

        } else if (rsrp > -109 && rsrp <= -103) {
            return ((float) (rsrp + 109) / 6) + 1;

        } else if (rsrp > -103 && rsrp <= -97) {
            return (((float) (rsrp + 103)) / 6) + 2;

        } else if (rsrp > -97 && rsrp <= -92) {
            return (((float) (rsrp + 97)) / 5) + 3;

        } else if (rsrp > -92 && rsrp <= -88) {
            return (((float) (rsrp + 92)) / 4) + 4;

        } else {
            return 5.0f;
        }
    }

    public static float SIRssiWebBrowsing(int rssi) {
        if (rssi <= -81) {
            return 1.0f;

        } else if (rssi > -81 && rssi <= -76) {
            return ((float) (rssi + 81) / 5) + 1;

        } else if (rssi > -76 && rssi <= -71) {
            return (((float) (rssi + 76)) / 5) + 2;

        } else if (rssi > -71 && rssi <= -66) {
            return (((float) (rssi + 71)) / 5) + 3;

        } else if (rssi > -66 && rssi <= -60) {
            return (((float) (rssi + 66)) / 6) + 4;

        } else {
            return 5.0f;
        }
    }


    public static double calculateWebBrowsingHttp1_1(float si_throughput, float si_rsrp, float si_rssi) {
        return (0.275906516431794 * si_throughput) + (0.20608960296224 * si_rsrp) + (0.187881476178343 * si_rssi);
    }

    public static double calculateWebBrowsingHttp2(float si_throughput, float si_rsrp, float si_rssi) {
        return (0.274590130897324 * si_throughput) + (0.224955809413482 * si_rsrp) + (0.200835527674662 * si_rssi);
    }

    public static double calculateWebBrowsingHttp1_1TLS(float si_throughput, float si_rsrp, float si_rssi) {
        return (0.273217929983119 * si_throughput) + (0.224515432839308 * si_rsrp) + (0.191683339409728 * si_rssi);
    }


    public static String betterWebBrowsingService(float si_throughput, float si_rsrp, float si_rssi) {
        double http1_1 = calculateWebBrowsingHttp1_1(si_throughput, si_rsrp, si_rssi);
        double http2 = calculateWebBrowsingHttp2(si_throughput, si_rsrp, si_rssi);
        double http1_1tls = calculateWebBrowsingHttp1_1TLS(si_throughput, si_rsrp, si_rssi);

        if (http1_1 > http2 && http1_1 > http1_1tls) {
            return HTTP1_1;

        } else if (http2 > http1_1 && http2 > http1_1tls) {
            return HTTP2;

        } else {
            return HTTP1_1TLS;
        }
    }

    public static String getBetterWebBrowsingService(double http1_1, double http1_1tls, double http2) {
        if (http1_1 > http2 && http1_1 > http1_1tls) {
            return HTTP1_1;

        } else if (http2 > http1_1 && http2 > http1_1tls) {
            return HTTP2;

        } else {
            return HTTP1_1TLS;
        }
    }
}