package com.example.hotel.booking.hotel;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Построитель RestClient с тайм-аутами и балансировкой через Eureka.
 */
@Configuration
@EnableConfigurationProperties(HotelProperties.class)
public class HotelConfig {

    /**
     * @param props Настройки тайм-аутов
     * @return Балансируемый построитель клиента
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder hotelRestClientBuilder(final HotelProperties props) {
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return RestClient.builder().requestFactory(factory);
    }
}