package online.misterpilot.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.misterpilot.platform.config.GlobalExceptionHandler;
import online.misterpilot.platform.dto.request.UsageChargeRequest;
import online.misterpilot.platform.dto.response.CostCalculationResponse;
import online.misterpilot.platform.service.KeyUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("UsageController")
class UsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KeyUsageService keyUsageService;

    private UsageChargeRequest validRequest;

    private static CostCalculationResponse deepSeekResponse() {
        return CostCalculationResponse.builder()
                .costUsd(new BigDecimal("0.00095881"))
                .costInr(new BigDecimal("0.09"))
                .model("deepseek-v4-pro")
                .breakdown("model=deepseek-v4-pro | margin=0%")
                .walletDebited(false)
                .build();
    }

    private static CostCalculationResponse misterPilotResponse() {
        return CostCalculationResponse.builder()
                .costUsd(new BigDecimal("0.00113100"))
                .costInr(new BigDecimal("0.11"))
                .model("deepseek-v4-pro")
                .breakdown("model=deepseek-v4-pro | margin=30%")
                .walletDebited(true)
                .build();
    }

    @BeforeEach
    void setUp() {
        validRequest = UsageChargeRequest.builder()
                .apiKey("mp_sk_test1234")
                .outputTokens(1000L)
                .cacheHitTokens(500L)
                .cacheMissTokens(200L)
                .model("deepseek-v4-pro")
                .build();
    }

    // ================================================================
    //  Happy path
    // ================================================================

    @Nested
    @DisplayName("POST /internal/usage/charge — valid requests")
    class ValidRequests {

        @Test
        @DisplayName("Should return 200 OK with CostCalculationResponse body")
        void shouldReturn200WithResponseBody() throws Exception {
            CostCalculationResponse response = deepSeekResponse();
            when(keyUsageService.chargeUsage(
                    anyString(), anyLong(), anyLong(), anyLong(), anyString()))
                    .thenReturn(response);

            mockMvc.perform(post("/internal/usage/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.costUsd").value(0.00095881))
                    .andExpect(jsonPath("$.costInr").value(0.09))
                    .andExpect(jsonPath("$.model").value("deepseek-v4-pro"))
                    .andExpect(jsonPath("$.walletDebited").value(false));
        }

        @Test
        @DisplayName("Should call KeyUsageService.chargeUsage with correct args")
        void shouldCallServiceWithCorrectArgs() throws Exception {
            when(keyUsageService.chargeUsage(
                    anyString(), anyLong(), anyLong(), anyLong(), anyString()))
                    .thenReturn(deepSeekResponse());

            mockMvc.perform(post("/internal/usage/charge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)));

            verify(keyUsageService).chargeUsage(
                    eq("mp_sk_test1234"),
                    eq(1000L), eq(500L), eq(200L),
                    eq("deepseek-v4-pro"));
        }

        @Test
        @DisplayName("Should return walletDebited=false for DeepSeek key")
        void shouldReturnWalletNotDebitedForDeepSeek() throws Exception {
            when(keyUsageService.chargeUsage(
                    anyString(), anyLong(), anyLong(), anyLong(), anyString()))
                    .thenReturn(deepSeekResponse());

            mockMvc.perform(post("/internal/usage/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.walletDebited").value(false));
        }

        @Test
        @DisplayName("Should return walletDebited=true for MisterPilot key")
        void shouldReturnWalletDebitedForMisterPilot() throws Exception {
            when(keyUsageService.chargeUsage(
                    anyString(), anyLong(), anyLong(), anyLong(), anyString()))
                    .thenReturn(misterPilotResponse());

            mockMvc.perform(post("/internal/usage/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.walletDebited").value(true));
        }
    }

    // ================================================================
    //  Error cases
    // ================================================================

    @Nested
    @DisplayName("POST /internal/usage/charge — error responses")
    class ErrorResponses {

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturn400ForMissingBody() throws Exception {
            mockMvc.perform(post("/internal/usage/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when service throws IllegalArgumentException")
        void shouldReturn400ForIllegalArgument() throws Exception {
            when(keyUsageService.chargeUsage(
                    anyString(), anyLong(), anyLong(), anyLong(), anyString()))
                    .thenThrow(new IllegalArgumentException("Invalid API key"));

            mockMvc.perform(post("/internal/usage/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 409 when service throws IllegalStateException")
        void shouldReturn409ForIllegalState() throws Exception {
            when(keyUsageService.chargeUsage(
                    anyString(), anyLong(), anyLong(), anyLong(), anyString()))
                    .thenThrow(new IllegalStateException(
                            "Payment already processed"));

            mockMvc.perform(post("/internal/usage/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isConflict());
        }
    }
}
