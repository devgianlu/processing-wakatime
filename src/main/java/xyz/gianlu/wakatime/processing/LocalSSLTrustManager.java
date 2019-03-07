package xyz.gianlu.wakatime.processing;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

class LocalSSLTrustManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
    }

    @Override
    public final X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}