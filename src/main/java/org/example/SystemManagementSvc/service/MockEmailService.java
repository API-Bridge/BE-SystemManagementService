package org.example.SystemManagementSvc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 개발 환경용 Mock 이메일 서비스
 * 실제 이메일은 발송하지 않고 로그로만 출력
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "email.mock.enabled", havingValue = "true")
public class MockEmailService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 모킹된 이메일 발송 (실제로는 로그만 출력)
     */
    public boolean sendEmail(String recipient, String subject, String htmlContent) {
        try {
            log.info("📧 MOCK EMAIL SENT 📧");
            log.info("┌─────────────────────────────────────────────────────────────┐");
            log.info("│ Mock Email Service - Development Mode                      │");
            log.info("├─────────────────────────────────────────────────────────────┤");
            log.info("│ Timestamp: {}", LocalDateTime.now().format(TIMESTAMP_FORMATTER) + "                            │");
            log.info("│ To:        {}", String.format("%-50s", recipient) + "│");
            log.info("│ Subject:   {}", String.format("%-50s", truncate(subject, 50)) + "│");
            log.info("├─────────────────────────────────────────────────────────────┤");
            log.info("│ HTML Content Preview:                                       │");
            
            // HTML 내용을 텍스트로 변환하여 미리보기
            String textPreview = htmlContent
                .replaceAll("<[^>]+>", "")  // HTML 태그 제거
                .replaceAll("\\s+", " ")    // 연속된 공백을 하나로
                .trim();
            
            // 내용을 여러 줄로 나누어 표시
            String[] lines = wrapText(textPreview, 59);
            for (String line : lines) {
                log.info("│ {}", String.format("%-59s", line) + "│");
            }
            
            log.info("└─────────────────────────────────────────────────────────────┘");
            
            // 상세 HTML 내용은 DEBUG 레벨로
            log.debug("Full HTML Content:\n{}", htmlContent);
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to mock email sending", e);
            return false;
        }
    }
    
    /**
     * 여러 수신자에게 이메일 발송
     */
    public void sendEmails(List<String> recipients, String subject, String htmlContent) {
        log.info("🚀 Sending mock emails to {} recipients", recipients.size());
        
        for (String recipient : recipients) {
            sendEmail(recipient, subject, htmlContent);
        }
        
        log.info("✅ All mock emails sent successfully!");
    }
    
    /**
     * 텍스트를 지정된 길이로 자르기
     */
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * 텍스트를 지정된 폭에 맞게 여러 줄로 나누기
     */
    private String[] wrapText(String text, int width) {
        if (text.length() <= width) {
            return new String[]{text};
        }
        
        java.util.List<String> lines = new java.util.ArrayList<>();
        String remaining = text;
        
        while (remaining.length() > width) {
            int breakPoint = remaining.lastIndexOf(' ', width);
            if (breakPoint == -1) {
                breakPoint = width;
            }
            
            lines.add(remaining.substring(0, breakPoint));
            remaining = remaining.substring(breakPoint).trim();
        }
        
        if (!remaining.isEmpty()) {
            lines.add(remaining);
        }
        
        // 최대 10줄까지만 표시
        if (lines.size() > 10) {
            lines = lines.subList(0, 9);
            lines.add("... (content truncated for display) ...");
        }
        
        return lines.toArray(new String[0]);
    }
}