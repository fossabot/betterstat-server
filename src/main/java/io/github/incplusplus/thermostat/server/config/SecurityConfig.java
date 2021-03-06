package io.github.incplusplus.thermostat.server.config;

import io.github.incplusplus.thermostat.security.ActiveUserStore;
import io.github.incplusplus.thermostat.service.MongoUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@EnableWebSecurity
@Configuration
@EnableConfigurationProperties
public class SecurityConfig extends WebSecurityConfigurerAdapter
{
	@Autowired
	MongoUserDetailsService userDetailsService;
	
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
				.csrf().disable()
				.authorizeRequests()
					.antMatchers("/v2/**","/swagger-ui.html", "/swagger-resources/**", "/webjars/**").permitAll()
					.anyRequest().authenticated()
				.and()
				.httpBasic()
				.and()
				.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
	}
	
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
	
	@ConditionalOnMissingBean(SessionRegistry.class)
	@Bean
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}
	
	@Bean
	@Override
	public AuthenticationManager authenticationManagerBean() throws Exception
	{
		return super.authenticationManagerBean();
	}
	
	@Bean
	public ActiveUserStore activeUserStore() {
		return new ActiveUserStore();
	}
	
	@Override
	public void configure(AuthenticationManagerBuilder builder) throws Exception {
		builder.userDetailsService(userDetailsService);
	}
}