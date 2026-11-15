/*
 * This file is part of Jpsonic.
 *
 * Jpsonic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Jpsonic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * (C) 2009 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.service;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.domain.language.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UrlPathHelper;

public final class NetworkUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkUtils.class);
    private static final String X_FORWARDED_SERVER = "X-Forwarded-Server";
    private static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    private static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    private static final UrlPathHelper URL_PATH_HELPER = new UrlPathHelper();

    private NetworkUtils() {
    }

    public static String getBaseUrl(HttpServletRequest request) {
        try {
            URI uri;
            try {
                uri = calculateProxyUri(request);
            } catch (URISyntaxException | IllegalArgumentException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Could not calculate proxy uri: " + e.getMessage());
                }
                uri = calculateNonProxyUri(request);
            }

            String baseUrl = uri.toString() + "/";
            if (LOG.isDebugEnabled()) {
                LOG.debug("Calculated base url to " + baseUrl);
            }
            return baseUrl;
        } catch (ExecutionException e) {
            throw new CompletionException("Could not calculate base url: ", e);
        }
    }

    private static URI calculateProxyUri(HttpServletRequest request) throws URISyntaxException {
        String xForardedHost = request.getHeader(X_FORWARDED_HOST);
        // If the request has been through multiple reverse proxies,
        // We need to return the original Host that the client used
        if (xForardedHost != null) {
            xForardedHost = xForardedHost.split(",", -1)[0];
        }

        if (!isValidXForwardedHost(xForardedHost)) {
            xForardedHost = request.getHeader(X_FORWARDED_SERVER);

            // If the request has been through multiple reverse proxies,
            // We need to return the original Host that the client used
            if (xForardedHost != null) {
                xForardedHost = xForardedHost.split(",", -1)[0];
            }

            if (!isValidXForwardedHost(xForardedHost)) {
                throw new IllegalArgumentException(
                        "Cannot calculate proxy uri without HTTP header " + X_FORWARDED_HOST);
            }
        }

        String scheme = request.getHeader(X_FORWARDED_PROTO);
        if (StringUtils.isBlank(scheme)) {
            throw new IllegalArgumentException("Scheme not provided");
        }

        URI proxyHost = new URI("ignored://" + xForardedHost);
        String host = proxyHost.getHost();
        int port = proxyHost.getPort();
        return new URI(scheme, null, host, port, URL_PATH_HELPER.getContextPath(request), null,
                null);
    }

    private static boolean isValidXForwardedHost(String xForardedHost) {
        return StringUtils.isNotBlank(xForardedHost)
                && !StringUtil.equalsIgnoreCase("null", xForardedHost);
    }

    private static URI calculateNonProxyUri(HttpServletRequest request) throws ExecutionException {
        try {
            URL url = StringUtil.parseURL(request.getRequestURL().toString());
            String host = url.getHost();
            String scheme = url.getProtocol();
            int port = url.getPort();
            String userInfo = url.getUserInfo();
            return new URI(scheme, userInfo, host, port, URL_PATH_HELPER.getContextPath(request),
                    null, null);
        } catch (MalformedURLException | URISyntaxException e) {
            throw new ExecutionException("URL analysis failed.", e);
        }
    }

}
