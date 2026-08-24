package io.okagent.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiAuthorizationTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void rejectsAnonymousApiRequestsWithStableJson() throws Exception {
        mvc.perform(get("/api/v1/models"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void allowsViewerToReadButNotMutate() throws Exception {
        mvc.perform(get("/api/v1/models").with(jwtRole("VIEWER"))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/agents/{id}", UUID.randomUUID()).with(jwtRole("VIEWER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void limitsAccountManagementToAdministrators() throws Exception {
        mvc.perform(get("/api/v1/accounts").with(jwtRole("VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/accounts").with(jwtRole("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/users/{id}", UUID.randomUUID()).with(jwtRole("EDITOR")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/users/{id}", UUID.randomUUID()).with(jwtRole("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void keepsLoginEndpointPublic() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtRole(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
