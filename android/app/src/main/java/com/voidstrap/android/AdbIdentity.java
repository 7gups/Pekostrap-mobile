package com.voidstrap.android;

import android.content.Context;
import android.util.Base64;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

/** The ADB identity for wireless debugging: an RSA key and a certificate, kept in SecureStore. */
final class AdbIdentity {
    static final String KEY_VAULT = "adb_key";
    static final String CERT_VAULT = "adb_cert";

    final PrivateKey privateKey;
    final Certificate certificate;

    private AdbIdentity(PrivateKey privateKey, Certificate certificate) {
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    static synchronized AdbIdentity loadOrCreate(Context c) throws Exception {
        String keyText = SecureStore.get(c, KEY_VAULT);
        String certText = SecureStore.get(c, CERT_VAULT);
        if (keyText != null && certText != null) {
            PrivateKey key = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(keyText, Base64.NO_WRAP)));
            Certificate cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new java.io.ByteArrayInputStream(Base64.decode(certText, Base64.NO_WRAP)));
            return new AdbIdentity(key, cert);
        }
        AdbIdentity created = create();
        SecureStore.put(c, KEY_VAULT, Base64.encodeToString(created.privateKey.getEncoded(), Base64.NO_WRAP));
        SecureStore.put(c, CERT_VAULT, Base64.encodeToString(created.certificate.getEncoded(), Base64.NO_WRAP));
        return created;
    }

    /** Creates a 2048-bit RSA key and a self-signed certificate, as the libadb sample does. */
    private static AdbIdentity create() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
        KeyPair pair = generator.generateKeyPair();
        PublicKey publicKey = pair.getPublic();
        PrivateKey privateKey = pair.getPrivate();

        String subject = "CN=Pekostrap";
        String algorithmName = "SHA512withRSA";
        long expiry = System.currentTimeMillis() + 10L * 365 * 86400000L;
        CertificateExtensions extensions = new CertificateExtensions();
        extensions.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(
                new KeyIdentifier(publicKey).getIdentifier()));
        X500Name name = new X500Name(subject);
        Date notBefore = new Date();
        Date notAfter = new Date(expiry);
        extensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));
        CertificateValidity validity = new CertificateValidity(notBefore, notAfter);
        X509CertInfo info = new X509CertInfo();
        info.set("version", new CertificateVersion(2));
        info.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
        info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithmName)));
        info.set("subject", new CertificateSubjectName(name));
        info.set("key", new CertificateX509Key(publicKey));
        info.set("validity", validity);
        info.set("issuer", new CertificateIssuerName(name));
        info.set("extensions", extensions);
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(privateKey, algorithmName);
        return new AdbIdentity(privateKey, cert);
    }
}
