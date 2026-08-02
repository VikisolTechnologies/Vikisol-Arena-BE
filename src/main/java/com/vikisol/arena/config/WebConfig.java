package com.vikisol.arena.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

// Applies weak ETags to every GET response (computed from the response body) so repeated identical
// list/detail fetches (jobs, applications, activity feed, etc.) can 304 instead of re-transferring
// a full payload - part of the API-cost-discipline requirement rather than hand-rolling ETag logic
// per controller.
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/*");
        registration.setName("etagFilter");
        return registration;
    }
}
