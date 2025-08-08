package org.example.SystemManagementSvc.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ELK에서 수집되는 외부 API 호출 로그 데이터 모델
 * Elasticsearch 인덱스에서 조회한 API 호출 정보를 담는 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiCallLog {
    
    /** 로그 고유 ID */
    private String id;
    
    /** 호출된 외부 API 이름/식별자 */
    private String apiName;
    
    /** API 제공업체 (예: 기상청, 서울시청 등) */
    private String apiProvider;
    
    /** API 엔드포인트 URL */
    private String apiUrl;
    
    /** HTTP 메소드 (GET, POST 등) */
    private String httpMethod;
    
    /** HTTP 응답 상태 코드 */
    private Integer responseStatus;
    
    /** API 호출 소요 시간 (밀리초) */
    private Long responseTime;
    
    /** 요청 크기 (바이트) */
    private Long requestSize;
    
    /** 응답 크기 (바이트) */
    private Long responseSize;
    
    /** 호출 성공 여부 */
    private Boolean isSuccess;
    
    /** 에러 메시지 (실패한 경우) */
    private String errorMessage;
    
    /** API 호출 시간 */
    private LocalDateTime timestamp;
    
    /** 호출한 서비스명 */
    private String callerService;
    
    /** 사용자 ID (인증된 요청의 경우) */
    private String userId;
    
    /** 요청 ID (추적용) */
    private String requestId;
    
    /** API 카테고리 (날씨, 교통, 공공시설 등) */
    private String apiCategory;
}