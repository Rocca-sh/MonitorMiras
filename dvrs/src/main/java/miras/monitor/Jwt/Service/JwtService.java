package miras.monitor.Jwt.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys; 

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String prhase;

    private Key getKey(){
        byte[] keyBytes = prhase.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(String id, String orgId, String rol) {
        return Jwts.builder()
            .header()
                .type("JWT") 
            .and()
            .subject(id) 
            .claim("orgId", orgId)
            .claim("rol", rol)
            .issuedAt(new Date())
            // Token infinito para pruebas
            // .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60)) // 1 hora
            .signWith(getKey()) 
            .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith((SecretKey) getKey())
            .build()
            .parseSignedClaims(token)
            .getPayload(); 
    }

    public Boolean authToken(String token) {
        try {
            extractClaims(token); 
            return true;  
        } catch (Exception e) {
            return false; 
        }
    }

    public String getIdFromToken(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            return "Token invalido";  
        }  
    }

    public String getRoleFromToken(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.get("rol", String.class);
        } catch (Exception e) {
            return "Token invalido";
        }
    }

    public String getOrgIdFromToken(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.get("orgId", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String getOrgIdFromAuthHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return getOrgIdFromToken(token);
        }
        return null;
    }
}
