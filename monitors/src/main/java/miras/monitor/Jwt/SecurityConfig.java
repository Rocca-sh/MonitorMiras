package miras.monitor.Jwt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Habilitamos CORS globalmente en Security
            .cors(cors -> cors.configurationSource(request -> {
                var config = new CorsConfiguration();
                config.setAllowedOrigins(List.of("*"));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("*"));
                return config;
            }))
            // Deshabilitamos CSRF porque usaremos JWT (Stateless)
            .csrf(AbstractHttpConfigurer::disable)
            
            // Reglas de autorizacion
            .authorizeHttpRequests(auth -> auth
                // Rutas publicas (Auth, Org y Webhooks)
                .requestMatchers("/auth/**", "/api/webhook/**", "/api/hook/**", "/org/**").permitAll()
                
                // Rutas protegidas que requieren roles especificos
                .requestMatchers("/api/dvr/view/**").hasAnyRole("MEMBER", "VIEWER", "OWNER", "ADMIN")
                .requestMatchers("/api/dvr/**").hasAnyRole("MEMBER", "OWNER", "ADMIN")
                .requestMatchers("/api/**").hasAnyRole("MEMBER", "VIEWER", "OWNER", "ADMIN")
                
                // Cualquier otra ruta debe estar autenticada
                .anyRequest().authenticated()
            )
            
            // Politica sin estado (Stateless) para que Spring no cree sesiones en memoria
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Inyectamos nuestro filtro personalizado ANTES del filtro por defecto de Spring
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
