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

package com.tesshu.jpsonic.controller;

import java.net.URI;

import com.tesshu.jpsonic.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Spring MVC Controller that serves the login page.
 */
@Controller
@RequestMapping({ "/accessDenied", "/accessDenied.view" })
public class AccessDeniedController {

    private static final Logger LOG = LoggerFactory.getLogger(AccessDeniedController.class);

    @SuppressFBWarnings(value = "CRLF_INJECTION_LOGS", justification = "False positive. find-sec-bugs#614")
    @GetMapping
    public ModelAndView get(HttpServletRequest request) {
        if (LOG.isInfoEnabled()) {
            LOG
                .info("The IP {} tried to access the forbidden url {}.",
                        URI.create(request.getRemoteAddr()),
                        URI.create(request.getRequestURL().toString()));
        }
        return new ModelAndView("accessDenied");
    }

}
