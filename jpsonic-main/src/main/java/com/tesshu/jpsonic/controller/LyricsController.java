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

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for the lyrics popup.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/lyrics", "/lyrics.view" })
public class LyricsController {

    @GetMapping
    protected ModelAndView get(HttpServletRequest request) {
        return new ModelAndView("lyrics", "model",
                LegacyMap
                    .of("artist", request.getParameter(Attributes.Request.ARTIST.value()), "song",
                            request.getParameter(Attributes.Request.SONG.value())));
    }
}
