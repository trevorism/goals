package com.trevorism.config

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import org.junit.jupiter.api.Test

class ConfigurationTest {

    @Test
    void testTheBaseUrlPlaceholderResolvesToTheFullHttpsUrl() {
        Environment environment = ApplicationContext.builder().build().environment.start()

        assert environment.placeholderResolver.resolveRequiredPlaceholders('${goals.base-url}') == "https://goals.action.trevorism.com"

        environment.stop()
    }
}
