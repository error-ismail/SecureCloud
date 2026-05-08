package crypto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSAUtil {
    public static PublicKey loadPublicKey(Path keyDir, String username) {
        try {
            Path publicPath = keyDir.resolve(username + "_public.key");
            if (!Files.exists(publicPath)) {
                return null;
            }
            byte[] pubBytes = Base64.getDecoder().decode(Files.readString(publicPath).trim());
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new X509EncodedKeySpec(pubBytes));
        } catch (Exception ex) {
            return null;
        }
    }

    public static KeyPair loadOrCreateKeyPair(Path keyDir, String username) {
        try {
            Path privatePath = keyDir.resolve(username + "_private.key");
            Path publicPath = keyDir.resolve(username + "_public.key");

            if (Files.exists(privatePath) && Files.exists(publicPath)) {
                byte[] privBytes = Base64.getDecoder().decode(Files.readString(privatePath).trim());
                byte[] pubBytes = Base64.getDecoder().decode(Files.readString(publicPath).trim());

                KeyFactory kf = KeyFactory.getInstance("RSA");
                PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
                PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
                return new KeyPair(publicKey, privateKey);
            }

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Files.writeString(privatePath, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            Files.writeString(publicPath, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            return pair;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load RSA keys", ex);
        }
    }

    public static String signHash(byte[] hash, PrivateKey privateKey) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(hash);
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign", ex);
        }
    }

    public static boolean verifyHash(byte[] hash, String signatureBase64, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(hash);
            return sig.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception ex) {
            return false;
        }
    }
}
