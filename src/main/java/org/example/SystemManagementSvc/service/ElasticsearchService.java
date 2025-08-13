package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.Avg;
import org.elasticsearch.search.aggregations.metrics.Max;
import org.elasticsearch.search.aggregations.metrics.Sum;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.example.SystemManagementSvc.dto.analytics.ApiCallStatistics;
import org.example.SystemManagementSvc.dto.analytics.ErrorStatistics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 연동을 위한 핵심 서비스
 * ELK Stack에서 로그 데이터를 조회하고 집계하는 기능 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ElasticsearchService {

    private final RestHighLevelClient elasticsearchClient;
    
    @Value("${elasticsearch.indices.api-calls}")
    private String apiCallsIndex;
    
    @Value("${elasticsearch.indices.error-logs}")  
    private String errorLogsIndex;
    
    private static final DateTimeFormatter ES_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /**
     * 서비스별 에러 통계를 Elasticsearch에서 조회
     * 
     * @param startTime 조회 시작 시간
     * @param endTime   조회 종료 시간
     * @param limit     결과 개수 제한
     * @return 서비스별 에러 통계 리스트
     */
    public List<ErrorStatistics> getServiceErrorStatistics(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        try {
            SearchRequest searchRequest = new SearchRequest(errorLogsIndex);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            
            // 시간 범위 필터 설정
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.rangeQuery("@timestamp")
                    .gte(startTime.format(ES_DATE_FORMATTER))
                    .lte(endTime.format(ES_DATE_FORMATTER)))
                .must(QueryBuilders.termQuery("level", "ERROR")); // ERROR 레벨만 집계
            
            sourceBuilder.query(boolQuery);
            sourceBuilder.size(0); // 실제 문서는 반환하지 않고 집계만
            
            // 서비스별 집계 설정
            sourceBuilder.aggregation(
                AggregationBuilders.terms("services")
                    .field("service.name.keyword")
                    .size(limit)
                    .subAggregation(
                        AggregationBuilders.terms("error_types")
                            .field("error.type.keyword")
                            .size(1) // 가장 빈번한 에러 타입만
                    )
                    .subAggregation(
                        AggregationBuilders.max("last_error")
                            .field("@timestamp")
                    )
            );
            
            searchRequest.source(sourceBuilder);
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            
            return parseErrorStatistics(response, startTime, endTime);
            
        } catch (Exception e) {
            log.error("Failed to query error statistics from Elasticsearch", e);
            return createFallbackErrorStatistics(); // fallback to mock data
        }
    }

    /**
     * 외부 API 호출 통계를 Elasticsearch에서 조회
     * 
     * @param startTime 조회 시작 시간
     * @param endTime   조회 종료 시간  
     * @param limit     결과 개수 제한
     * @return API 호출 통계 리스트
     */
    public List<ApiCallStatistics> getApiCallStatistics(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        try {
            SearchRequest searchRequest = new SearchRequest(apiCallsIndex);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            
            // 시간 범위 필터
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.rangeQuery("@timestamp")
                    .gte(startTime.format(ES_DATE_FORMATTER))
                    .lte(endTime.format(ES_DATE_FORMATTER)));
                    
            sourceBuilder.query(boolQuery);
            sourceBuilder.size(0);
            
            // API별 집계 설정 - 호출량 기준 내림차순 정렬
            sourceBuilder.aggregation(
                AggregationBuilders.terms("apis")
                    .field("api.name.keyword")
                    .size(limit)
                    .order(org.elasticsearch.search.aggregations.BucketOrder.count(false))
                    // 성공 호출 수 계산
                    .subAggregation(
                        AggregationBuilders.filter("successful_calls",
                            QueryBuilders.rangeQuery("response.status").gte(200).lt(300))
                    )
                    // 실패 호출 수 계산  
                    .subAggregation(
                        AggregationBuilders.filter("failed_calls",
                            QueryBuilders.boolQuery()
                                .should(QueryBuilders.rangeQuery("response.status").gte(400))
                                .should(QueryBuilders.existsQuery("error.message")))
                    )
                    // 평균 응답시간
                    .subAggregation(
                        AggregationBuilders.avg("avg_response_time")
                            .field("response.time")
                    )
                    // 최대 응답시간
                    .subAggregation(
                        AggregationBuilders.max("max_response_time")
                            .field("response.time")
                    )
                    // 마지막 호출 시간
                    .subAggregation(
                        AggregationBuilders.max("last_call")
                            .field("@timestamp")
                    )
                    // API 제공자 정보
                    .subAggregation(
                        AggregationBuilders.terms("providers")
                            .field("api.provider.keyword")
                            .size(1)
                    )
            );
            
            searchRequest.source(sourceBuilder);
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            
            return parseApiCallStatistics(response);
            
        } catch (Exception e) {
            log.error("Failed to query API call statistics from Elasticsearch", e);
            return createFallbackApiCallStatistics(); // fallback to mock data
        }
    }

    /**
     * Elasticsearch 응답에서 에러 통계 파싱
     */
    private List<ErrorStatistics> parseErrorStatistics(SearchResponse response, LocalDateTime startTime, LocalDateTime endTime) {
        List<ErrorStatistics> results = new ArrayList<>();
        
        Terms servicesAggregation = response.getAggregations().get("services");
        int rank = 1;
        
        for (Terms.Bucket serviceBucket : servicesAggregation.getBuckets()) {
            String serviceName = serviceBucket.getKeyAsString();
            long totalErrors = serviceBucket.getDocCount();
            
            // 가장 빈번한 에러 타입 추출
            Terms errorTypesAgg = serviceBucket.getAggregations().get("error_types");
            String mostFrequentErrorType = errorTypesAgg.getBuckets().isEmpty() ? 
                "UNKNOWN" : errorTypesAgg.getBuckets().get(0).getKeyAsString();
            
            // 마지막 에러 발생 시간
            Max lastErrorAgg = serviceBucket.getAggregations().get("last_error");
            String lastErrorTime = lastErrorAgg.getValueAsString();
            
            // 에러율 계산 (간소화: 전체 로그 대비 에러 비율로 추정)
            double errorRate = calculateErrorRate(totalErrors, startTime, endTime);
            
            results.add(ErrorStatistics.builder()
                .serviceName(serviceName)
                .totalErrorCount(totalErrors)
                .errorRate(errorRate)
                .mostFrequentErrorType(mostFrequentErrorType)
                .lastErrorTime(lastErrorTime)
                .rank(rank++)
                .build());
        }
        
        return results;
    }

    /**
     * Elasticsearch 응답에서 API 호출 통계 파싱
     */
    private List<ApiCallStatistics> parseApiCallStatistics(SearchResponse response) {
        List<ApiCallStatistics> results = new ArrayList<>();
        
        Terms apisAggregation = response.getAggregations().get("apis");
        int rank = 1;
        
        for (Terms.Bucket apiBucket : apisAggregation.getBuckets()) {
            String apiName = apiBucket.getKeyAsString();
            long totalCalls = apiBucket.getDocCount();
            
            // 성공 호출 수
            Filter successCallsFilter = apiBucket.getAggregations().get("successful_calls");
            long successCalls = successCallsFilter.getDocCount();
            
            // 실패 호출 수
            Filter failedCallsFilter = apiBucket.getAggregations().get("failed_calls");
            long failureCalls = failedCallsFilter.getDocCount();
            
            // 성공률 계산
            double successRate = totalCalls > 0 ? (double) successCalls / totalCalls * 100.0 : 0.0;
            
            // 평균 응답시간
            Avg avgResponseTimeAgg = apiBucket.getAggregations().get("avg_response_time");
            double avgResponseTime = avgResponseTimeAgg.getValue();
            
            // 최대 응답시간
            Max maxResponseTimeAgg = apiBucket.getAggregations().get("max_response_time");
            long maxResponseTime = (long) maxResponseTimeAgg.getValue();
            
            // 마지막 호출 시간
            Max lastCallAgg = apiBucket.getAggregations().get("last_call");
            String lastCallTime = lastCallAgg.getValueAsString();
            
            // API 제공자
            Terms providersAgg = apiBucket.getAggregations().get("providers");
            String apiProvider = providersAgg.getBuckets().isEmpty() ? 
                "Unknown" : providersAgg.getBuckets().get(0).getKeyAsString();
            
            results.add(ApiCallStatistics.builder()
                .apiName(apiName)
                .apiProvider(apiProvider)
                .totalCallCount(totalCalls)
                .successCallCount(successCalls)
                .failureCallCount(failureCalls)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .averageResponseTime(Math.round(avgResponseTime * 100.0) / 100.0)
                .maxResponseTime(maxResponseTime)
                .lastCallTime(lastCallTime)
                .rank(rank++)
                .build());
        }
        
        return results;
    }

    /**
     * 에러율 계산 (단순 추정)
     */
    private double calculateErrorRate(long errorCount, LocalDateTime startTime, LocalDateTime endTime) {
        // 실제로는 전체 요청 수 대비 에러 비율을 계산해야 하지만,
        // 여기서는 시간당 에러율로 간소화
        long hours = java.time.Duration.between(startTime, endTime).toHours();
        if (hours == 0) hours = 1;
        
        double errorRatePerHour = (double) errorCount / hours;
        return Math.min(errorRatePerHour / 10.0, 100.0); // 최대 100%로 제한
    }

    /**
     * Elasticsearch 연결 실패 시 fallback 에러 통계
     */
    private List<ErrorStatistics> createFallbackErrorStatistics() {
        log.warn("Using fallback error statistics due to Elasticsearch connection failure");
        
        return List.of(
            ErrorStatistics.builder()
                .serviceName("auth-service")
                .totalErrorCount(150L)
                .errorRate(5.2)
                .mostFrequentErrorType("VALIDATION_ERROR")
                .lastErrorTime(LocalDateTime.now().minusMinutes(10).format(ES_DATE_FORMATTER))
                .rank(1)
                .build(),
            ErrorStatistics.builder()
                .serviceName("payment-service")
                .totalErrorCount(89L)
                .errorRate(3.1)
                .mostFrequentErrorType("DATABASE_ERROR")
                .lastErrorTime(LocalDateTime.now().minusMinutes(25).format(ES_DATE_FORMATTER))
                .rank(2)
                .build()
        );
    }

    /**
     * Elasticsearch 연결 실패 시 fallback API 호출 통계
     */
    private List<ApiCallStatistics> createFallbackApiCallStatistics() {
        log.warn("Using fallback API call statistics due to Elasticsearch connection failure");
        
        return List.of(
            ApiCallStatistics.builder()
                .apiName("weather-api")
                .apiProvider("기상청")
                .totalCallCount(1250L)
                .successCallCount(1200L)
                .failureCallCount(50L)
                .successRate(96.0)
                .averageResponseTime(250.5)
                .maxResponseTime(1500L)
                .lastCallTime(LocalDateTime.now().minusMinutes(5).format(ES_DATE_FORMATTER))
                .rank(1)
                .build(),
            ApiCallStatistics.builder()
                .apiName("traffic-api")
                .apiProvider("국토교통부")
                .totalCallCount(890L)
                .successCallCount(850L)
                .failureCallCount(40L)
                .successRate(95.5)
                .averageResponseTime(180.3)
                .maxResponseTime(2100L)
                .lastCallTime(LocalDateTime.now().minusMinutes(8).format(ES_DATE_FORMATTER))
                .rank(2)
                .build()
        );
    }
}