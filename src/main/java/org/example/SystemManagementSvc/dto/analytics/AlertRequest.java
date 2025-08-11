package org.example.SystemManagementSvc.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Prometheus Alertmanager로부터 수신되는 알림 데이터 DTO
 * Webhook을 통해 전달되는 알림 정보를 매핑
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRequest {

    /** 알림 그룹 식별자 */
    private String groupKey;
    
    /** 알림 상태 (firing, resolved) */
    private String status;
    
    /** 수신자 정보 */
    private String receiver;
    
    /** 공통 라벨 (모든 알림에 적용되는 라벨) */
    private Map<String, String> commonLabels;
    
    /** 공통 어노테이션 (모든 알림에 적용되는 어노테이션) */
    private Map<String, String> commonAnnotations;
    
    /** 개별 알림 목록 */
    private List<Alert> alerts;
    
    /** 외부 URL (Alertmanager UI 링크) */
    private String externalURL;
    
    /** 알림 그룹 라벨 */
    private Map<String, String> groupLabels;

    /**
     * 개별 알림 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alert {
        
        /** 알림 상태 */
        private String status;
        
        /** 알림 라벨 */
        private Map<String, String> labels;
        
        /** 알림 어노테이션 */
        private Map<String, String> annotations;
        
        /** 알림 시작 시간 */
        private LocalDateTime startsAt;
        
        /** 알림 종료 시간 (resolved 상태일 때) */
        private LocalDateTime endsAt;
        
        /** 알림 생성자 URL */
        private String generatorURL;
        
        /** 지문 (알림 식별용 해시) */
        private String fingerprint;
    }
}