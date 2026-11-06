package com.gba.merchantbao.auth.infrastructure;

import com.gba.merchantbao.auth.config.AuthJwtKeyMaterial;
import com.gba.merchantbao.auth.domain.AccountRole;
import com.gba.merchantbao.auth.domain.AuthError;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** RFC5869 HKDF-SHA256 followed by AES-256-GCM. Cache is never a plaintext credential store. */
@Component
public class RefreshCipher {
    private final AuthJwtKeyMaterial keys;
    private final SecureRandom random=new SecureRandom();
    public RefreshCipher(AuthJwtKeyMaterial keys) {this.keys=keys;}
    private byte[] key(AccountRole role) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new byte[32],"HmacSHA256"));
        byte[] prk=mac.doFinal((role==AccountRole.CONSUMER?keys.consumerKey():keys.merchantStaffKey()).getEncoded());
        try {
            mac.init(new SecretKeySpec(prk,"HmacSHA256"));
            return mac.doFinal("auth-refresh-retry-v1\u0001".getBytes(StandardCharsets.UTF_8));
        } finally {Arrays.fill(prk,(byte)0);}
    }
    public String encrypt(AccountRole role,String aad,String value) {
        byte[] derived=null;
        try {
            derived=key(role); byte[] nonce=new byte[12]; random.nextBytes(nonce);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(derived,"AES"),new GCMParameterSpec(128,nonce));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8)); byte[] encrypted=c.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed=new byte[nonce.length+encrypted.length]; System.arraycopy(nonce,0,packed,0,12); System.arraycopy(encrypted,0,packed,12,encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch(Exception e) {throw AuthError.dependency();}
        finally {if(derived!=null) Arrays.fill(derived,(byte)0);}
    }
    public String decrypt(AccountRole role,String aad,String value) {
        byte[] derived=null;
        try {
            derived=key(role); byte[] packed=Base64.getUrlDecoder().decode(value);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(derived,"AES"),new GCMParameterSpec(128,packed,0,12));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(packed,12,packed.length-12),StandardCharsets.UTF_8);
        } catch(Exception e) {throw AuthError.unauthorized();}
        finally {if(derived!=null) Arrays.fill(derived,(byte)0);}
    }
}
