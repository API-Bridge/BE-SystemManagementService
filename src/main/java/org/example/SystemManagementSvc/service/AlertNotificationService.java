package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.dto.analytics.AlertRequest;
import org.example.SystemManagementSvc.dto.analytics.AlertResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Prometheus Alertmanager와 연동하여 알림을 처리하고 AWS SES를 통해 이메일 발송
 * 시스템 에러, API 장애, 성능 이슈 등에 대한 실시간 알림 제공
 */
@Slf4j
@Service
public class AlertNotificationService {

    @Value("${aws.ses.region:ap-northeast-2}")
    private String sesRegion;
    
    @Value("${aws.ses.from-email:noreply@apibridge.com}")
    private String fromEmail;
    
    @Value("${email.mock.enabled:false}")
    private boolean mockEmailEnabled;
    
    private final MockEmailService mockEmailService;
    
    public AlertNotificationService(@org.springframework.beans.factory.annotation.Autowired(required = false) MockEmailService mockEmailService) {
        this.mockEmailService = mockEmailService;
    }
    
    private static final DateTimeFormatter ALERT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm:ss");

    /**
     * Prometheus Alertmanager로부터 수신된 알림 처리
     * 
     * @param alertRequest Prometheus 알림 데이터
     * @return 알림 처리 결과
     */
    public AlertResponse processAlert(AlertRequest alertRequest) {
        log.info("Processing alert notification: {}", alertRequest.getGroupKey());
        
        try {
            // 알림 타입별 분류 처리
            String alertType = determineAlertType(alertRequest);
            String severity = alertRequest.getCommonLabels().getOrDefault("severity", "warning");
            
            // 이메일 내용 생성
            String subject = generateEmailSubject(alertType, severity);
            String htmlContent = generateEmailContent(alertRequest, alertType, severity);
            
            // 관리자 이메일 목록 가져오기
            List<String> adminEmails = getAdminEmailList();
            
            // 비동기로 이메일 발송
            CompletableFuture<Void> emailTask = CompletableFuture.runAsync(() -> {
                sendEmails(adminEmails, subject, htmlContent);
            });
            
            // Slack/Teams 등 다른 채널 알림 (추후 확장)
            notifyOtherChannels(alertRequest, alertType, severity);
            
            log.info("Alert processing completed for: {}", alertRequest.getGroupKey());
            
            return AlertResponse.builder()
                .success(true)
                .message("Alert notification processed successfully")
                .alertId(alertRequest.getGroupKey())
                .processedAt(LocalDateTime.now())
                .notificationChannels(List.of("email"))
                .recipientCount(adminEmails.size())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to process alert notification", e);
            
            return AlertResponse.builder()
                .success(false)
                .message("Failed to process alert: " + e.getMessage())
                .alertId(alertRequest.getGroupKey())
                .processedAt(LocalDateTime.now())
                .notificationChannels(List.of())
                .recipientCount(0)
                .build();
        }
    }

    /**
     * 알림 타입 결정
     */
    private String determineAlertType(AlertRequest alertRequest) {
        String alertName = alertRequest.getCommonLabels().getOrDefault("alertname", "Unknown");
        
        // Prometheus 알림 규칙 이름 기반으로 타입 분류
        if (alertName.contains("APIError") || alertName.contains("ServiceError")) {
            return "SERVICE_ERROR";
        } else if (alertName.contains("HighCPU") || alertName.contains("HighMemory")) {
            return "PERFORMANCE_ISSUE";
        } else if (alertName.contains("ServiceDown") || alertName.contains("HealthCheck")) {
            return "SERVICE_DOWN";
        } else if (alertName.contains("ExternalAPI")) {
            return "EXTERNAL_API_ISSUE";
        }
        
        return "SYSTEM_ALERT";
    }

    /**
     * 이메일 제목 생성
     */
    private String generateEmailSubject(String alertType, String severity) {
        String urgencyLabel = severity.equalsIgnoreCase("critical") ? "[긴급]" : 
                             severity.equalsIgnoreCase("warning") ? "[주의]" : "[알림]";
        
        String typeLabel = switch (alertType) {
            case "SERVICE_ERROR" -> "서비스 에러 발생";
            case "PERFORMANCE_ISSUE" -> "성능 이슈 감지";
            case "SERVICE_DOWN" -> "서비스 다운 알림";
            case "EXTERNAL_API_ISSUE" -> "외부 API 장애";
            default -> "시스템 알림";
        };
        
        return String.format("%s API Bridge - %s", urgencyLabel, typeLabel);
    }

    /**
     * HTML 이메일 내용 생성
     */
    private String generateEmailContent(AlertRequest alertRequest, String alertType, String severity) {
        StringBuilder htmlContent = new StringBuilder();
        
        // 알림 심각도에 따른 색상 설정
        String severityColor = severity.equalsIgnoreCase("critical") ? "#dc3545" : 
                              severity.equalsIgnoreCase("warning") ? "#fd7e14" : "#28a745";
        
        htmlContent.append("<!DOCTYPE html>")
            .append("<html><head><meta charset='UTF-8'></head><body>")
            .append("<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>")
            
            // 헤더
            .append("<div style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; text-align: center;'>")
            .append("<h1 style='margin: 0;'>🚨 API Bridge 시스템 알림</h1>")
            .append("<p style='margin: 10px 0 0 0;'>").append(LocalDateTime.now().format(ALERT_TIME_FORMATTER)).append("</p>")
            .append("</div>")
            
            // 알림 요약
            .append("<div style='padding: 20px; background-color: #f8f9fa;'>")
            .append("<div style='background-color: ").append(severityColor).append("; color: white; padding: 10px; border-radius: 5px; margin-bottom: 15px;'>")
            .append("<h2 style='margin: 0;'>심각도: ").append(severity.toUpperCase()).append("</h2>")
            .append("</div>")
            
            // 기본 정보
            .append("<table style='width: 100%; border-collapse: collapse; margin-bottom: 20px;'>")
            .append("<tr><td style='padding: 8px; border: 1px solid #ddd; background-color: #e9ecef;'><strong>알림 ID</strong></td>")
            .append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(alertRequest.getGroupKey()).append("</td></tr>")
            .append("<tr><td style='padding: 8px; border: 1px solid #ddd; background-color: #e9ecef;'><strong>알림 타입</strong></td>")
            .append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(alertType).append("</td></tr>")
            .append("<tr><td style='padding: 8px; border: 1px solid #ddd; background-color: #e9ecef;'><strong>상태</strong></td>")
            .append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(alertRequest.getStatus()).append("</td></tr>")
            .append("</table>");
        
        // 상세 알림 정보
        if (!alertRequest.getAlerts().isEmpty()) {
            htmlContent.append("<h3>상세 알림 내용</h3>");
            
            alertRequest.getAlerts().forEach(alert -> {
                htmlContent.append("<div style='border-left: 4px solid ").append(severityColor).append("; padding-left: 15px; margin-bottom: 15px;'>")
                    .append("<h4 style='margin-top: 0;'>").append(alert.getAnnotations().getOrDefault("summary", "알림 발생")).append("</h4>")
                    .append("<p>").append(alert.getAnnotations().getOrDefault("description", "상세 정보 없음")).append("</p>");
                
                if (alert.getLabels() != null && !alert.getLabels().isEmpty()) {
                    htmlContent.append("<details><summary>라벨 정보</summary><ul>");
                    alert.getLabels().forEach((key, value) -> 
                        htmlContent.append("<li><strong>").append(key).append(":</strong> ").append(value).append("</li>")
                    );
                    htmlContent.append("</ul></details>");
                }
                
                htmlContent.append("</div>");
            });
        }
        
        // 대응 가이드
        htmlContent.append("<div style='background-color: #e3f2fd; padding: 15px; border-radius: 5px; margin-top: 20px;'>")
            .append("<h3 style='margin-top: 0; color: #1976d2;'>📋 대응 가이드</h3>")
            .append(generateActionGuide(alertType, severity))
            .append("</div>");
        
        // 푸터
        htmlContent.append("<div style='text-align: center; padding: 20px; color: #666; border-top: 1px solid #ddd; margin-top: 20px;'>")
            .append("<p>이 알림은 API Bridge 모니터링 시스템에서 자동 발송되었습니다.</p>")
            .append("<p>문의사항이 있으시면 시스템 관리자에게 연락해주세요.</p>")
            .append("</div>")
            .append("</div></body></html>");
        
        return htmlContent.toString();
    }

    /**
     * 알림 타입별 대응 가이드 생성
     */
    private String generateActionGuide(String alertType, String severity) {
        return switch (alertType) {
            case "SERVICE_ERROR" -> 
                "<ul>" +
                "<li>서비스 로그를 확인하여 에러 원인을 파악하세요</li>" +
                "<li>ELK Dashboard에서 에러 발생 패턴을 분석하세요</li>" +
                "<li>필요시 해당 서비스를 재시작하거나 롤백을 고려하세요</li>" +
                "</ul>";
                
            case "PERFORMANCE_ISSUE" -> 
                "<ul>" +
                "<li>시스템 리소스 사용량을 확인하세요 (CPU, Memory, Disk)</li>" +
                "<li>느린 쿼리나 API 호출이 있는지 확인하세요</li>" +
                "<li>트래픽 급증 여부를 확인하고 필요시 스케일링하세요</li>" +
                "</ul>";
                
            case "SERVICE_DOWN" -> 
                "<ul>" +
                "<li>즉시 서비스 상태를 확인하고 복구 작업을 시작하세요</li>" +
                "<li>로드밸런서에서 해당 인스턴스를 제외하세요</li>" +
                "<li>비상 연락망을 통해 관련 팀에 알리세요</li>" +
                "</ul>";
                
            case "EXTERNAL_API_ISSUE" -> 
                "<ul>" +
                "<li>외부 API 제공업체의 상태 페이지를 확인하세요</li>" +
                "<li>대체 API나 캐시된 데이터 사용을 검토하세요</li>" +
                "<li>API 호출 제한에 걸렸는지 확인하세요</li>" +
                "</ul>";
                
            default -> 
                "<ul>" +
                "<li>시스템 전반적인 상태를 점검하세요</li>" +
                "<li>관련 로그를 확인하여 원인을 파악하세요</li>" +
                "<li>필요시 개발팀에 에스컬레이션하세요</li>" +
                "</ul>";
        };
    }

    /**
     * SES 또는 Mock을 통한 이메일 발송
     */
    private void sendEmails(List<String> recipients, String subject, String htmlContent) {
        if (mockEmailEnabled && mockEmailService != null) {
            // 개발 환경에서는 Mock 서비스 사용
            log.info("Using Mock Email Service for development");
            mockEmailService.sendEmails(recipients, subject, htmlContent);
            return;
        }
        
        // 프로덕션 환경에서는 실제 AWS SES 사용
        try (SesClient sesClient = SesClient.builder()
            .region(Region.of(sesRegion))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()) {
            
            for (String recipient : recipients) {
                try {
                    SendEmailRequest emailRequest = SendEmailRequest.builder()
                        .source(fromEmail)
                        .destination(Destination.builder()
                            .toAddresses(recipient)
                            .build())
                        .message(Message.builder()
                            .subject(Content.builder()
                                .data(subject)
                                .charset("UTF-8")
                                .build())
                            .body(Body.builder()
                                .html(Content.builder()
                                    .data(htmlContent)
                                    .charset("UTF-8")
                                    .build())
                                .build())
                            .build())
                        .build();
                    
                    SendEmailResponse response = sesClient.sendEmail(emailRequest);
                    log.info("Email sent successfully to {}, MessageId: {}", recipient, response.messageId());
                    
                } catch (Exception e) {
                    log.error("Failed to send email to {}", recipient, e);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize SES client", e);
        }
    }

    /**
     * 기타 알림 채널 (Slack, Teams 등)
     * 추후 확장 가능
     */
    private void notifyOtherChannels(AlertRequest alertRequest, String alertType, String severity) {
        // TODO: Slack, Microsoft Teams, Discord 등 다른 알림 채널 구현
        log.debug("Other notification channels not implemented yet");
    }

    /**
     * 관리자 이메일 목록 조회
     * 추후 데이터베이스나 설정 파일에서 동적으로 조회 가능
     */
    private List<String> getAdminEmailList() {
        // TODO: DB에서 관리자 이메일 목록을 동적으로 조회
        return List.of(
            "admin@apibridge.com",
            "devops@apibridge.com",
            "monitoring@apibridge.com"
        );
    }
}