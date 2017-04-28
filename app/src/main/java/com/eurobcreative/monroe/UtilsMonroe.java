package com.eurobcreative.monroe;

public class UtilsMonroe {
    public static final String MONROE_ACTION = "Monroe";
    public static final String SPEED_RESULT = "speed";
    public static final String RSSI_RESULT = "rssi";

    public static float SISpeed (long speed){
        if (speed <= 0){
            return 1.0f;

        } else if (speed > 0 && speed <= 500){
            return ((float) speed / 500) + 1;

        } else if (speed > 500 && speed <= 800){
            return (((float) (speed - 500)) / 300) + 2;

        } else if (speed > 800 && speed <= 900){
            return (((float) (speed - 800)) / 100) + 3;

        } else if (speed > 900 && speed <= 1300) {
            return (((float) (speed - 900)) / 400) + 4;

        } else {
            return 5.0f;
        }
    }

    public static float SIRsrp (int rsrp){
        if (rsrp <= -109){
            return 1.0f;

        } else if (rsrp > -109 && rsrp <= -103){
            return ((float) (rsrp + 109) / 6) + 1;

        } else if (rsrp > -103 && rsrp <= -97){
            return (((float) (rsrp + 103)) / 6) + 2;

        } else if (rsrp > -97 && rsrp <= -92){
            return (((float) (rsrp + 97)) / 5) + 3;

        } else if (rsrp > -92 && rsrp <= -88) {
            return (((float) (rsrp + 92)) / 4) + 4;

        } else {
            return 5.0f;
        }
    }

    public static float SIRssi (int rssi){
        if (rssi <= -81){
            return 1.0f;

        } else if (rssi > -81 && rssi <= -76){
            return ((float) (rssi + 81) / 5) + 1;

        } else if (rssi > -76 && rssi <= -71){
            return (((float) (rssi + 76)) / 5) + 2;

        } else if (rssi > -71 && rssi <= -66){
            return (((float) (rssi + 71)) / 5) + 3;

        } else if (rssi > -66 && rssi <= -60) {
            return (((float) (rssi + 66)) / 6) + 4;

        } else {
            return 5.0f;
        }
    }

    private static final String HTTP1_1 = "HTTP1_1";
    private static final String HTTP2 = "HTTP2";
    private static final String HTTP1_1TLS = "HTTP1_1TLS";
    public static String betterHttpMode (float si_speed, float si_rsrp, float si_rssi){
        double http1_1 = (0.275906516431794 * si_speed) + (0.20608960296224 * si_rsrp) + (0.187881476178343 * si_rssi);
        double http2 = (0.274590130897324 * si_speed) + (0.224955809413482 * si_rsrp) + (0.200835527674662 * si_rssi);
        double http1_1tls = (0.273217929983119 * si_speed) + (0.224515432839308 * si_rsrp) + (0.191683339409728 * si_rssi);

        if (http1_1 > http2 && http1_1 > http1_1tls){
            return HTTP1_1;

        } else if (http2 > http1_1 && http2 > http1_1tls){
            return HTTP2;

        } else {
            return HTTP1_1TLS;
        }
    }
}