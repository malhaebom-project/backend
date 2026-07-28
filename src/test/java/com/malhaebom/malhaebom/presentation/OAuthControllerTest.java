package com.malhaebom.malhaebom.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OAuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void Google_OAuth_로그인_경로로_이동한다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/google/authorize"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "/oauth2/authorization/google"
                ));
    }
}
