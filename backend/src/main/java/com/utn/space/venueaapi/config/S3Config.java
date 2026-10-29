package com.utn.space.venueaapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    // Sin credenciales explícitas: el SDK usa el rol IAM de la instancia EC2 o las variables de entorno AWS_*
    //Esta canfiguración se usa para ir a buscar las imagenes al bucket S3 de AWS
    @Bean
    public S3Presigner s3Presigner(@Value("${aws.region}") String region) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Client s3Client(@Value("${aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
