package com.performance.platform.app.web;

import com.performance.platform.app.api.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaticResourceTest {

    @Configuration
    @EnableWebMvc
    static class MvcConfiguration {}

    @Test
    void servesFaviconAndReturns404ForMissingStaticResources() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "web.ui.enabled=true");
            context.register(MvcConfiguration.class, WebUiConfiguration.class, ApiExceptionHandler.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();

            mvc.perform(get("/assets/favicon.svg"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
            mvc.perform(get("/assets/missing.svg"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
        }
    }
}
