package org.example.SystemManagementSvc.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.dto.analytics.ApiCallStatistics;
import org.example.SystemManagementSvc.dto.analytics.ErrorStatistics;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prometheus 메트릭 수집 및 관리 서비스
 * 시스템 상태, API 호출, 에러 등의 메트릭을 Prometheus에 노출
 */
@Slf4j
@Service
public class PrometheusMetricsService {

    private final MeterRegistry meterRegistry;
    private final ApiCallAnalyticsService apiCallAnalyticsService;
    private final ErrorAnalyticsService errorAnalyticsService;
    
    // 메트릭 저장용 맵
    private final Map<String, AtomicLong> serviceErrorCounts = new HashMap<>();
    private final Map<String, AtomicLong> apiCallCounts = new HashMap<>();
    private final Map<String, AtomicLong> apiSuccessCounts = new HashMap<>();
    
    // 카운터 메트릭들
    private final Counter alertsProcessed;
    private final Counter emailsSent;
    private final Counter healthChecksTotal;
    private final Counter healthChecksFailed;
    
    // 게이지 메트릭들
    private final Gauge systemHealthStatus;
    private final AtomicLong systemHealthValue = new AtomicLong(1); // 1=healthy, 0=unhealthy
    
    // 타이머 메트릭들
    private final Timer alertProcessingTime;
    private final Timer elasticsearchQueryTime;

    public PrometheusMetricsService(MeterRegistry meterRegistry, 
                                   ApiCallAnalyticsService apiCallAnalyticsService,
                                   ErrorAnalyticsService errorAnalyticsService) {
        this.meterRegistry = meterRegistry;
        this.apiCallAnalyticsService = apiCallAnalyticsService;
        this.errorAnalyticsService = errorAnalyticsService;
        
        // 카운터 메트릭 초기화
        this.alertsProcessed = Counter.builder("apibridge_alerts_processed_total")
            .description("Total number of alerts processed")
            .register(meterRegistry);
            
        this.emailsSent = Counter.builder("apibridge_emails_sent_total")
            .description("Total number of notification emails sent")
            .register(meterRegistry);
            
        this.healthChecksTotal = Counter.builder("apibridge_health_checks_total")
            .description("Total number of external API health checks performed")
            .register(meterRegistry);
            
        this.healthChecksFailed = Counter.builder("apibridge_health_checks_failed_total")
            .description("Total number of failed external API health checks")
            .register(meterRegistry);
        
        // 게이지 메트릭 초기화
        this.systemHealthStatus = Gauge.builder("apibridge_system_health_status", systemHealthValue, val -> val.get())
            .description("Overall system health status (1=healthy, 0=unhealthy)")
            .register(meterRegistry);
        
        // 타이머 메트릭 초기화
        this.alertProcessingTime = Timer.builder("apibridge_alert_processing_duration_seconds")
            .description("Time taken to process alerts")
            .register(meterRegistry);
            
        this.elasticsearchQueryTime = Timer.builder("apibridge_elasticsearch_query_duration_seconds")
            .description("Time taken to execute Elasticsearch queries")
            .register(meterRegistry);
    }

    /**
     * 알림 처리 메트릭 업데이트
     */
    public void recordAlertProcessed(String alertType, String severity, boolean success, long processingTimeMs) {
        // 총 처리된 알림 수 증가
        Counter.builder("apibridge_alerts_processed_total")
            .description("Total number of alerts processed")
            .tag("alert_type", alertType)
            .tag("severity", severity)
            .tag("status", success ? "success" : "failed")
            .register(meterRegistry)
            .increment();
        
        // 처리 시간 기록
        alertProcessingTime.record(processingTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        log.debug("Alert processing metric recorded: type={}, severity={}, success={}, time={}ms", 
                 alertType, severity, success, processingTimeMs);
    }

    /**
     * 이메일 발송 메트릭 업데이트
     */
    public void recordEmailSent(String recipient, boolean success, String alertType) {
        Counter.builder("apibridge_emails_sent_total")
            .description("Total number of notification emails sent")
            .tag("status", success ? "success" : "failed")
            .tag("alert_type", alertType)
            .register(meterRegistry)
            .increment();
        
        log.debug("Email sending metric recorded: recipient={}, success={}, type={}", recipient, success, alertType);
    }

    /**
     * 외부 API 헬스체크 메트릭 업데이트
     */
    public void recordHealthCheck(String apiName, String provider, boolean success, long responseTimeMs) {
        // 총 헬스체크 수 증가
        Counter.builder("apibridge_health_checks_total")
            .description("Total number of external API health checks performed")
            .tag("api_name", apiName)
            .tag("provider", provider)
            .register(meterRegistry)
            .increment();
        
        // 실패한 헬스체크 수 기록
        if (!success) {
            Counter.builder("apibridge_health_checks_failed_total")
                .description("Total number of failed external API health checks")
                .tag("api_name", apiName)
                .tag("provider", provider)
                .register(meterRegistry)
                .increment();
        }
        
        // 헬스체크 응답시간 기록
        Timer.builder("apibridge_health_check_duration_seconds")
            .description("Health check response time")
            .tag("api_name", apiName)
            .tag("provider", provider)
            .tag("status", success ? "success" : "failed")
            .register(meterRegistry)
            .record(responseTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        log.debug("Health check metric recorded: api={}, provider={}, success={}, time={}ms", 
                 apiName, provider, success, responseTimeMs);
    }

    /**
     * Elasticsearch 쿼리 시간 메트릭 업데이트
     */
    public void recordElasticsearchQuery(String queryType, long executionTimeMs, boolean success) {
        elasticsearchQueryTime.record(executionTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        // 쿼리 성공/실패 카운터
        Counter.builder("apibridge_elasticsearch_queries_total")
            .description("Total number of Elasticsearch queries executed")
            .tag("query_type", queryType)
            .tag("status", success ? "success" : "failed")
            .register(meterRegistry)
            .increment();
        
        log.debug("Elasticsearch query metric recorded: type={}, time={}ms, success={}", 
                 queryType, executionTimeMs, success);
    }

    /**
     * 서비스별 에러 통계를 Prometheus 게이지로 노출
     */
    public void updateErrorMetrics() {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(1); // 최근 1시간
            
            List<ErrorStatistics> errorStats = errorAnalyticsService.getServiceErrorRanking(startTime, endTime, 20);
            
            for (ErrorStatistics stat : errorStats) {
                String serviceName = stat.getServiceName();
                
                // 기존 게이지 업데이트 또는 새로 생성
                serviceErrorCounts.computeIfAbsent(serviceName, key -> {
                    AtomicLong errorCount = new AtomicLong(0);
                    Gauge.builder("apibridge_service_errors_total", errorCount, val -> val.get())
                        .description("Total number of errors per service")
                        .tag("service_name", serviceName)
                        .register(meterRegistry);
                    return errorCount;
                });
                
                serviceErrorCounts.get(serviceName).set(stat.getTotalErrorCount());
                
                // 에러율 게이지는 매번 새로 등록하지 않고 한 번만 등록
                try {
                    Gauge.builder("apibridge_service_error_rate", stat, ErrorStatistics::getErrorRate)
                        .description("Error rate percentage per service")
                        .tag("service_name", serviceName)
                        .register(meterRegistry);
                } catch (Exception e) {
                    // 이미 등록된 경우 무시
                }
            }
            
            log.debug("Error metrics updated for {} services", errorStats.size());
            
        } catch (Exception e) {
            log.error("Failed to update error metrics", e);
        }
    }

    /**
     * API 호출 통계를 Prometheus 게이지로 노출
     */
    public void updateApiCallMetrics() {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(1); // 최근 1시간
            
            List<ApiCallStatistics> apiStats = apiCallAnalyticsService.getApiCallRanking(startTime, endTime, 20);
            
            for (ApiCallStatistics stat : apiStats) {
                String apiName = stat.getApiName();
                String provider = stat.getApiProvider();
                
                // API 총 호출 수
                apiCallCounts.computeIfAbsent(apiName, key -> {
                    AtomicLong callCount = new AtomicLong(0);
                    Gauge.builder("apibridge_api_calls_total", callCount, val -> val.get())
                        .description("Total number of API calls")
                        .tag("api_name", apiName)
                        .tag("provider", provider)
                        .register(meterRegistry);
                    return callCount;
                });
                
                apiCallCounts.get(apiName).set(stat.getTotalCallCount());
                
                // API 성공 호출 수
                apiSuccessCounts.computeIfAbsent(apiName, key -> {
                    AtomicLong successCount = new AtomicLong(0);
                    Gauge.builder("apibridge_api_calls_success_total", successCount, val -> val.get())
                        .description("Total number of successful API calls")
                        .tag("api_name", apiName)
                        .tag("provider", provider)
                        .register(meterRegistry);
                    return successCount;
                });
                
                apiSuccessCounts.get(apiName).set(stat.getSuccessCallCount());
                
                // API 성공률과 응답시간은 한 번만 등록
                try {
                    Gauge.builder("apibridge_api_success_rate", stat, ApiCallStatistics::getSuccessRate)
                        .description("API success rate percentage")
                        .tag("api_name", apiName)
                        .tag("provider", provider)
                        .register(meterRegistry);
                    
                    Gauge.builder("apibridge_api_response_time_avg", stat, ApiCallStatistics::getAverageResponseTime)
                        .description("Average API response time in milliseconds")
                        .tag("api_name", apiName)
                        .tag("provider", provider)
                        .register(meterRegistry);
                } catch (Exception e) {
                    // 이미 등록된 경우 무시
                }
            }
            
            log.debug("API call metrics updated for {} APIs", apiStats.size());
            
        } catch (Exception e) {
            log.error("Failed to update API call metrics", e);
        }
    }

    /**
     * 시스템 전반적 건강 상태 업데이트
     */
    public void updateSystemHealthStatus(String status) {
        long healthValue = switch (status.toUpperCase()) {
            case "HEALTHY" -> 1;
            case "WARNING" -> 0;
            case "CRITICAL" -> -1;
            default -> 0;
        };
        
        systemHealthValue.set(healthValue);
        log.debug("System health status updated: {}", status);
    }

    /**
     * 정기적으로 메트릭 업데이트를 수행하는 스케줄러
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000) // 1분마다
    public void updateMetricsScheduled() {
        log.debug("Starting scheduled metrics update");
        
        try {
            updateErrorMetrics();
            updateApiCallMetrics();
            
            log.debug("Scheduled metrics update completed successfully");
        } catch (Exception e) {
            log.error("Failed to update metrics in scheduled task", e);
        }
    }
}