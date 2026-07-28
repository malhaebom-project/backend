package com.malhaebom.malhaebom.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 로그아웃하면_리프레시_쿠키를_만료시킨다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(
                                "MALHAEBOM_REFRESH",
                                "unknown-refresh-token"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().string(
                        "Set-Cookie",
                        containsString("MALHAEBOM_REFRESH=")
                ))
                .andExpect(header().string(
                        "Set-Cookie",
                        containsString("Max-Age=0")
                ));
    }
}
