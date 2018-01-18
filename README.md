# napplytics
NApplytics performs measurements of multiple actions to recommend the best configuration options, and so developers can apply this configuration in their Android applications. This App uses Mobilyzer library for web browsing service and ExoPlayer library for video streaming service to calculate network throughput.

For the web browsing service, we have three configuration options: HTTP 1.1, HTTP 1.1 TLS and HTTP 2. In this case, we need the throughput value, the RSSI (Received Signal Strength Indicator) value and the RSRP (Reference Signals Received Power) value, and then the App calculates three Satisfaction Indices (SI) and it displays the option with the best SI.

For the video streaming service, we have two configuration options: HTTP and RTSP. In this case, we need the throughput value, the RSSI value and the RSRP value, and then the App calculates two Satisfaction Indices and it displays the option with the best SI.
