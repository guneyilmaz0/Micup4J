package net.guneyilmaz0.micup4j.requests.restaction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.interactions.commands.Command;
import net.guneyilmaz0.micup4j.interactions.commands.build.CommandData;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bulk replacement: only added definitions survive. Definitions are copied when added. */
public final class CommandListUpdateAction extends RestAction<List<Command>> {
    private final State state;
    /**
     * Creates the action; prefer {@code updateCommands()} on the client or a guild.
     *
     * @param client  the session
     * @param guildId the guild, or {@code null} for global commands
     */
    public CommandListUpdateAction(Micup client, String guildId) { this(new State(client, guildId)); }
    private CommandListUpdateAction(State state) { super(state::execute); this.state = state; }
    /**
     * Adds commands to the replacement set.
     *
     * @param commands the commands
     * @return this action
     */
    public CommandListUpdateAction addCommands(CommandData... commands) { return addCommands(Arrays.asList(commands)); }
    /**
     * Adds commands to the replacement set; each is copied.
     *
     * @param commands the commands; names must be unique
     * @return this action
     * @throws IllegalArgumentException if a name repeats within its type or the set exceeds 100 commands
     */
    public CommandListUpdateAction addCommands(Collection<? extends CommandData> commands) {
        state.add(commands); return this;
    }
    private static final class State {
        private final Micup client;
        private final String guildId;
        private final List<ObjectNode> commands = new ArrayList<>();
        private boolean submitted;
        State(Micup client, String guildId) { this.client = Objects.requireNonNull(client); this.guildId = guildId == null ? null : Json.id(guildId); }
        synchronized void add(Collection<? extends CommandData> values) {
            if (submitted) throw new IllegalStateException("Command action has already been submitted");
            var next = new ArrayList<>(commands);
            for (var value : values) next.add(value.toJson().deepCopy());
            if (next.size() > 100) throw new IllegalArgumentException("A command scope supports at most 100 commands");
            var keys = new HashSet<String>();
            for (var value : next) if (!keys.add(value.path("type").asInt() + ":" + value.path("name").asText()))
                throw new IllegalArgumentException("Command names must be unique within their type");
            commands.clear(); commands.addAll(next);
        }
        synchronized CompletableFuture<List<Command>> execute() {
            submitted = true;
            var body = Json.MAPPER.createArrayNode(); commands.forEach(value -> body.add(value.deepCopy()));
            String path = client.commandPath(guildId);
            return client.getRestClient().request("PUT", path, body).map(data -> {
                if (!data.isArray()) throw new IllegalStateException("Invalid command collection response");
                var result = new ArrayList<Command>(); for (var entry : data) result.add(Command.fromJson(entry));
                return List.copyOf(result);
            }).submit();
        }
    }
}
