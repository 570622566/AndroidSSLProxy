package me.lty.ssltest.mitm.tool;

import android.util.LruCache;

import java.security.cert.X509Certificate;

import me.lty.ssltest.mitm.CAConfig;
import me.lty.ssltest.mitm.tool.CertUtil;

public class CertPool {

    private static final int CERT_CACHE_SIZE = 20;

    private static LruCache<String, X509Certificate> certCache = new LruCache<>(CERT_CACHE_SIZE);

    public static X509Certificate getCert(String host, CAConfig config) throws Exception {
        X509Certificate x509Certificate = null;
        if (host != null) {
            String key = host.trim().toLowerCase();
            x509Certificate = certCache.get(key);
            if (x509Certificate == null) {
                x509Certificate = CertUtil.genCert(config.getIssuer(), config.getCaPriKey(),
                                                   config.getCaNotBefore(), config.getCaNotAfter(),
                                                   config.getServerPubKey(), key
                );
                certCache.put(key, x509Certificate);
            }
        }
        return x509Certificate;
    }
}
