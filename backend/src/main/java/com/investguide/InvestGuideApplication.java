package com.investguide;

import com.investguide.config.AppProperties;
import com.investguide.config.LlmProperties;
import com.investguide.config.PaymentProperties;
import com.investguide.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * InvestGuideUA backend entry point.
 *
 * <p>Single deployable Spring Boot JAR (MVP §1.1, §2). Modules live under
 * {@code com.investguide.*} per SPECIFICATION §12.
 */
@SpringBootApplication
@EnableConfigurationProperties({
        AppProperties.class,
        LlmProperties.class,
        PaymentProperties.class,
        SecurityProperties.class
})
public class InvestGuideApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvestGuideApplication.class, args);
    }
}
