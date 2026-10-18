package com.yfctransport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "YFC_ADMIN_PASSWORD=local-test-password-only")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class LocalLoginTest {
  @Autowired MockMvc mvc;

  @Test
  void localAdminCanAuthenticate() throws Exception {
    mvc.perform(formLogin().user("admin").password("local-test-password-only"))
        .andExpect(authenticated().withUsername("admin").withRoles("SUPER_ADMIN"));
  }

  @Test
  void invalidPasswordIsRejected() throws Exception {
    mvc.perform(formLogin().user("admin").password("incorrect")).andExpect(unauthenticated());
  }
}
