package crypto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESUtil {
    private static final int KEY_SIZE = 128;
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE = 128;

    public static SecretKey loadOrCreateKey(Path keyFile) {
        try {
            if (Files.exists(keyFile)) {
                String base64 = Files.readString(keyFile).trim();
                byte[] keyBytes = Base64.getDecoder().decode(base64);
                return new SecretKeySpec(keyBytes, "AES");
            }
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(KEY_SIZE);
            SecretKey key = generator.generateKey();
            String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
            Files.writeString(keyFile, base64);
            return key;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load AES key", ex);
        }
    }

    public static void encryptFile(Path input, Path output, SecretKey key) {
        try {
            byte[] iv = new byte[IV_SIZE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, iv));

            try (InputStream is = Files.newInputStream(input);
                 OutputStream os = Files.newOutputStream(output)) {
                os.write(iv);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    byte[] enc = cipher.update(buffer, 0, read);
                    if (enc != null) {
                        os.write(enc);
                    }
                }
                byte[] finalBytes = cipher.doFinal();
                if (finalBytes != null) {
                    os.write(finalBytes);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt file", ex);
        }
    }

    public static void decryptFile(Path input, Path output, SecretKey key) {
        try (InputStream is = Files.newInputStream(input)) {
            byte[] iv = is.readNBytes(IV_SIZE);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, iv));

            try (OutputStream os = Files.newOutputStream(output)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    byte[] dec = cipher.update(buffer, 0, read);
                    if (dec != null) {
                        os.write(dec);
                    }
                }
                byte[] finalBytes = cipher.doFinal();
                if (finalBytes != null) {
                    os.write(finalBytes);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read encrypted file", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt file", ex);
        }
    }
}
