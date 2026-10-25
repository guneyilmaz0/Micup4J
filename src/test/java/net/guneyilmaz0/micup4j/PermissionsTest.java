package net.guneyilmaz0.micup4j;

import net.guneyilmaz0.micup4j.entities.*;
import net.guneyilmaz0.micup4j.permissions.*;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import static net.guneyilmaz0.micup4j.permissions.Permission.*;
import static org.junit.jupiter.api.Assertions.*;

class PermissionsTest {
    final PermissionSet send = PermissionSet.of(SEND_MESSAGES), view = PermissionSet.of(VIEW_CHANNEL);
    Role role(String id, PermissionSet mask) { return new Role("1", id, "Role", 0, 1, mask, false, false); }
    Member member(String id, String... roles) { return new Member("1", new User(id, "User", false), null, List.of(roles), null, null, false, false); }
    Guild guild(Role... roles) { return new Guild("1", "Guild", "9", true, List.of(roles), List.of(), List.of(), true, true); }
    PermissionOverwrite overwrite(String id, PermissionOverwrite.Type type, PermissionSet allow, PermissionSet deny) {
        return new PermissionOverwrite(id, type, allow, deny);
    }
    GuildChannel channel(PermissionOverwrite... overwrites) { return new GuildChannel("1", "8", "chat", 0, null, 0, List.of(overwrites)); }

    @Test void unsignedMasksPreserveHighBitsAndCompareNumerically() {
        assertEquals(PermissionSet.ALL, PermissionSet.parse("18446744073709551615"));
        assertEquals(send, PermissionSet.parse("0002048"));
        assertTrue(PermissionSet.of(VIEW_CHANNEL_MEMBERS).value().testBit(54));
        for (String invalid : List.of("-1", "+1", "1.0", "18446744073709551616", " 1"))
            assertThrows(IllegalArgumentException.class, () -> PermissionSet.parse(invalid));
        assertThrows(IllegalArgumentException.class, PermissionSet.ALL::toRequestValue);
        assertEquals("9223372036854775807", PermissionSet.parse("9223372036854775807").toRequestValue());
        assertThrows(IllegalArgumentException.class, () -> new PermissionSet(BigInteger.valueOf(-1)));
    }
    @Test void ownerAndAdministratorBypassEveryOverwrite() {
        Guild g = guild(role("1", view), role("2", PermissionSet.of(ADMINISTRATOR)));
        GuildChannel denied = channel(overwrite("1", PermissionOverwrite.Type.ROLE, PermissionSet.NONE, PermissionSet.ALL));
        assertEquals(PermissionSet.ALL, PermissionResolver.channelPermissions(g, member("9"), denied));
        assertEquals(PermissionSet.ALL, PermissionResolver.channelPermissions(g, member("3", "2"), denied));
        assertEquals(PermissionSet.NONE, PermissionResolver.channelPermissions(g, null, denied));
    }
    @Test void everyoneThenRoleUnionsThenMemberOverwrite() {
        Guild g = guild(role("1", view.union(send)), role("2", PermissionSet.NONE), role("3", PermissionSet.NONE));
        Member m = member("4", "2", "3");
        GuildChannel c = channel(
                overwrite("1", PermissionOverwrite.Type.ROLE, PermissionSet.NONE, send),
                overwrite("2", PermissionOverwrite.Type.ROLE, send, view),
                overwrite("3", PermissionOverwrite.Type.ROLE, view, send));
        assertEquals(view.union(send), PermissionResolver.channelPermissions(g, m, c), "Role allows defeat other role denies");
        c = channel(c.permissionOverwrites().get(0), c.permissionOverwrites().get(1), c.permissionOverwrites().get(2),
                overwrite("4", PermissionOverwrite.Type.MEMBER, PermissionSet.NONE, send));
        assertEquals(view, PermissionResolver.channelPermissions(g, m, c));
        assertEquals(view.union(send), PermissionResolver.channelPermissions(g, m, null));
    }
    @Test void incompleteOrForeignStateNeverProducesPermissionGuesses() {
        Guild g = guild(role("1", view));
        assertThrows(IllegalStateException.class, () -> PermissionResolver.guildPermissions(g, member("4", "2")));
        Guild incomplete = new Guild("1", "G", "9", true, g.roles(), List.of(), List.of(), false, false);
        assertThrows(IllegalStateException.class, () -> PermissionResolver.guildPermissions(incomplete, member("4")));
        Guild unavailable = new Guild("1", "G", "9", false, g.roles(), List.of(), List.of(), true, true);
        assertThrows(IllegalStateException.class, () -> PermissionResolver.guildPermissions(unavailable, member("9")));
        Member foreign = new Member("5", new User("4", "U", false), null, List.of(), null, null, false, false);
        assertThrows(IllegalArgumentException.class, () -> PermissionResolver.guildPermissions(g, foreign));
        assertThrows(IllegalStateException.class, () -> PermissionResolver.guildPermissions(guild(), member("4")));
    }
    @Test void timeoutsRemainIndependentOfMaskAndExpireAtBoundary() {
        Instant expiry = Instant.parse("2026-10-26T12:00:00Z");
        Member timed = new Member("1", new User("4", "U", false), null, List.of(), null, expiry, true, false);
        assertTrue(timed.isTimedOutAt(expiry.minusSeconds(1)));
        assertFalse(timed.isTimedOutAt(expiry));
        assertEquals(send, PermissionResolver.guildPermissions(guild(role("1", send)), timed));
    }
    @Test void roleHierarchyUsesAscendingUnsignedSnowflakeForPositionTies() {
        var first = role("18446744073709551614", send);
        var second = role("18446744073709551615", view);
        assertTrue(Role.HIERARCHY.compare(first, second) < 0);
        Role higher = new Role("1", "3", "Higher", 0, 2, view, false, false);
        assertEquals(List.of(higher, first, second), List.of(second, first, higher).stream().sorted(Role.HIERARCHY).toList());
    }
}
