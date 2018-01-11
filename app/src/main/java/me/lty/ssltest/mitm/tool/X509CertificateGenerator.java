package me.lty.ssltest.mitm.tool;/*
Copyright 2007

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    * Neither the name of Stanford University nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

//
//  X509CertificateGenerator.java
//  Originally developed for use in CS255 class projects
//
//  Modified by : Priyank Patel <pkpatel@cs.stanford.edu>
//  Modified by : Liz Stinson
//  Modified by : Srinivas Inguva
//

import iaik.asn1.*;
import iaik.asn1.structures.*;
import iaik.x509.*;

import java.security.*;
import java.security.cert.CertificateException;

/**
 * A utility class that provides a method for generating a signed
 * X.509 certificate from a given base certificate.  All fields of the
 * base certificate are preserved, except for the IssuerDN, the
 * public key, and the signature.
 */
public class X509CertificateGenerator {

    public static X509Certificate generateCertificate(
            PublicKey subjectPublicKey,
            Principal issuerName,
            String commonName,
            PrivateKey issuerPrivateKey,
            AlgorithmID algorithm,
            X509Certificate baseCert
    ) {
        X509Certificate cert = null;

        try {
            cert = new X509Certificate(baseCert.getEncoded());
            cert.setPublicKey(subjectPublicKey);
            cert.setIssuerDN(issuerName);

            Name n = (Name) baseCert.getSubjectDN();
            n.addRDN(ObjectID.commonName,commonName);
            cert.setSubjectDN(n);
            cert.sign(algorithm, issuerPrivateKey);
        } catch (InvalidKeyException e) {
            System.err.println("X509 Certificate Generation Error: Invalid Key");
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("X509 Certificate Generation Error: No Such Algorithm");
            e.printStackTrace();
        } catch (CertificateException e) {
            System.err.println("X509 Certificate Generation Error: Certificate Exception");
            e.printStackTrace();
        }
        return cert;
    }


}
