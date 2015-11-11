package com.palantir.stash.stashbot.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import org.slf4j.Logger;

import com.palantir.stash.stashbot.logger.PluginLoggerFactory;

/**
 * This class implements some helper functions since com.atlassian.bitbucket.ssh.utils.KeyUtils is not available to
 * plugins.
 * 
 * @author cmyers
 */
public class KeyUtils {

    private final Logger log;

    public KeyUtils(PluginLoggerFactory plf) {
        this.log = plf.getLoggerForThis(this);
    }

    public PublicKey getPublicKey(String publicKeyText) {
        try {
            if (!publicKeyText.startsWith("ssh-rsa ")) {
                throw new IllegalArgumentException("only RSA keys are supported");
            }
            String base64 = publicKeyText.split(" ", 3)[1];
            byte[] decoded = Base64.getDecoder().decode(base64);
            // http://blog.oddbit.com/2011/05/08/converting-openssh-public-keys/
            // the format is:
            // length, keytype
            // length, exponent
            // length, modulus
            // Where all lengths are 4-byte big-endian integers
            // 
            int position = 0;

            // first read type

            // http://stackoverflow.com/questions/5616052/how-can-i-convert-a-4-byte-array-to-an-integer
            int typeLength = ByteBuffer.wrap(decoded, position, 4).getInt();
            position += 4;
            String type = bytesToStringAscii(Arrays.copyOfRange(decoded, position, position + typeLength));
            position += typeLength;

            // next read the exponent

            int exponentLength = ByteBuffer.wrap(decoded, position, 4).getInt();
            position += 4;
            //int exponent = ByteBuffer.wrap(decoded, position, 4).getInt();
            BigInteger exponent = new BigInteger(Arrays.copyOfRange(decoded, position, position + exponentLength));
            position += exponentLength;

            // next read the modulus
            int modulusLength = ByteBuffer.wrap(decoded, position, 4).getInt();
            position += 4;
            BigInteger modulus = new BigInteger(Arrays.copyOfRange(decoded, position, position + modulusLength));

            KeyFactory kf = KeyFactory.getInstance("RSA");
            // need modulus and private exponent
            RSAPublicKeySpec rpks = new RSAPublicKeySpec(modulus, exponent);
            return kf.generatePublic(rpks);
        } catch (InvalidKeySpecException e) {
            log.error("Unable to parse public key", e);
            return null;
        } catch (NoSuchAlgorithmException e) {
            log.error("Unable to parse public key", e);
            return null;
        } catch (Exception e) {
            log.error("Unable to parse public key", e);
            return null;
        }
    }

    /*
    public PrivateKey getPrivateKey(String privateKeyText) {
        try {
            // chop off leading ASCII armor
            String base64 = privateKeyText.substring(privateKeyText.indexOf("MII"));
            if (base64.contains("-")) {
                // chop off ending ASCII armor
                base64 = base64.substring(0, base64.indexOf("-"));
            }

            byte[] decoded = Base64.decode(base64);
            X509EncodedKeySpec privateKeySpec = new RSAP(privateKeyText.getBytes());
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey pk = kf.generatePrivate(privateKeySpec);
            return pk;
        } catch (InvalidKeySpecException e) {
            log.error("Unable to parse public key", e);
            return null;
        } catch (NoSuchAlgorithmException e) {
            log.error("Unable to parse public key", e);
            return null;
        } catch (Exception e) {
            log.error("Unable to parse public key", e);
            return null;

        }
    }
    */

    // http://www.javacodegeeks.com/2010/11/java-best-practices-char-to-byte-and.html
    public static String bytesToStringUTFCustom(byte[] bytes) {
        char[] buffer = new char[bytes.length >> 1];
        for (int i = 0; i < buffer.length; i++) {
            int bpos = i << 1;
            char c = (char) (((bytes[bpos] & 0x00FF) << 8) + (bytes[bpos + 1] & 0x00FF));
            buffer[i] = c;
        }
        return new String(buffer);
    }

    // http://www.javacodegeeks.com/2010/11/java-best-practices-char-to-byte-and.html
    public static String bytesToStringAscii(byte[] bytes) {
        char[] buffer = new char[bytes.length];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (char) bytes[i];
        }
        return new String(buffer);
    }
}
