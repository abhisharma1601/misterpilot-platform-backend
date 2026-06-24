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

import static org.mockito.ArgumentMatchers.any;
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

    private static CostCalculationResponse chargedResponse() {
        return CostCalculationResponse.builder()
                .costUsd(BigDecimal.ZERO)
                .costInr(new BigDecimal("1.00"))
                .model("deepseek-v4-pro")
                .breakdown("deducted=₹1.00")
                .walletDebited(true)
                .build();
    }

    @BeforeEach
    void setUp() {
        validRequest = UsageChargeRequest.builder()
                .apiKey("mp_sk_test1234")
                .costInr(new BigDecimal("1.00"))
                .model("deepseek-v4-pro")
                .outputTokens(1000L)
                .cacheHitTokens(500L)
                .cacheMissTokens(200L)
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
            when(keyUsageService.chargeUsage(
                    anyString(), any(BigDecimal.class), anyString(), anyLong(), anyLong(), anyLong()))
                    .thenReturn(chargedResponse());

            mockMvc.perform(post("/internal/usage/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.costInr").value(1.00))
                    .andExpect(jsonPath("$.model").value("deepseek-v4-pro"))
                    .andExpect(jsonPath("$.walletDebited").value(true));
        }

        @Test
        @DisplayName("Should call KeyUsageService.chargeUsage with all correct args")
        void shouldCallServiceWithCorrectArgs() throws Exception {
            when(keyUsageService.chargeUsage(
                    anyString(), any(BigDecimal.class), anyString(), anyLong(), anyLong(), anyLong()))
                    .thenReturn(chargedResponse());

            mockMvc.perform(post("/internal/usage/charge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)));

            verify(keyUsageService).chargeUsage(
                    eq("mp_sk_test1234"),
                    eq(new BigDecimal("1.00")),
                    eq("deepseek-v4-pro"),
                    eq(1000L), eq(500L), eq(200L));
        }

        @Test
        @DisplayName("Should return walletDebited=true")
        void shouldReturnWalletDebited() throws Exception {
            when(keyUsageService.chargeUsage(
                    anyString(), any(BigDecimal.class), anyString(), anyLong(), anyLong(), anyLong()))
                    .thenReturn(chargedResponse());

            mockMvc.perform(post("/internal/usage/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.walletDebited").value(true));
        }

        @Test
        @DisplayName("Should return breakdown string in response")
        void shouldReturnBreakdown() throws Exception {
            when(keyUsageService.chargeUsage(
                    anyString(), any(BigDecimal.class), anyString(), anyLong(), anyLong(), anyLong()))
                    .thenReturn(chargedResponse());

            mockMvc.perform(post("/internal/usage/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.breakdown").isNotEmpty());
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
                    anyString(), any(BigDecimal.class), anyString(), anyLong(), anyLong(), anyLong()))
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
                    anyString(), any(BigDecimal.class), anyString(), anyLong(), anyLong(), anyLong()))
                    .thenThrow(new IllegalStateException("Insufficient balance"));

            mockMvc.perform(post("/internal/usage/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isConflict());
        }
    }
}
