package org.airsonic.player;

import com.tesshu.jpsonic.controller.ViewName;
import com.tesshu.jpsonic.filter.FontSchemeFilter;
import org.airsonic.player.filter.BootstrapVerificationFilter;
import org.airsonic.player.filter.MetricsFilter;
import org.airsonic.player.filter.ParameterDecodingFilter;
import org.airsonic.player.filter.RESTFilter;
import org.airsonic.player.filter.RequestEncodingFilter;
import org.airsonic.player.filter.ResponseHeaderFilter;
import org.airsonic.player.spring.DatabaseConfiguration.ProfileNameConstants;
import org.airsonic.player.util.LegacyHsqlUtil;
import org.directwebremoting.servlet.DwrServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Profiles;
import org.springframework.util.ReflectionUtils;

import javax.servlet.Filter;
import javax.servlet.Servlet;

import java.lang.reflect.Method;

@SpringBootApplication(exclude = { JmxAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
        MultipartAutoConfiguration.class, // TODO: update to use spring boot builtin multipart support
        LiquibaseAutoConfiguration.class }, scanBasePackages = { "org.airsonic.player", "com.tesshu.jpsonic" })
public class Application extends SpringBootServletInitializer
        implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    /**
     * Registers the DWR servlet.
     *
     * @return a registration bean.
     */
    @Bean
    public ServletRegistrationBean<Servlet> dwrServletRegistrationBean() {
        ServletRegistrationBean<Servlet> servlet = new ServletRegistrationBean<>(new DwrServlet(), "/dwr/*");
        servlet.addInitParameter("crossDomainSessionSecurity", "false");
        return servlet;
    }

    @Bean
    public ServletRegistrationBean<Servlet> cxfServletBean() {
        return new ServletRegistrationBean<>(new org.apache.cxf.transport.servlet.CXFServlet(), "/ws/*");
    }

    @Bean
    public FilterRegistrationBean<Filter> bootstrapVerificationFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(bootstrapVerificationFiler());
        registration.addUrlPatterns("/*");
        registration.setName("BootstrapVerificationFilter");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public Filter bootstrapVerificationFiler() {
        return new BootstrapVerificationFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> parameterDecodingFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(parameterDecodingFilter());
        registration.addUrlPatterns("/*");
        registration.setName("ParameterDecodingFilter");
        registration.setOrder(2);
        return registration;
    }

    @Bean
    public Filter parameterDecodingFilter() {
        return new ParameterDecodingFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> restFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(restFilter());
        registration.addUrlPatterns("/rest/*");
        registration.setName("RESTFilter");
        registration.setOrder(3);
        return registration;
    }

    @Bean
    public Filter restFilter() {
        return new RESTFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> requestEncodingFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(requestEncodingFilter());
        registration.addUrlPatterns("/*");
        registration.addInitParameter("encoding", "UTF-8");
        registration.setName("RequestEncodingFilter");
        registration.setOrder(4);
        return registration;
    }

    @Bean
    public Filter requestEncodingFilter() {
        return new RequestEncodingFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> cacheFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(cacheFilter());
        registration.addUrlPatterns("/icons/*", "/style/*", "/script/*", "/dwr/*", "/icons/*",
                "/" + ViewName.COVER_ART.value(), "/" + ViewName.AVATAR.value());
        registration.addInitParameter("Cache-Control", "max-age=36000");
        registration.setName("CacheFilter");
        registration.setOrder(5);
        return registration;
    }

    @Bean
    public Filter cacheFilter() {
        return new ResponseHeaderFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> noCacheFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(noCacheFilter());
        registration.addUrlPatterns("/" + ViewName.STATUS_CHART.value(), "/" + ViewName.USER_CHART.value(),
                "/" + ViewName.PLAY_QUEUE.value(), "/" + ViewName.PODCAST_CHANNELS.value(),
                "/" + ViewName.PODCAST_CHANNEL.value(), "/" + ViewName.HELP.value(), "/" + ViewName.TOP.value(),
                "/" + ViewName.HOME.value());
        registration.addInitParameter("Cache-Control", "no-cache, post-check=0, pre-check=0");
        registration.addInitParameter("Pragma", "no-cache");
        registration.addInitParameter("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        registration.setName("NoCacheFilter");
        registration.setOrder(6);
        return registration;
    }

    @Bean
    public Filter metricsFilter() {
        return new MetricsFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> metricsFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(metricsFilter());
        registration.setOrder(7);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<FontSchemeFilter> fontSchemeFilterRegistration() {
        FilterRegistrationBean<FontSchemeFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new FontSchemeFilter());
        registration.addUrlPatterns("*.view", "/login", "/index");
        registration.setOrder(8);
        return registration;
    }

    @Bean
    public Filter noCacheFilter() {
        return new ResponseHeaderFilter();
    }

    private static SpringApplicationBuilder doConfigure(SpringApplicationBuilder application) {
        // Handle HSQLDB database upgrades from 1.8 to 2.x before any beans are started.
        application.application().addListeners((ApplicationListener<ApplicationPreparedEvent>) event -> {
            if (event.getApplicationContext().getEnvironment()
                    .acceptsProfiles(Profiles.of(ProfileNameConstants.LEGACY))) {
                LegacyHsqlUtil.upgradeHsqldbDatabaseSafely();
            }
        });

        // Customize the application or call application.sources(...) to add sources
        // Since our example is itself a @Configuration class (via @SpringBootApplication)
        // we actually don't need to override this method.
        return application.sources(Application.class).web(WebApplicationType.SERVLET);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return doConfigure(application);
    }

    @Override
    public void customize(ConfigurableServletWebServerFactory container) {
        LOG.trace("Servlet container is {}", container.getClass().getCanonicalName());
        // Yes, there is a good reason we do this.
        // We cannot count on the tomcat classes being on the classpath which will
        // happen if the war is deployed to another app server like Jetty. So, we
        // ensure this class does not have any direct dependencies on any Tomcat
        // specific classes.
        try {
            Class<?> tomcatESCF = Class
                    .forName("org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory");
            if (tomcatESCF.isInstance(container)) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Detected Tomcat web server");
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Attempting to optimize tomcat");
                }
                Object tomcatESCFInstance = tomcatESCF.cast(container);
                Class<?> tomcatApplicationClass = Class.forName("org.airsonic.player.TomcatApplicationHelper");
                Method configure = ReflectionUtils.findMethod(tomcatApplicationClass, "configure", tomcatESCF);
                if (configure == null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Cannot find configure method.");
                    }
                } else {
                    configure.invoke(null, tomcatESCFInstance);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Tomcat optimizations complete");
                    }
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Skipping tomcat optimization as we are not running on tomcat");
                }
            }
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No tomcat classes found");
            }
        } catch (Exception e) {
            LOG.warn("An error happened while trying to optimize tomcat", e);
        }

    }

    public static void main(String[] args) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder();
        doConfigure(builder).run(args);
    }

}
