package org.example.SystemManagementSvc.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 연결 설정
 * ELK 스택과의 통신을 위한 High Level REST Client 구성
 */
@Slf4j
@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;
    
    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;
    
    @Value("${elasticsearch.scheme:http}")
    private String scheme;

    /**
     * Elasticsearch High Level REST Client Bean 생성
     * - 비동기 및 동기 요청 모두 지원
     * - 자동 재시도 및 연결 풀링 포함
     * 
     * @return RestHighLevelClient 인스턴스
     */
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        RestClientBuilder builder = RestClient.builder(
            new HttpHost(elasticsearchHost, elasticsearchPort, scheme))
            .setRequestConfigCallback(requestConfigBuilder ->
                requestConfigBuilder
                    .setConnectTimeout(5000)  // 연결 타임아웃: 5초
                    .setSocketTimeout(60000)) // 소켓 타임아웃: 60초
            .setHttpClientConfigCallback(httpClientBuilder ->
                httpClientBuilder
                    .setMaxConnTotal(100)     // 최대 연결 수
                    .setMaxConnPerRoute(50)); // 경로별 최대 연결 수

        RestHighLevelClient client = new RestHighLevelClient(builder);
        
        log.info("Elasticsearch client configured - {}://{}:{}", scheme, elasticsearchHost, elasticsearchPort);
        
        return client;
    }
}