package me.lty.ssltest.mitm.tool;/*
Copyright 2007

Redistribution and use in source and binary forms, with or without modification, are permitted
provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of
    * conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, this list of
    * conditions and the following disclaimer in the documentation and/or other materials
    * provided with the distribution.
    * Neither the name of Stanford University nor the names of its contributors may be used to
    * endorse or promote products derived from this software without specific prior written
    * permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

import android.util.Log;

import iaik.asn1.structures.*;
import iaik.x509.X509Certificate;

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

    private static final String TAG = SignCert.class.getSimpleName();

    private static KeyStore load(String ksFile, String ksPass) {
        KeyStore tmp = null;
        try {
            tmp = KeyStore.getInstance(KeyStore.getDefaultType());
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

    public static X509Certificate forgeCert(PrivateKey pk, java.security.cert.X509Certificate
            caCert,
                                            String commonName, iaik.x509.X509Certificate baseCert)
            throws Exception {
        Security.addProvider(new iaik.security.provider.IAIK());

        if (pk == null) {
            Log.d(TAG, "no private key!");
        } else {
            Log.d(TAG,"pk format=" + pk.getFormat());
        }
        //Use IAIK's cert-factory, so we can easily use the X509CertificateGenerator!
        Principal issuer = caCert.getSubjectDN();

        AlgorithmID alg = AlgorithmID.sha256WithRSAEncryption;

        PublicKey subjectPubKey = caCert.getPublicKey();

        X509Certificate x509 = X509CertificateGenerator.generateCertificate(
                subjectPubKey,
                issuer,
                commonName,
                pk,
                alg,
                baseCert
        );

        Log.d(TAG,"Newly forged cert: ");
        Log.d(TAG,x509.toString(true));

        return x509;
    }

}
