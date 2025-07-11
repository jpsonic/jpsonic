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
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic;

import java.util.Arrays;

import org.eclipse.jetty.server.ServerConnector;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;

public final class JettyApplicationHelper {

    private JettyApplicationHelper() {
    }

    public static void configure(JettyServletWebServerFactory factory) {
        factory.addServerCustomizers(server -> {
            Arrays
                .stream(server.getConnectors())
                .filter(c -> c instanceof ServerConnector)
                .forEach(c -> {
                    ServerConnector serverConnector = (ServerConnector) c;
                    serverConnector.setIdleTimeout(300_000);
                });
        });
    }
}
