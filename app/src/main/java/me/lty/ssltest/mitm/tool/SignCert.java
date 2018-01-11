package me.lty.ssltest.mitm.tool;/*
Copyright 2007

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    * Neither the name of Stanford University nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

import iaik.asn1.structures.*;
import iaik.x509.X509Certificate;
import me.lty.ssltest.mitm.MITMProxyServer;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;

/**
 * Utility methods for creating a new signed certificate.
 *
 * @author Srinivas Inguva
 * @author Liz Stinson
 * @author Priyank Patel
 */

public class SignCert {
    private static KeyStore load(String ksFile, String ksPass) {
        KeyStore tmp = null;
        try {
            tmp = KeyStore.getInstance("jks");
            tmp.load(new FileInputStream(ksFile), ksPass.toCharArray());
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return null;
        } catch (KeyStoreException kse) {
            System.err.println("Error while parsing keystore");
            kse.printStackTrace();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return tmp;
    }

    /**
     * Forge certificate which is identical to the given base certificate, except is signed
     * by the "CA" certificate in caKS, and has the associated IssuerDN.
     * <p>
     * The new cert will be signed by a the CA whose public/private keys are contained
     * in the caKS KeyStore (under the alias caAlias).
     */

    public static X509Certificate forgeCert(PrivateKey pk, java.security.cert.X509Certificate caCert,
                                            String commonName, iaik.x509.X509Certificate baseCert)
            throws Exception {
        Security.addProvider(new iaik.security.provider.IAIK());

        if (pk == null) {
            System.out.println("no private key!");
        } else {
            if (MITMProxyServer.debugFlag)
                System.out.println("pk format=" + pk.getFormat());
        }
        //Use IAIK's cert-factory, so we can easily use the X509CertificateGenerator!
        Principal issuer = caCert.getSubjectDN();

        AlgorithmID alg = AlgorithmID.sha256WithRSAEncryption;

        PublicKey subjectPubKey = caCert.getPublicKey();

        X509Certificate x509 = X509CertificateGenerator.generateCertificate(subjectPubKey, issuer, commonName, pk, alg, baseCert);

        if (MITMProxyServer.debugFlag) {
            System.out.println("Newly forged cert: ");
            System.out.println(x509.toString(true));
        }

        return x509;
    }

    /* Self test */
    public static void main(String[] args) throws Exception {
        //String caKeystore = args[0];
        //String caKSPass = args[1];
        //String caAlias = args[2];
        //String commonName = args[3];
        //
        //KeyStore caKS = load(caKeystore, caKSPass);
        //PrivateKey pk = (PrivateKey) caKS.getKey(caAlias, caKSPass.toCharArray());
        //
        //X509Certificate newCert = forgeCert(caKS, caKSPass.toCharArray(), caAlias, commonName, null);
        //
        //KeyStore newKS = KeyStore.getInstance("jks");
        //newKS.load(null, null);
        //
        //newKS.setKeyEntry("myKey", pk, caKSPass.toCharArray(), new Certificate[]{newCert});
        //newKS.store(new FileOutputStream("newkeystore"), caKSPass.toCharArray());
    }

}
