package cu.lenier.nextchat.util;

import android.util.Base64;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoHelper {
    private static final String KEY  = "0123456789abcdef";
    private static final String ALGO = "AES/CBC/PKCS5Padding";

    public static String encrypt(String plain) throws Exception {
        byte[] keyBytes = KEY.getBytes("UTF-8");
        SecretKeySpec ks = new SecretKeySpec(keyBytes, "AES");
        Cipher c = Cipher.getInstance(ALGO);
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        c.init(Cipher.ENCRYPT_MODE, ks, new IvParameterSpec(iv));
        byte[] enc = c.doFinal(plain.getBytes("UTF-8"));
        byte[] all = new byte[iv.length + enc.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(enc,0, all, iv.length, enc.length);
        return Base64.encodeToString(all, Base64.NO_WRAP);
    }

    public static String decrypt(String cipherText) throws Exception {
        byte[] all = Base64.decode(cipherText, Base64.NO_WRAP);
        byte[] iv  = new byte[16];
        System.arraycopy(all, 0, iv, 0, iv.length);
        byte[] enc = new byte[all.length - iv.length];
        System.arraycopy(all, iv.length, enc, 0, enc.length);
        SecretKeySpec ks = new SecretKeySpec(KEY.getBytes("UTF-8"), "AES");
        Cipher c = Cipher.getInstance(ALGO);
        c.init(Cipher.DECRYPT_MODE, ks, new IvParameterSpec(iv));
        byte[] dec = c.doFinal(enc);
        return new String(dec, "UTF-8");
    }
}
