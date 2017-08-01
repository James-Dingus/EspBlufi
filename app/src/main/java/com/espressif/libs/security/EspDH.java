/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.espressif.libs.security;

import java.math.BigInteger;
import java.security.AlgorithmParameterGenerator;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;

public class EspDH {
    private static final String FIXP = "cf5cf5c38419a724957ff5dd323b9c45c3cdd261eb740f69aa94b8bb1a5c9640" +
            "9153bd76b24222d03274e4725a5406092e9e82e9135c643cae98132b0d95f7d6" +
            "5347c68afc1e677da90e51bbab5f5cf429c291b4ba39c6b2dc5e8c7231e46aa7" +
            "728e87664532cdf547be20c9a3fa8342be6e34371a27c06f7dc0edddd2f86373";
    private static final String FIXG = "2";

    private final int mLength;
    private BigInteger mP;
    private BigInteger mG;

    private DHPrivateKey mPrivateKey;
    private DHPublicKey mPublicKey;

    private byte[] mSecretKey;

    public EspDH(int length) {
        this(new BigInteger(FIXP, 16), new BigInteger(FIXG), length);
    }

    public EspDH(BigInteger p, BigInteger g, int length) {
        mP = p;
        mG = g;
        mLength = length;
        generateKeys();
    }

    private BigInteger[] generatePG() {
        AlgorithmParameterGenerator paramGen = null;
        try {
            paramGen = AlgorithmParameterGenerator.getInstance("DH");
            paramGen.init(mLength, new SecureRandom());
            AlgorithmParameters params = paramGen.generateParameters();
            DHParameterSpec dhSpec = params.getParameterSpec(DHParameterSpec.class);
            BigInteger pv = dhSpec.getP();
            BigInteger gv = dhSpec.getG();

            return new BigInteger[]{pv, gv};
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
            e.printStackTrace();
        }

        return null;
    }

    private boolean generateKeys() {
        try {
            // Use the values to generate a key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DH");
            DHParameterSpec dhSpec = new DHParameterSpec(mP, mG, mLength);
            keyGen.initialize(dhSpec);
            KeyPair keypair = keyGen.generateKeyPair();

            // Get the generated public and private keys
            mPrivateKey = (DHPrivateKey) keypair.getPrivate();
            mPublicKey = (DHPublicKey) keypair.getPublic();

            return true;
        } catch (NoSuchAlgorithmException
                | InvalidAlgorithmParameterException
                | ClassCastException e) {
            e.printStackTrace();

            return false;
        }
    }

    public BigInteger getP() {
        return mP;
    }

    public BigInteger getG() {
        return mG;
    }

    public DHPrivateKey getPriveteKey() {
        return mPrivateKey;
    }

    public DHPublicKey getPublicKey() {
        return mPublicKey;
    }

    public byte[] getSecretKey() {
        return mSecretKey;
    }

    public void generateSecretKey(BigInteger y) throws InvalidKeySpecException {
        try {
            DHPublicKeySpec ks = new DHPublicKeySpec(y, mP, mG);
            KeyFactory keyFact = KeyFactory.getInstance("DH");
            PublicKey publicKey = keyFact.generatePublic(ks);

            // Prepare to generate the secret key with the private key and public key of the other party
            KeyAgreement ka = KeyAgreement.getInstance("DH");
            ka.init(mPrivateKey);
            ka.doPhase(publicKey, true);

            // Generate the secret key
            mSecretKey = ka.generateSecret();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }
}
