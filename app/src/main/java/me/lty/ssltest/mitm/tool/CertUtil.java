package me.lty.ssltest.mitm.tool;

import android.util.Log;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CertUtil {

    private static final String TAG = CertUtil.class.getSimpleName();

    private static KeyFactory keyFactory = null;
    private static final long TEN_YEAR = TimeUnit.DAYS.toMillis(3650);

    private static KeyFactory getKeyFactory() throws NoSuchAlgorithmException {
        if (keyFactory == null) {
            keyFactory = KeyFactory.getInstance("RSA");
        }
        return keyFactory;
    }

    /**
     * 生成RSA公私密钥对,长度为2048
     */
    public static KeyPair genKeyPair() throws Exception {
        KeyPairGenerator caKeyPairGen = KeyPairGenerator.getInstance("RSA", "BC");
        caKeyPairGen.initialize(2048, new SecureRandom());
        return caKeyPairGen.genKeyPair();
    }

    /**
     * 从文件加载RSA私钥 openssl pkcs8 -topk8 -nocrypt -inform PEM -outform DER -in ca.key -out
     * ca_private.der
     */
    public static PrivateKey loadPriKey(byte[] bts) throws Exception {
        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(bts);
        return getKeyFactory().generatePrivate(privateKeySpec);
    }

    /**
     * 从文件加载RSA私钥 openssl pkcs8 -topk8 -nocrypt -inform PEM -outform DER -in ca.key -out
     * ca_private.der
     */
    //public static PrivateKey loadPriKey(String path) throws Exception {
    //    return loadPriKey(Files.readAllBytes(Paths.get(path)));
    //}

    /**
     * 从文件加载RSA私钥 openssl pkcs8 -topk8 -nocrypt -inform PEM -outform DER -in ca.key -out
     * ca_private.der
     */
    //public static PrivateKey loadPriKey(URI uri) throws Exception {
    //    return loadPriKey(Paths.get(uri).toString());
    //}

    /**
     * 从文件加载RSA私钥 openssl pkcs8 -topk8 -nocrypt -inform PEM -outform DER -in ca.key -out
     * ca_private.der
     */
    public static PrivateKey loadPriKey(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] bts = new byte[1024];
        int len;
        while ((len = inputStream.read(bts)) != -1) {
            outputStream.write(bts, 0, len);
        }
        inputStream.close();
        outputStream.close();
        return loadPriKey(outputStream.toByteArray());
    }

    /**
     * 从文件加载RSA公钥 openssl rsa -in ca.key -pubout -outform DER -out ca_pub.der
     */
    public static PublicKey loadPubKey(byte[] bts) throws Exception {
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bts);
        return getKeyFactory().generatePublic(publicKeySpec);
    }

    /**
     * 从文件加载RSA公钥 openssl rsa -in ca.key -pubout -outform DER -out ca_pub.der
     */
    //public static PublicKey loadPubKey(String path) throws Exception {
    //    EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Files.readAllBytes(Paths.get(path)));
    //    return getKeyFactory().generatePublic(publicKeySpec);
    //}

    /**
     * 从文件加载RSA公钥 openssl rsa -in ca.key -pubout -outform DER -out ca_pub.der
     */
    //public static PublicKey loadPubKey(URI uri) throws Exception {
    //    return loadPubKey(Paths.get(uri).toString());
    //}

    /**
     * 从文件加载RSA公钥 openssl rsa -in ca.key -pubout -outform DER -out ca_pub.der
     */
    public static PublicKey loadPubKey(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] bts = new byte[1024];
        int len;
        while ((len = inputStream.read(bts)) != -1) {
            outputStream.write(bts, 0, len);
        }
        inputStream.close();
        outputStream.close();
        return loadPubKey(outputStream.toByteArray());
    }

    /**
     * 从文件加载证书
     */
    public static X509Certificate loadCert(InputStream inputStream) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(inputStream);
    }

    /**
     * 从文件加载证书
     */
    public static X509Certificate loadCert(String path) throws Exception {
        return loadCert(new FileInputStream(path));
    }

    /**
     * 从文件加载证书
     */
    //public static X509Certificate loadCert(URI uri) throws Exception {
    //    return loadCert(Paths.get(uri).toString());
    //}

    /**
     * 读取ssl证书使用者信息
     */
    public static String getSubject(X509Certificate certificate) throws Exception {
        //读出来顺序是反的需要反转下
        List<String> tempList = Arrays.asList(certificate.getIssuerDN().toString().split(", "));

        StringBuilder builder = new StringBuilder();
        for (int i = tempList.size() - 1; i >= 0; i--) {
            if (i != 0) {
                builder.append(tempList.get(i) + ", ");
            } else {
                builder.append(tempList.get(i));
            }
        }
        return builder.toString();
        //return IntStream.rangeClosed(0, tempList.size() - 1)
        //                .mapToObj(i -> tempList.get(tempList.size() - i - 1))
        //                .collect(Collectors.joining(", "));
    }

    /**
     * 动态生成服务器证书,并进行CA签授
     *
     * @param issuer 颁发机构
     */
    public static X509Certificate genCert(String issuer, PrivateKey caPriKey, Date caNotBefore,
                                          Date caNotAfter, PublicKey serverPubKey,
                                          String... hosts) throws Exception {
        /* String issuer = "C=CN, ST=GD, L=SZ, O=lee, OU=study, CN=ProxyeeRoot";
        String subject = "C=CN, ST=GD, L=SZ, O=lee, OU=study, CN=" + host;*/
        //根据CA证书subject来动态生成目标服务器证书的issuer和subject
        String subject = "C=CN, ST=BJ, L=BJ, O=BGH, OU=LLYH, CN=" + hosts[0];
        //doc from https://www.cryptoworkshop.com/guide/
        JcaX509v3CertificateBuilder jv3Builder = new JcaX509v3CertificateBuilder(
                new X500Name(issuer),
                //issue#3 修复ElementaryOS上证书不安全问题(serialNumber为1时证书会提示不安全)
                // ，避免serialNumber冲突，采用时间戳+4位随机数生成
                BigInteger.valueOf(System.currentTimeMillis() + (long) (Math.random() * 10000) +
                                           1000),
                caNotBefore,
                caNotAfter,
                new X500Name(subject),
                serverPubKey
        );
        //SAN扩展证书支持的域名，否则浏览器提示证书不安全
        GeneralName[] generalNames = new GeneralName[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            generalNames[i] = new GeneralName(GeneralName.dNSName, hosts[i]);
        }
        GeneralNames subjectAltName = new GeneralNames(generalNames);
        jv3Builder.addExtension(Extension.subjectAlternativeName, false, subjectAltName);
        //SHA256 用SHA1浏览器可能会提示证书不安全
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build
                (caPriKey);
        return new JcaX509CertificateConverter().getCertificate(jv3Builder.build(signer));
    }

    public static final boolean isCertInstalled(X509Certificate certificate) {
        if (certificate == null) {
            return false;
        }
        KeyStore ks;
        try {
            ks = KeyStore.getInstance("AndroidCAStore");
            ks.load(null, null);
            Enumeration e = ks.aliases();

            while (e.hasMoreElements()) {
                String alias = (String) e.nextElement();
                java.security.cert.Certificate installedCert = ks.getCertificate(alias);
                if (installedCert != null && (installedCert instanceof X509Certificate)) {
                    Log.i(TAG, alias);
                    Log.i(TAG, ((X509Certificate) installedCert).getSubjectDN().getName());
                    Log.i(TAG, ((X509Certificate) installedCert).getSubjectX500Principal().getName());
                    byte[] installed = ((X509Certificate) installedCert).getSignature();
                    if (Arrays.equals(installed, certificate.getSignature())) {
                        Log.i(TAG, "signature match");
                        return true;
                    }
                }
            }
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "no matching signagure");
        return false;
    }

}
