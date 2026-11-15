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
 * (C) 2026 tesshucom
 */

package com.tesshu.jpsonic.feature.auth.rememberme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.annotation.Documented;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.tesshu.jpsonic.domain.model.AuthKey;
import com.tesshu.jpsonic.domain.model.AuthKey.AuthKeyType;
import com.tesshu.jpsonic.domain.repository.AuthKeyRepository;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.NeedsDB;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "jpsonic.feature.security=false")
@ActiveProfiles("test")
@NeedsDB
@SuppressWarnings("PMD.TooManyStaticImports")
class RememberMeKeyManagerTest {

    @Resource
    private SettingsFacade settingsFacade;
    @Resource
    private AuthKeyRepository authKeyRepository;

    private RememberMeKeyManager keyManager;

    @BeforeEach
    void setup() {
        authKeyRepository.remove(AuthKeyType.REMEMBERME);
        keyManager = new RememberMeKeyManager(settingsFacade, authKeyRepository);
    }

    @Test
    void initCreatesKeyWhenNotExists() {
        settingsFacade.commit(RMSKeys.enable, false);
        keyManager.init();

        AuthKey stored = authKeyRepository.get(AuthKeyType.REMEMBERME);
        assertNotNull(stored);
        assertTrue(keyManager.isRunning());
    }

    @Test
    void initUsesExistingKey() {
        settingsFacade.commit(RMSKeys.enable, true);
        String existing = "existingKey";
        authKeyRepository.create(AuthKeyType.REMEMBERME, existing, Instant.now());

        keyManager.init();

        assertEquals(existing, keyManager.getKey());
    }

    @Test
    void testGetKeyThrowsWhenDisabled() {
        settingsFacade.commit(RMSKeys.enable, false);
        keyManager.init();

        assertThrows(IllegalAccessError.class, keyManager::getKey);
    }

    @Test
    void testGetKeyReturnsWhenEnabledAndRunning() {
        settingsFacade.commit(RMSKeys.enable, true);
        keyManager.init();

        String key = keyManager.getKey();

        assertNotNull(key);
    }

    @Test
    void rotateUpdatesKeyWhenActive() {
        settingsFacade.commit(RMSKeys.enable, true);
        keyManager.init();

        String before = keyManager.getKey();

        keyManager.rotate();

        String after = keyManager.getKey();
        AuthKey stored = authKeyRepository.get(AuthKeyType.REMEMBERME);

        assertNotEquals(before, after);
        assertEquals(after, stored.value());
    }

    @Test
    void rotateDoesNothingWhenDisabled() {
        settingsFacade.commit(RMSKeys.enable, false);
        keyManager.init();

        AuthKey before = authKeyRepository.get(AuthKeyType.REMEMBERME);

        keyManager.rotate();

        AuthKey after = authKeyRepository.get(AuthKeyType.REMEMBERME);

        assertEquals(before.value(), after.value());
    }

    @Test
    void rotateDoesNothingWhenStopped() {
        settingsFacade.commit(RMSKeys.enable, true);
        keyManager.init();
        keyManager.stop();

        AuthKey before = authKeyRepository.get(AuthKeyType.REMEMBERME);

        keyManager.rotate();

        AuthKey after = authKeyRepository.get(AuthKeyType.REMEMBERME);

        assertEquals(before.value(), after.value());
    }

    @Test
    void testGetKeyThrowsWhenStopped() {
        settingsFacade.commit(RMSKeys.enable, true);
        keyManager.init();
        keyManager.stop();

        assertThrows(IllegalAccessError.class, keyManager::getKey);
    }

    @Documented
    @SuppressWarnings("PMD.ClassNamingConventions")
    private @interface initDecisions {
        @interface Conditions {
            @interface Settings {
                @interface enable {
                    @interface TRUE {
                    }

                    @interface FALSE {
                    }
                }

                @interface rotationType {
                    @interface PERIOD_OR_FIXED {
                    }

                    @interface RESTART {
                    }
                }
            }

            @interface DbState {
                @interface EXISTS {
                }

                @interface NOT_EXISTS {
                }
            }
        }

        @interface Result {
            @interface Action {
                @interface LOAD_EXISTING {
                }

                @interface ROTATE_IMMEDIATELY {
                }

                @interface CREATE_INITIAL {
                }
            }
        }
    }

    @Nested
    @SuppressWarnings("PMD.SingularField")
    class InitTest {

        @Mock
        private AuthKeyRepository authKeyRepository;
        private RememberMeKeyManager manager;

        @BeforeEach
        void setup() {
            MockitoAnnotations.openMocks(this);
        }

        /**
         * i01: Globally disabled. Expected: Just load the existing key without any side
         * effects.
         */
        @initDecisions.Conditions.Settings.enable.FALSE
        @initDecisions.Conditions.DbState.EXISTS
        @initDecisions.Result.Action.LOAD_EXISTING
        @Test
        void i01() {
            SettingsFacade settings = SettingsFacadeBuilder
                .create()
                .withBoolean(RMSKeys.enable, false)
                .buildWithDefault();

            String existingKey = "disabled-but-exists";
            when(authKeyRepository.get(any(AuthKeyType.class)))
                .thenReturn(new AuthKey(AuthKeyType.REMEMBERME, existingKey, Instant.now()));

            manager = new RememberMeKeyManager(settings, authKeyRepository);
            manager.init();

            assertThat(manager.isRunning()).isTrue();
            verify(authKeyRepository, never())
                .update(any(AuthKeyType.class), anyString(), any(Instant.class));
        }

        /**
         * i02: Periodic mode with existing key. Expected: Key is loaded as-is (Support
         * for 'Life after restart').
         */
        @initDecisions.Conditions.Settings.enable.TRUE
        @initDecisions.Conditions.Settings.rotationType.PERIOD_OR_FIXED
        @initDecisions.Conditions.DbState.EXISTS
        @initDecisions.Result.Action.LOAD_EXISTING
        @Test
        void i02() {
            SettingsFacade settings = SettingsFacadeBuilder
                .create()
                .withBoolean(RMSKeys.enable, true)
                .withInt(RMSKeys.rotationType, KeyRotationType.PERIOD.value())
                .build();

            String existingKey = "existing-key";
            when(authKeyRepository.get(any(AuthKeyType.class)))
                .thenReturn(new AuthKey(AuthKeyType.REMEMBERME, existingKey, Instant.now()));

            manager = new RememberMeKeyManager(settings, authKeyRepository);
            manager.init();

            assertThat(manager.getKey()).isEqualTo(existingKey);
            verify(authKeyRepository, never())
                .update(any(AuthKeyType.class), anyString(), any(Instant.class));
            verify(authKeyRepository, never())
                .create(any(AuthKeyType.class), anyString(), any(Instant.class));
        }

        /**
         * i03: RESTART mode with existing key. Expected: Immediate rotation (Force
         * logout on every boot).
         */
        @initDecisions.Conditions.Settings.enable.TRUE
        @initDecisions.Conditions.Settings.rotationType.RESTART
        @initDecisions.Conditions.DbState.EXISTS
        @initDecisions.Result.Action.ROTATE_IMMEDIATELY
        @Test
        void i03() {
            SettingsFacade settings = SettingsFacadeBuilder
                .create()
                .withBoolean(RMSKeys.enable, true)
                .withInt(RMSKeys.rotationType, KeyRotationType.RESTART.value())
                .build();

            when(authKeyRepository.get(AuthKeyType.REMEMBERME))
                .thenReturn(new AuthKey(AuthKeyType.REMEMBERME, "old-key", Instant.now()),
                        new AuthKey(AuthKeyType.REMEMBERME, "new-key", Instant.now()));

            manager = new RememberMeKeyManager(settings, authKeyRepository);
            manager.init();

            // Should be rotated, so key must not be "old-key"
            assertThat(manager.getKey()).isNotEqualTo("old-key");
            assertThat(manager.getKey()).isEqualTo("new-key");
            verify(authKeyRepository).update(any(AuthKeyType.class), anyString(), any(Instant.class));
        }

        /**
         * i04: Brand new installation (No key in DB). Expected: Create initial key.
         */
        @initDecisions.Conditions.DbState.NOT_EXISTS
        @initDecisions.Result.Action.CREATE_INITIAL
        @Test
        void i04() {
            SettingsFacade settings = SettingsFacadeBuilder.create().buildWithDefault();
            when(authKeyRepository.get(AuthKeyType.REMEMBERME)).thenReturn(null);
            manager = new RememberMeKeyManager(settings, authKeyRepository);
            manager.init();

            assertThat(manager.getKey()).isNotNull();

            verify(authKeyRepository).create(eq(AuthKeyType.REMEMBERME), anyString(), any(Instant.class));
        }
    }

    @Documented
    @SuppressWarnings("PMD.ClassNamingConventions")
    private @interface rotateIfNecessaryDecisions {
        @interface Conditions {
            @interface Settings {
                @interface enable {
                    @interface TRUE {
                    }

                    @interface FALSE {
                    }
                }

                @interface rotationType {
                    @interface PERIOD {
                    }

                    @interface RESTART {
                    }

                    @interface FIXED {
                    }
                }

                @interface rotationPeriod {
                    @interface DAILY {
                    }
                }
            }

            @interface State {
                @interface time {
                    @interface GE_THRESHOLD {
                    }

                    @interface LT_THRESHOLD {
                    }
                }
            }
        }

        @interface Result {
            @interface Action {
                @interface ROTATE {
                }

                @interface SKIP {
                }
            }
        }
    }

    @Nested
    class RotateIfNecessaryTest {

        @Mock
        private AuthKeyRepository authKeyRepository;
        private RememberMeKeyManager manager;

        @BeforeEach
        void setup() {
            MockitoAnnotations.openMocks(this);
        }

        private void initManager(SettingsFacade settings, Instant lastUpdate) {
            when(authKeyRepository.get(AuthKeyType.REMEMBERME))
                .thenReturn(new AuthKey(AuthKeyType.REMEMBERME, "old-key", lastUpdate));
            manager = new RememberMeKeyManager(settings, authKeyRepository);
            manager.init();
            clearInvocations(authKeyRepository);
        }

        /**
         * r01: PERIOD mode, time has elapsed -> Expected: ROTATE
         */
        @rotateIfNecessaryDecisions.Conditions.Settings.enable.TRUE
        @rotateIfNecessaryDecisions.Conditions.Settings.rotationType.PERIOD
        @rotateIfNecessaryDecisions.Conditions.Settings.rotationPeriod.DAILY
        @rotateIfNecessaryDecisions.Conditions.State.time.GE_THRESHOLD
        @rotateIfNecessaryDecisions.Result.Action.ROTATE
        @Test
        void r01() {
            SettingsFacade settings = SettingsFacadeBuilder
                .create()
                .withBoolean(RMSKeys.enable, true)
                .withInt(RMSKeys.rotationType, KeyRotationType.PERIOD.value())
                .withInt(RMSKeys.rotationPeriod, KeyRotationPeriod.DAILY.value())
                .build();

            // 24h + 1m elapsed
            Instant lastUpdate = LocalDate
                .now()
                .minusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
            Instant now = lastUpdate.plus(24, ChronoUnit.HOURS).plusSeconds(60);

            initManager(settings, lastUpdate);
            manager.rotateIfNecessary(now);

            verify(authKeyRepository, times(1))
                .update(any(AuthKeyType.class), anyString(), any(Instant.class));
        }

        /**
         * r02: PERIOD mode, time not yet elapsed -> Expected: SKIP
         */
        @rotateIfNecessaryDecisions.Conditions.Settings.enable.TRUE
        @rotateIfNecessaryDecisions.Conditions.Settings.rotationType.PERIOD
        @rotateIfNecessaryDecisions.Conditions.State.time.LT_THRESHOLD
        @rotateIfNecessaryDecisions.Result.Action.SKIP
        @Test
        void r02() {
            SettingsFacade settings = SettingsFacadeBuilder
                .create()
                .withBoolean(RMSKeys.enable, true)
                .withInt(RMSKeys.rotationType, KeyRotationType.PERIOD.value())
                .withInt(RMSKeys.rotationPeriod, KeyRotationPeriod.DAILY.value())
                .build();

            // Only 1 hour elapsed
            Instant lastUpdate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant now = lastUpdate.plus(1, ChronoUnit.HOURS);

            initManager(settings, lastUpdate);
            manager.rotateIfNecessary(now);

            verify(authKeyRepository, never())
                .update(any(AuthKeyType.class), anyString(), any(Instant.class));
        }

        /**
         * r03: RESTART mode, time elapsed -> Expected: SKIP (Scheduler should ignore)
         */
        @rotateIfNecessaryDecisions.Conditions.Settings.enable.TRUE
        @rotateIfNecessaryDecisions.Conditions.Settings.rotationType.RESTART
        @rotateIfNecessaryDecisions.Result.Action.SKIP
        @Test
        void r03() {
            SettingsFacade settings = SettingsFacadeBuilder
                .create()
                .withBoolean(RMSKeys.enable, true)
                .withInt(RMSKeys.rotationType, KeyRotationType.RESTART.value())
                .build();

            Instant wayPast = Instant.now().minus(100, ChronoUnit.DAYS);
            initManager(settings, wayPast);

            manager.rotateIfNecessary(Instant.now());

            verify(authKeyRepository, never())
                .update(any(AuthKeyType.class), anyString(), any(Instant.class));
        }

        /**
         * r04: FIXED mode, time elapsed -> Expected: SKIP
         */
        @rotateIfNecessaryDecisions.Conditions.Settings.enable.TRUE
        @rotateIfNecessaryDecisions.Conditions.Settings.rotationType.FIXED
        @rotateIfNecessaryDecisions.Result.Action.SKIP
        @Test
        void r04() {
            SettingsFacade settings = SettingsFacadeBuilder
                .create()
                .withBoolean(RMSKeys.enable, true)
                .withInt(RMSKeys.rotationType, KeyRotationType.FIXED.value())
                .build();

            Instant wayPast = Instant.now().minus(100, ChronoUnit.DAYS);
            initManager(settings, wayPast);

            manager.rotateIfNecessary(Instant.now());

            verify(authKeyRepository, never())
                .update(any(AuthKeyType.class), anyString(), any(Instant.class));
        }

        /**
         * r05: Globally disabled -> Expected: SKIP
         */
        @rotateIfNecessaryDecisions.Conditions.Settings.enable.FALSE
        @rotateIfNecessaryDecisions.Result.Action.SKIP
        @Test
        void r05() {
            SettingsFacade settings = SettingsFacadeBuilder
                .create()
                .withBoolean(RMSKeys.enable, false)
                .build();

            Instant wayPast = Instant.now().minus(100, ChronoUnit.DAYS);
            initManager(settings, wayPast);

            manager.rotateIfNecessary(Instant.now());

            verify(authKeyRepository, never())
                .update(any(AuthKeyType.class), anyString(), any(Instant.class));
        }
    }
}
