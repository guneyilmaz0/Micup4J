package net.guneyilmaz0.micup4j;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.config.CachePolicy;
import net.guneyilmaz0.micup4j.config.GatewayConfig;
import net.guneyilmaz0.micup4j.entities.*;
import net.guneyilmaz0.micup4j.events.*;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;
import net.guneyilmaz0.micup4j.internal.Json;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class MentionsCompatibilityTest extends GatewayFixture {
    Micup connect(MicupBuilder builder, ListenerAdapter listener) throws Exception {
        client = builder.setInstanceOrigin(server.url("/").uri()).setErrorHandler(errors::add).addEventListeners(listener).build();
        client.awaitReady(Duration.ofSeconds(5)); return client;
    }
    ListenerAdapter quiet() { return new ListenerAdapter() {}; }

    @Test void mentionsUseActivePayloadInTextOrderAndOnlyResolveCachedMembers() throws Exception {
        start(quiet());
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        var data = Json.parse("""
                {"id":"30","channel_id":"8","guild_id":"1","author":{"id":"4","username":"Author"},
                 "content":"<@5> <@!4> <@5> <@999> <@&2> <#8>","mention_everyone":true,
                 "mentions":[{"id":"4","username":"Four"},{"id":"5","username":"Five"}],
                 "users":[{"id":"999","username":"Not an active mention"}],"mention_roles":["2"],
                 "mention_channels":[{"id":"8","name":"chat","type":0}]}
                """);
        var message = new Message(client, data);
        ((com.fasterxml.jackson.databind.node.ObjectNode) data.path("mentions").get(0)).put("username", "Changed");
        var mentions = message.getMentions();
        assertEquals(List.of("5", "4"), mentions.getUsers().stream().map(User::getId).toList());
        assertEquals("Four", mentions.getUsers().getLast().getName());
        assertEquals(List.of("4"), mentions.getMembers().stream().map(Member::getId).toList());
        assertEquals("2", mentions.getRoles().getFirst().getId()); assertEquals("8", mentions.getChannels().getFirst().getId());
        assertTrue(mentions.mentionsEveryone()); assertFalse(mentions.isMentioned(new User("999", "Unknown", false)));
        assertThrows(UnsupportedOperationException.class, () -> mentions.getUsers().clear());
        assertEquals(2, server.getRequestCount(), "Mention getters must not issue HTTP");
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String id = request.getPath().substring(request.getPath().lastIndexOf('/') + 1);
                return json("{\"user\":{\"id\":\"" + id + "\",\"username\":\"Retrieved\"},\"roles\":[\"2\"]}");
            }
        });
        var retrieved = mentions.retrieveMembers().submit().get(3, TimeUnit.SECONDS);
        assertEquals(List.of("5", "4"), retrieved.stream().map(Member::getId).toList());
        assertEquals("/public/v1/guilds/1/members/5", route("/members/5").getPath()); route("/members/4");
        assertNull(client.getGuildById("1").getMemberById("5"), "HTTP retrieval must not overwrite Gateway cache state");
    }

    @Test void directAndGroupMessagesDoNotInventGuildMemberships() throws Exception {
        start(quiet());
        var event = new net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent(client, 2, Json.parse("""
                {"id":"30","channel_id":"8","channel_type":3,"author":{"id":"4","username":"Caller"},
                 "mentions":[{"id":"5","username":"Target"}],"content":"<@5>","mention_roles":["2"]}
                """));
        assertEquals(net.guneyilmaz0.micup4j.entities.channel.ChannelType.GROUP, event.getChannelType());
        assertFalse(event.isFromGuild()); assertNull(event.getMember());
        assertEquals(1, event.getMessage().getMentions().getUsers().size());
        assertTrue(event.getMessage().getMentions().getMembers().isEmpty());
        assertTrue(event.getMessage().getMentions().getRoles().isEmpty());
        assertTrue(event.getMessage().getMentions().retrieveMembers().complete().isEmpty());
        assertEquals(2, server.getRequestCount());
    }

    @Test void cancellationStopsExplicitMentionMemberRetrievalBeforeTheNextUser() throws Exception {
        start(quiet());
        var message = new Message(client, Json.parse("""
                {"id":"30","channel_id":"8","guild_id":"1","author":{"id":"4"},"content":"<@4> <@5>",
                 "mentions":[{"id":"4"},{"id":"5"}]}
                """));
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                return json(EntityCacheTest.member("4")).setHeadersDelay(250, TimeUnit.MILLISECONDS);
            }
        });
        var result = message.getMentions().retrieveMembers().submit();
        route("/members/4"); assertTrue(result.cancel(true));
        assertNull(server.takeRequest(400, TimeUnit.MILLISECONDS));
    }

    @Test void memberColorsKeepPrimaryAndGradientFromTheHighestAssignedColoredRole() throws Exception {
        start(quiet());
        var guild = (com.fasterxml.jackson.databind.node.ObjectNode) EntityCacheTest.snapshot().deepCopy();
        var role = (com.fasterxml.jackson.databind.node.ObjectNode) guild.path("roles").get(1);
        role.put("color", 0x3498DB).put("color_secondary", 0);
        guild.withArray("roles").add(Json.parse(EntityCacheTest.role("3", 2)));
        ((com.fasterxml.jackson.databind.node.ObjectNode) guild.path("members").get(0)).withArray("roles").add("3");
        client.dispatch("GUILD_CREATE", 2, guild).get(3, TimeUnit.SECONDS);
        var member = client.getGuildById("1").getMemberById("4");
        assertEquals(0x3498DB, member.getColors().getPrimary().getRGB() & 0xFFFFFF);
        assertEquals(0, member.getColors().getSecondary().getRGB() & 0xFFFFFF);
        assertTrue(member.getColors().isGradient());
        assertEquals(member.getColor(), member.getColors().getPrimary());
        assertTrue(RoleColors.DEFAULT.isDefault()); assertNull(RoleColors.DEFAULT.getPrimary());
    }

    @Test void bmoStyleAvatarBodyUsesMentionedMemberColorsAndDiscoveredAvatarUrls() throws Exception {
        start(quiet());
        var snapshot = (com.fasterxml.jackson.databind.node.ObjectNode) EntityCacheTest.snapshot().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) snapshot.path("roles").get(1)).put("color", 0x3498DB);
        ((com.fasterxml.jackson.databind.node.ObjectNode) snapshot.path("members").get(0).path("user")).put("avatar", "caller_hash");
        snapshot.withArray("members").add(Json.parse("{\"user\":{\"id\":\"5\",\"username\":\"Target\",\"avatar\":\"a_target_hash\"},\"roles\":[\"2\"]}"));
        client.dispatch("GUILD_CREATE", 2, snapshot).get(3, TimeUnit.SECONDS);
        var member = client.getGuildById("1").getMemberById("4");
        var message = new Message(client, Json.parse("{\"id\":\"30\",\"channel_id\":\"8\",\"guild_id\":\"1\",\"author\":{\"id\":\"4\"},\"content\":\"!avatar <@5>\",\"mentions\":[{\"id\":\"5\"}]}"));
        Member target = member;
        if (!message.getMentions().getMembers().isEmpty()) target = message.getMentions().getMembers().getFirst();
        message.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setAuthor(target.getUser().getName(), null, target.getUser().getAvatarUrl())
                .setTitle("Link", target.getUser().getAvatarUrl() + "?size=1024")
                .setColor(target.getColors().getPrimary()).setImage(target.getUser().getAvatarUrl() + "?size=1024")
                .setFooter(member.getUser().getName() + " requested this", member.getUser().getAvatarUrl()).build()).submit().get(3, TimeUnit.SECONDS);
        var embed = Json.parse(route("/messages").getBody().readUtf8()).path("embeds").get(0);
        assertEquals("Target", embed.path("author").path("name").asText());
        assertEquals(0x3498DB, embed.path("color").asInt());
        assertEquals(server.url("/proxy/avatars/5/a_target_hash.gif?size=1024").toString(), embed.path("image").path("url").asText());
    }

}
