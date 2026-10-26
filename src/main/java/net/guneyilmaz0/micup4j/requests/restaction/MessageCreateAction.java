package net.guneyilmaz0.micup4j.requests.restaction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.utils.FileUpload;
import java.util.Arrays;
import java.util.Collection;

/**
 * Fluent lazy message creation with content, embeds, components and files. A message with files is sent as
 * {@code multipart/form-data} and requires {@code ATTACH_FILES}.
 */
public final class MessageCreateAction extends MessageAction<MessageCreateAction> {
    /**
     * Creates the action; prefer {@code sendMessage}, {@code sendFiles} or {@code reply}.
     *
     * @param client  the session
     * @param path    the messages route
     * @param guildId the channel's guild, or {@code null} for direct messages
     * @param body    the initial payload, copied
     */
    public MessageCreateAction(Micup client, String path, String guildId, ObjectNode body) { super(client, "POST", path, guildId, body); }
    @Override protected MessageCreateAction self() { return this; }

    /**
     * Adds files.
     *
     * @param files the files; the total must stay at most {@value #MAX_FILES}
     * @return this action
     * @throws IllegalArgumentException if too many files are attached
     * @throws IllegalStateException if the action was already submitted
     */
    public MessageCreateAction addFiles(FileUpload... files) { return addFiles(Arrays.asList(files)); }
    /**
     * Adds files.
     *
     * @param files the files; the total must stay at most {@value #MAX_FILES}
     * @return this action
     */
    public MessageCreateAction addFiles(Collection<? extends FileUpload> files) { files(files, true); return this; }

    /**
     * Replaces the files.
     *
     * @param files at most {@value #MAX_FILES} files; none removes them
     * @return this action
     */
    public MessageCreateAction setFiles(FileUpload... files) { return setFiles(Arrays.asList(files)); }
    /**
     * Replaces the files.
     *
     * @param files at most {@value #MAX_FILES} files; an empty collection removes them
     * @return this action
     */
    public MessageCreateAction setFiles(Collection<? extends FileUpload> files) { files(files, false); return this; }
}
