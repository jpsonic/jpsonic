package com.tesshu.jpsonic.service;

import static org.junit.Assert.assertNotNull;

import com.tesshu.jpsonic.domain.provider.resource.UserProvider;
import com.tesshu.jpsonic.persistence.NeedsDB;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@NeedsDB
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserProvider userProvider;

    @Test
    void testGetUserByNameString() {
        User admin = userService.getUserByName("admin");
        assertNotNull(admin);
        assertNotNull(userService.getUserByNameStrict(admin.getUsername()));
        UserSettings userSettings = userService.getUserSettings(admin.getUsername());
        assertNotNull(userSettings);
        assertNotNull(userSettings.getBitRateLimit());
    }

    @Test
    void testGetDomainUserSettings() {
        User admin = userService.getUserByName("admin");
        assertNotNull(admin);
        assertNotNull(userService.getUserByNameStrict(admin.getUsername()));
        com.tesshu.jpsonic.domain.model.UserSettings userSettings = userProvider
            .getUserSettings(admin.getUsername());
        assertNotNull(userSettings);
        assertNotNull(userSettings.bitRateLimit());
    }

    @Test
    void testGetGuestUser() {
        User guest = userService.getUserByName(User.USERNAME_GUEST);
        assertNotNull(guest);
        assertNotNull(userService.getUserByNameStrict(guest.getUsername()));
        UserSettings userSettings = userService.getUserSettings(guest.getUsername());
        assertNotNull(userSettings);
        assertNotNull(userSettings.getBitRateLimit());
    }
}
