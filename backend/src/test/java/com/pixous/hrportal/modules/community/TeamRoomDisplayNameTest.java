package com.pixous.hrportal.modules.community;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Team rooms are stored under a prefixed, lower-cased name so they can be
 * found from a designation. Now that they appear in Chat beside ordinary
 * conversations, the reader must see a title rather than the naming scheme.
 */
class TeamRoomDisplayNameTest {

    private static String displayName(String stored) throws Exception {
        Method m = CommunityService.class.getDeclaredMethod("displayName", CommunityGroup.class);
        m.setAccessible(true);
        CommunityGroup g = new CommunityGroup();
        g.setName(stored);
        return (String) m.invoke(null, g);
    }

    @Test
    void aTeamRoomReadsAsATitleNotAsItsKey() throws Exception {
        assertThat(displayName("__team__ai engineer")).isEqualTo("Ai Engineer Team");
        assertThat(displayName("__team__software developer")).isEqualTo("Software Developer Team");
    }

    @Test
    void anAcronymKeepsItsCapitals() throws Exception {
        // "AI" must not be flattened to "Ai" when it was stored capitalised.
        assertThat(displayName("__team__AI")).isEqualTo("AI Team");
    }

    @Test
    void anOrdinaryGroupNameIsLeftAlone() throws Exception {
        assertThat(displayName("Announcements")).isEqualTo("Announcements");
    }
}
