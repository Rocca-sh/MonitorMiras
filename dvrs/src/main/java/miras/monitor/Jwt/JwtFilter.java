package miras.monitor.Jwt;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import miras.monitor.Jwt.Service.JwtService;
import miras.monitor.User.Model.UserPrincipal;

@Component
public class JwtFilter extends OncePerRequestFilter{

    @Autowired 
    private JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,  FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userId;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        try {
            userId = jwtService.getIdFromToken(jwt);
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String rol = jwtService.getRoleFromToken(jwt);
                String orgId = jwtService.getOrgIdFromToken(jwt);
                
                String authorityName = rol.startsWith("ROLE_") ? rol : "ROLE_" + rol;
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                    new SimpleGrantedAuthority(authorityName)
                );
                
                UserPrincipal principal = new UserPrincipal(userId, orgId, rol);
                
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            System.out.println("Error validando el token: " + e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}
