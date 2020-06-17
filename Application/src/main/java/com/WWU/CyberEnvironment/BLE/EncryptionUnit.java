package com.WWU.CyberEnvironment.BLE;

import android.util.Base64;
import android.util.Log;

import java.io.StringWriter;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionUnit {
    /* The purpose of the EncryptionUnit is to encapsulate all security functionality
     * for the app. This object will handle the RSA key pair and specific information about
     * the decryption process, namely the private key, from the rest of the app. This class
     * will return a private key as a string that can be sent to the ESP32, as well as decrypt
     * and given message. This object is not intended to do an encryption because the only data
     * that is transmitted is sent from the ESP.
     */
    private String encryptionAlgorithm = "RSA";
    private KeyPairGenerator keyGen;
    private KeyPair keyPair;

    private SecretKeyFactory symetricKeyFactory;
    private SecretKey symmetricKey;

    private Cipher cipher;
    private Cipher dataCipher;

    private IvParameterSpec ivSpec;


    /* Constructor
     * The constructor will create the KeyPairGenerator, use it to create a public
     * and private key in a KeyPair object, and construct the cipher as well.
     */
    public EncryptionUnit(){
        //Constructs the KeyPairGenerator and the SecretKeyFactory
        try {
            keyGen = KeyPairGenerator.getInstance(encryptionAlgorithm);
            symetricKeyFactory = SecretKeyFactory.getInstance("AES/CBC/NoPadding");
        } catch (Exception NoSuchAlgorithmException) {
            Log.d("key gen", "Algorithm not found");
        }
        //Generates the Private/Public key pair
        keyPair = keyGen.generateKeyPair();
        //constructs the Cipher object.
        try {
            cipher = Cipher.getInstance(encryptionAlgorithm);
        } catch (Exception NoSuchAlgorithmException) {
            Log.d("cipher init", "Algorithm not found");
        }
        try {
            dataCipher = Cipher.getInstance("AES/CBC/NoPadding");
        } catch (Exception NoSuchAlgorithmException) {
            Log.d("cipher2 init", "Algorithm not found");
        }
        //sets the Cipher to decrypt by default
        try {
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        } catch(Exception InvalidKeyException){
            Log.d("cipther init", "Bad Private Key");
        }


    }

    public String getPubKeyS(){
        PublicKey pubKey = keyPair.getPublic();
//        byte[] keyBytes = Base64.encode(pubKey.getEncoded(), Base64.DEFAULT);
//        String pubKeyS = new String(keyBytes);

        String pubKeyS = pubKey.toString();
        return pubKeyS;
    }
    public byte[] getPubKey(){
        PublicKey pubKey = keyPair.getPublic();
        return pubKey.getEncoded();
    }

    public String getPrivKeyS(){
        PrivateKey privateKey = keyPair.getPrivate();
        return privateKey.toString();
    }
    public String getSymKeyS(){
        return symmetricKey.toString();
    }

    public byte[] getSymKey(){
        return symmetricKey.getEncoded();
    }

    /* This function creates a SecretKey object from the encrypted key sent by the board that
     * java functions can use to decrypt future received sensor data which will have been encrypted with
     * the symmetric key.
     * INPUT: keySource, The byte array received via readCharacteristic from the board containing the encrypted
     *  key, iv, the initialization vector sent by the board (unencrypted).
     * OUTPUT: No value is returned, but the global variable symmetricKey is set to the SecretKey
     *  generated from the value sent from the board.
     */
    public void createSymmetricKey(byte[] keySource, byte[] iv){
        byte[] decryptedSource = null;
        ivSpec = new IvParameterSpec(iv);
        try {
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        } catch(Exception InvalidKeyException){
            Log.d("cipther init", "Bad Private Key");
        }
        try {
            decryptedSource = cipher.doFinal(keySource);
        } catch(Exception IllegalBlockSizeException){
            Log.d("decryption", "bad block size");
        }
        if (decryptedSource != null) {
            byte[] keyCliped = Arrays.copyOfRange(decryptedSource, 223, 255);
            symmetricKey = new SecretKeySpec(keyCliped, "AES/CBC/NoPadding");
        }

        try {
            dataCipher.init(Cipher.DECRYPT_MODE, symmetricKey, ivSpec);
        } catch(Exception InvalidKeyException){
            Log.d("cipther init", "Bad Private Key");
        }
    }

    /* Decrypts the message sent from the esp
     * INPUT: The string that was sent from the ESP
     * OUTPUT: A string that is the decrypted data ONLY if decryption was successful, otherwise an error
     * message (success means no errors were thrown, this does not mean the decrypted message is correct)
     */
    public byte[] decrypt(byte[] message){
        byte[] data = new byte[0];
        //decrypts the message
        try {
            data = dataCipher.doFinal(message);
        } catch(Exception IllegalBlockSizeException){
            Log.d("decryption", "bad block size");
        }
        byte[] iv_view = dataCipher.getIV();
        //re-init dataCipher with iv_view
        return data;
    }


}