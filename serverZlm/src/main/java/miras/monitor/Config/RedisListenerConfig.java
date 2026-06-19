package miras.monitor.Config;

import miras.monitor.Utils.RedisWvpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisListenerConfig {

    // Creamos una conexión exclusiva que apunte a la Base de Datos 0 solo para escuchar a WVP
    @Bean
    public LettuceConnectionFactory wvpConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 6379);
        config.setDatabase(0); // Forzamos BD 0 (WVP)
        return new LettuceConnectionFactory(config);
    }

    // Exponemos un Template para poder hacer consultas manuales a la BD 0 de WVP
    @Bean("wvpRedisTemplate")
    public org.springframework.data.redis.core.StringRedisTemplate wvpRedisTemplate() {
        return new org.springframework.data.redis.core.StringRedisTemplate(wvpConnectionFactory());
    }

    @Bean
    public RedisMessageListenerContainer container(RedisWvpService listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        // Le inyectamos nuestra fábrica de conexiones especial que apunta a la 0
        container.setConnectionFactory(wvpConnectionFactory());

        // Escuchar todos los eventos de la base de datos 0
        container.addMessageListener(listener, new PatternTopic("__keyevent@0__:*"));
        
        return container;
    }
}
