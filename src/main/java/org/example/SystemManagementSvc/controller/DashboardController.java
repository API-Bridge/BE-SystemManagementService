package org.example.SystemManagementSvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.dto.analytics.ApiCallStatistics;
import org.example.SystemManagementSvc.dto.analytics.DashboardResponse;
import org.example.SystemManagementSvc.dto.analytics.ErrorStatistics;
import org.example.SystemManagementSvc.dto.common.BaseResponse;
import org.example.SystemManagementSvc.service.ApiCallAnalyticsService;
import org.example.SystemManagementSvc.service.DashboardService;
import org.example.SystemManagementSvc.service.ErrorAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ELK 기반 시스템 분석 및 대시보드 API 컨트롤러
 * 서비스별 에러 통계와 외부 API 호출 통계를 제공하는 REST API
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard Analytics", description = "ELK 기반 시스템 모니터링 및 분석 대시보드 API")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ErrorAnalyticsService errorAnalyticsService;
    private final ApiCallAnalyticsService apiCallAnalyticsService;

    @Operation(
        summary = "통합 대시보드 분석 데이터 조회",
        description = "ELK 스택에서 수집된 로그를 분석하여 에러 순위와 API 호출 순위를 포함한 통합 대시보드 데이터를 반환합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "대시보드 분석 데이터 조회 성공",
            content = @Content(schema = @Schema(implementation = BaseResponse.class))
        ),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/analytics")
    public ResponseEntity<BaseResponse<DashboardResponse>> getDashboardAnalytics(
            @Parameter(description = "분석할 시간 범위 (시간 단위)", example = "24")
            @RequestParam(defaultValue = "24") Integer hours,
            
            @Parameter(description = "순위별 반환할 최대 개수", example = "10")
            @RequestParam(defaultValue = "10") Integer limit) {
        
        log.info("Dashboard analytics requested - hours: {}, limit: {}", hours, limit);
        
        DashboardResponse analytics = dashboardService.getDashboardAnalytics(hours, limit);
        
        BaseResponse<DashboardResponse> response = BaseResponse.<DashboardResponse>builder()
            .success(true)
            .message("대시보드 분석 데이터 조회 성공")
            .data(analytics)
            .timestamp(java.time.LocalDateTime.now())
            .build();
            
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "서비스별 에러 발생 순위 조회",
        description = "지정된 기간 동안 각 서비스별로 발생한 에러 횟수를 기준으로 순위를 반환합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "에러 순위 조회 성공",
            content = @Content(schema = @Schema(implementation = BaseResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/errors/ranking")
    public ResponseEntity<BaseResponse<List<ErrorStatistics>>> getErrorRanking(
            @Parameter(description = "분석할 시간 범위 (시간 단위)", example = "24")
            @RequestParam(defaultValue = "24") Integer hours,
            
            @Parameter(description = "반환할 최대 개수", example = "10")
            @RequestParam(defaultValue = "10") Integer limit) {
        
        log.info("Error ranking requested - hours: {}, limit: {}", hours, limit);
        
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);
        
        List<ErrorStatistics> errorRanking = errorAnalyticsService
            .getServiceErrorRanking(startTime, endTime, limit);
        
        BaseResponse<List<ErrorStatistics>> response = BaseResponse.<List<ErrorStatistics>>builder()
            .success(true)
            .message("서비스별 에러 순위 조회 성공")
            .data(errorRanking)
            .timestamp(java.time.LocalDateTime.now())
            .build();
            
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "외부 API 호출 순위 조회",
        description = "지정된 기간 동안 외부 API 호출 횟수를 기준으로 순위를 반환합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "API 호출 순위 조회 성공",
            content = @Content(schema = @Schema(implementation = BaseResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/api-calls/ranking")
    public ResponseEntity<BaseResponse<List<ApiCallStatistics>>> getApiCallRanking(
            @Parameter(description = "분석할 시간 범위 (시간 단위)", example = "24")
            @RequestParam(defaultValue = "24") Integer hours,
            
            @Parameter(description = "반환할 최대 개수", example = "10")
            @RequestParam(defaultValue = "10") Integer limit) {
        
        log.info("API call ranking requested - hours: {}, limit: {}", hours, limit);
        
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);
        
        List<ApiCallStatistics> apiCallRanking = apiCallAnalyticsService
            .getApiCallRanking(startTime, endTime, limit);
        
        BaseResponse<List<ApiCallStatistics>> response = BaseResponse.<List<ApiCallStatistics>>builder()
            .success(true)
            .message("외부 API 호출 순위 조회 성공")
            .data(apiCallRanking)
            .timestamp(java.time.LocalDateTime.now())
            .build();
            
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "시스템 헬스체크",
        description = "대시보드 서비스와 ELK 연결 상태를 확인합니다."
    )
    @GetMapping("/health")
    public ResponseEntity<BaseResponse<String>> healthCheck() {
        log.info("Dashboard health check requested");
        
        BaseResponse<String> response = BaseResponse.<String>builder()
            .success(true)
            .message("대시보드 서비스 정상 동작")
            .data("OK")
            .timestamp(java.time.LocalDateTime.now())
            .build();
            
        return ResponseEntity.ok(response);
    }
}