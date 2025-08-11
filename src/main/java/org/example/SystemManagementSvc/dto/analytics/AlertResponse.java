package org.example.SystemManagementSvc.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 처리 결과를 반환하는 응답 DTO
 * Alertmanager webhook 처리 결과 및 상태 정보 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertResponse {

    /** 처리 성공 여부 */
    private boolean success;
    
    /** 결과 메시지 */
    private String message;
    
    /** 알림 ID (그룹 키) */
    private String alertId;
    
    /** 처리 완료 시간 */
    private LocalDateTime processedAt;
    
    /** 알림이 발송된 채널 목록 */
    private List<String> notificationChannels;
    
    /** 알림 수신자 수 */
    private int recipientCount;
    
    /** 처리 시간 (밀리초) */
    private Long processingTimeMs;
    
    /** 에러 상세 정보 (실패 시) */
    private String errorDetails;
    
    /** 재시도 여부 */
    private boolean retryRequested;
}