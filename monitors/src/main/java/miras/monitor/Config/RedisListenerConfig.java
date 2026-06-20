package miras.monitor.Config;

import miras.monitor.Utils.RedisEventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisListenerConfig {



    @Bean
    public RedisMessageListenerContainer container(RedisEventListener listener, org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);

        // Escuchamos todos los eventos que ocurren en la DB 1 (donde están dvr:*)
        container.addMessageListener(listener, new PatternTopic("__keyevent@1__:*"));
        
        return container;
    }
}
