package com.toyproject.eron.erapi;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.toyproject.eron.erapi.dto.UserSearchResponse;
import com.toyproject.eron.global.error.GlobalExceptionHandler;

class EternalReturnControllerTest {

    private EternalReturnApiClient eternalReturnApiClient;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        eternalReturnApiClient = Mockito.mock(EternalReturnApiClient.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EternalReturnController(eternalReturnApiClient))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchUserReturnsMappedUser() throws Exception {
        when(eternalReturnApiClient.getUserByNickname("testUser"))
                .thenReturn(new UserSearchResponse(
                        12345,
                        "testUser",
                        java.util.Map.of(
                                "user", java.util.Map.of(
                                        "userNum", 12345,
                                        "nickname", "testUser"
                                )
                        )
                ));

        mockMvc.perform(get("/api/er/users/search").param("nickname", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userNum").value(12345))
                .andExpect(jsonPath("$.nickname").value("testUser"))
                .andExpect(jsonPath("$.raw.user.userNum").value(12345));
    }

    @Test
    void apiExceptionReturnsStructuredErrorResponse() throws Exception {
        when(eternalReturnApiClient.getUserGames(12345))
                .thenThrow(new EternalReturnApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests"
                ));

        mockMvc.perform(get("/api/er/users/12345/games"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Too Many Requests"));
    }

    @Test
    void missingRequiredQueryParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/er/users/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}
