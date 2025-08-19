package org.example.SystemManagementSvc.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@TestPropertySource(properties = {
    "spring.application.name=test-service",
    "BUILD_VERSION=1.0.0"
})
@DisplayName("HealthController 테스트")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("헬스체크 성공 테스트")
    void health_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Service is healthy"))
                .andExpect(jsonPath("$.data.service").value("test-service"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.timestamp").exists())
                .andExpect(jsonPath("$.data.javaVersion").exists())
                .andExpect(jsonPath("$.data.javaVendor").exists());
    }

    @Test
    @DisplayName("헬스체크 응답 구조 검증")
    void health_ResponseStructure() throws Exception {
        // When & Then
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").isBoolean())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data.service").isString())
                .andExpect(jsonPath("$.data.status").isString())
                .andExpect(jsonPath("$.data.version").isString())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("헬스체크 기본값 테스트")
    void health_DefaultValues() throws Exception {
        // When & Then
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.javaVersion").value(System.getProperty("java.version")))
                .andExpect(jsonPath("$.data.javaVendor").value(System.getProperty("java.vendor")));
    }
}