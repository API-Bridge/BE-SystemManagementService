package org.example.SystemManagementSvc.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ELK에서 수집되는 에러 로그 데이터 모델
 * Elasticsearch 인덱스에서 조회한 에러 로그 정보를 담는 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorLog {
    
    /** 로그 고유 ID */
    private String id;
    
    /** 에러가 발생한 서비스명 */
    private String serviceName;
    
    /** 에러 레벨 (ERROR, WARN, FATAL 등) */
    private String level;
    
    /** 에러 메시지 */
    private String message;
    
    /** 스택 트레이스 */
    private String stackTrace;
    
    /** HTTP 상태 코드 (API 에러의 경우) */
    private Integer httpStatus;
    
    /** 에러가 발생한 API 경로 */
    private String apiPath;
    
    /** 에러 발생 시간 */
    private LocalDateTime timestamp;
    
    /** 호스트명 */
    private String hostname;
    
    /** 에러 분류 (예: DATABASE_ERROR, VALIDATION_ERROR 등) */
    private String errorCategory;
    
    /** 사용자 ID (인증된 요청의 경우) */
    private String userId;
    
    /** 요청 ID (추적용) */
    private String requestId;
}