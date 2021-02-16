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

package org.airsonic.player.service;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.airsonic.player.service.search.AbstractAirsonicHomeTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
public class JWTSecurityServiceTest extends AbstractAirsonicHomeTest {

    private final String key = "someKey";
    private final Algorithm algorithm = JWTSecurityService.getAlgorithm(key);
    private final JWTVerifier verifier = JWT.require(algorithm).build();

    @Autowired
    private SettingsService settingsService;

    /*
     * Originally Parameterized was used. If possible, it is better to rewrite to the new spring-method.
     */
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] { { "http://localhost:8080/jpsonic/stream?id=4", "/jpsonic/stream?id=4" },
                { "/jpsonic/stream?id=4", "/jpsonic/stream?id=4" }, });
    }

    @Test
    public void addJWTToken() {
        data().forEach(o -> {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(o[0].toString());
            settingsService.setJWTKey(key);
            JWTSecurityService service = new JWTSecurityService(settingsService);
            String actualUri = service.addJWTToken(builder).build().toUriString();
            String jwtToken = UriComponentsBuilder.fromUriString(actualUri).build().getQueryParams()
                    .getFirst(JWTSecurityService.JWT_PARAM_NAME);
            DecodedJWT verify = verifier.verify(jwtToken);
            Claim claim = verify.getClaim(JWTSecurityService.CLAIM_PATH);
            assertEquals(o[1], claim.asString());
        });
    }
}
