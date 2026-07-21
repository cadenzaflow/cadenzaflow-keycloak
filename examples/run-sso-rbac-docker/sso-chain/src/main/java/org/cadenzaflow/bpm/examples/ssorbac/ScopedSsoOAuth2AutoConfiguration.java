/*
 * Example glue module. Copy of the platform's
 * CamundaSpringSecurityOAuth2AutoConfiguration with ONE change: the SSO login
 * SecurityFilterChain is scoped to the webapp surface via securityMatcher and
 * given an explicit high-precedence @Order, so a second (any-request)
 * SecurityFilterChain - here openid-rbac-security guarding /engine-rest - can
 * live in the same JVM. The platform autoconfiguration itself is excluded in
 * config/default.yml. This is the prototype of the planned platform fix.
 *
 * Licensed under the Apache License, Version 2.0 (same as the copied source).
 */
package org.cadenzaflow.bpm.examples.ssorbac;

import jakarta.annotation.Nullable;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.cadenzaflow.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.cadenzaflow.bpm.engine.spring.SpringProcessEngineServicesConfiguration;
import org.cadenzaflow.bpm.spring.boot.starter.CamundaBpmAutoConfiguration;
import org.cadenzaflow.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.cadenzaflow.bpm.spring.boot.starter.property.WebappProperty;
import org.cadenzaflow.bpm.spring.boot.starter.security.oauth2.OAuth2Properties;
import org.cadenzaflow.bpm.spring.boot.starter.security.oauth2.impl.AuthorizeTokenFilter;
import org.cadenzaflow.bpm.spring.boot.starter.security.oauth2.impl.OAuth2AuthenticationProvider;
import org.cadenzaflow.bpm.spring.boot.starter.security.oauth2.impl.OAuth2GrantedAuthoritiesMapper;
import org.cadenzaflow.bpm.spring.boot.starter.security.oauth2.impl.OAuth2IdentityProviderPlugin;
import org.cadenzaflow.bpm.spring.boot.starter.security.oauth2.impl.SsoLogoutSuccessHandler;
import org.cadenzaflow.bpm.webapp.impl.security.auth.ContainerBasedAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.ClientsConfiguredCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import java.util.Map;

@AutoConfigureAfter({ CamundaBpmAutoConfiguration.class, SpringProcessEngineServicesConfiguration.class })
@ConditionalOnBean(CamundaBpmProperties.class)
@Conditional(ClientsConfiguredCondition.class)
@EnableConfigurationProperties(OAuth2Properties.class)
public class ScopedSsoOAuth2AutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(ScopedSsoOAuth2AutoConfiguration.class);
  private final OAuth2Properties oAuth2Properties;
  private final String webappPath;

  public ScopedSsoOAuth2AutoConfiguration(CamundaBpmProperties properties,
                                          OAuth2Properties oAuth2Properties) {
    this.oAuth2Properties = oAuth2Properties;
    WebappProperty webapp = properties.getWebapp();
    this.webappPath = webapp.getApplicationPath();
  }

  @Bean
  public FilterRegistrationBean<?> webappAuthenticationFilter() {
    FilterRegistrationBean<Filter> filterRegistration = new FilterRegistrationBean<>();
    filterRegistration.setName("Container Based Authentication Filter");
    filterRegistration.setFilter(new ContainerBasedAuthenticationFilter());
    filterRegistration.setInitParameters(Map.of(
        ProcessEngineAuthenticationFilter.AUTHENTICATION_PROVIDER_PARAM, OAuth2AuthenticationProvider.class.getName()));
    filterRegistration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 1);
    filterRegistration.addUrlPatterns(webappPath + "/app/*", webappPath + "/api/*");
    filterRegistration.setDispatcherTypes(DispatcherType.REQUEST);
    return filterRegistration;
  }

  @Bean
  @ConditionalOnProperty(name = "identity-provider.enabled", havingValue = "true", prefix = OAuth2Properties.PREFIX, matchIfMissing = true)
  public OAuth2IdentityProviderPlugin identityProviderPlugin() {
    return new OAuth2IdentityProviderPlugin();
  }

  @Bean
  @ConditionalOnProperty(name = "identity-provider.group-name-attribute", prefix = OAuth2Properties.PREFIX)
  protected GrantedAuthoritiesMapper grantedAuthoritiesMapper() {
    return new OAuth2GrantedAuthoritiesMapper(oAuth2Properties);
  }

  @Bean
  @ConditionalOnProperty(name = "sso-logout.enabled", havingValue = "true", prefix = OAuth2Properties.PREFIX)
  protected SsoLogoutSuccessHandler ssoLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
    return new SsoLogoutSuccessHandler(clientRegistrationRepository, oAuth2Properties);
  }

  @Bean
  protected AuthorizeTokenFilter authorizeTokenFilter(OAuth2AuthorizedClientManager clientManager) {
    return new AuthorizeTokenFilter(clientManager);
  }

  @Bean
  @Order(100) // before the (any-request) RBAC chain
  public SecurityFilterChain scopedSsoFilterChain(HttpSecurity http,
                                                  AuthorizeTokenFilter authorizeTokenFilter,
                                                  @Nullable SsoLogoutSuccessHandler ssoLogoutSuccessHandler) throws Exception {

    logger.info("Enabling CadenzaFlow Spring Security oauth2 integration (scoped to the webapp surface)");

    // THE change vs. the platform autoconfiguration: this chain only claims
    // the webapp + login/logout surface; everything else (notably
    // /engine-rest/**) falls through to the next chain. PathPattern matchers,
    // NOT securityMatcher(String): the string variant builds MvcRequestMatchers,
    // which never match the webapp's /api (a non-MVC JAX-RS servlet).
    PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
    http.securityMatcher(new OrRequestMatcher(
        paths.matcher(webappPath + "/**"),
        paths.matcher("/oauth2/**"),
        paths.matcher("/login/**"),
        paths.matcher("/logout")));

    // @formatter:off
    http.authorizeHttpRequests(c -> c
            .requestMatchers(webappPath + "/app/**").authenticated()
            .requestMatchers(webappPath + "/api/**").authenticated()
            .anyRequest().permitAll()
        )
        .addFilterAfter(authorizeTokenFilter, OAuth2AuthorizationRequestRedirectFilter.class)
        .anonymous(AbstractHttpConfigurer::disable)
        .oidcLogout(c -> c.backChannel(Customizer.withDefaults()))
        .oauth2Login(Customizer.withDefaults())
        .logout(c -> c
            .clearAuthentication(true)
            .invalidateHttpSession(true)
        )
        .oauth2Client(Customizer.withDefaults())
        .cors(AbstractHttpConfigurer::disable)
        .csrf(AbstractHttpConfigurer::disable);
    // @formatter:on

    if (oAuth2Properties.getSsoLogout().isEnabled()) {
      http.logout(c -> c.logoutSuccessHandler(ssoLogoutSuccessHandler));
    }

    return http.build();
  }

}
