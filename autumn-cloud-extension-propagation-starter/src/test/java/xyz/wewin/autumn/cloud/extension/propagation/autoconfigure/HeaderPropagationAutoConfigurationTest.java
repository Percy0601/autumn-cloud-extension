package xyz.wewin.autumn.cloud.extension.propagation.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import xyz.wewin.autumn.cloud.extension.propagation.HeaderPropagation;
import xyz.wewin.autumn.cloud.extension.propagation.HeaderPropagationProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderPropagationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HeaderPropagationAutoConfiguration.class,
                    HeaderPropagationWebClientAutoConfiguration.class));

    @Test
    void registersFacadeAndGroupConfigurer() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(HeaderPropagation.class);
            assertThat(context).hasSingleBean(RestClientHttpServiceGroupConfigurer.class);
            assertThat(context).hasSingleBean(HeaderPropagationProperties.class);
        });
    }

    @Test
    void defaultsToDisabledWithTrafficHeaderWhitelisted() {
        runner.run(context -> {
            HeaderPropagationProperties properties = context.getBean(HeaderPropagationProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.include()).containsExactly("X-Traffic");
        });
    }

    @Test
    void bindsPropertiesFromEnvironment() {
        runner.withPropertyValues(
                        "autumn.propagation.enabled=true",
                        "autumn.propagation.mode=BLACKLIST",
                        "autumn.propagation.include=X-Traffic,X-Tenant",
                        "autumn.propagation.max-headers=8")
                .run(context -> {
                    HeaderPropagationProperties properties = context.getBean(HeaderPropagationProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.mode()).isEqualTo(
                            xyz.wewin.autumn.cloud.extension.propagation.PropagationMode.BLACKLIST);
                    assertThat(properties.include()).containsExactly("X-Traffic", "X-Tenant");
                    assertThat(properties.maxHeaders()).isEqualTo(8);
                });
    }

    @Test
    void backsOffWhenUserProvidesOwnFacade() {
        HeaderPropagation custom = HeaderPropagation.of(new HeaderPropagationProperties(
                false, xyz.wewin.autumn.cloud.extension.propagation.PropagationMode.WHITELIST,
                List.of("X-Custom"), List.of(), 32, false));

        runner.withBean(HeaderPropagation.class, () -> custom)
                .run(context -> assertThat(context).hasSingleBean(HeaderPropagation.class));
    }
}
