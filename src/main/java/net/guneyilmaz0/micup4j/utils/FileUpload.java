package net.guneyilmaz0.micup4j.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A file attached to a new message, modelled on JDA's {@code FileUpload}. The data is read into memory when the upload
 * is created, so the same upload can be retried after a rate limit and sent more than once. Micup enforces file size
 * and attachment count limits; interaction responses and followups cannot carry files.
 */
public final class FileUpload {
    /** Longest description, in characters. */
    public static final int MAX_DESCRIPTION_LENGTH = 4096;

    private final String name;
    private final byte[] data;
    private volatile String description;
    private volatile boolean spoiler;

    private FileUpload(byte[] data, String name) {
        if (name == null || name.isBlank() || name.length() > 1024 || name.chars().anyMatch(c -> c < 0x20 || c == 0x7F || c == '/' || c == '\\'))
            throw new IllegalArgumentException("File name must be 1-1024 characters without control characters or path separators");
        this.data = Objects.requireNonNull(data, "data");
        this.name = name;
    }

    /**
     * Creates an upload from bytes.
     *
     * @param data the content, copied
     * @param name the file name shown in the client
     * @return the upload
     */
    public static FileUpload fromData(byte[] data, String name) { return new FileUpload(data.clone(), name); }

    /**
     * Creates an upload by reading a stream fully. The stream is closed.
     *
     * @param data the stream
     * @param name the file name
     * @return the upload
     * @throws UncheckedIOException if reading fails
     */
    public static FileUpload fromData(InputStream data, String name) {
        try (data) { return new FileUpload(data.readAllBytes(), name); }
        catch (IOException error) { throw new UncheckedIOException("Could not read upload data", error); }
    }

    /**
     * Creates an upload from a file, using its name.
     *
     * @param file the file
     * @return the upload
     * @throws UncheckedIOException if reading fails
     */
    public static FileUpload fromData(File file) { return fromData(file.toPath()); }

    /**
     * Creates an upload from a file, using its name.
     *
     * @param path the file
     * @return the upload
     * @throws UncheckedIOException if reading fails
     */
    public static FileUpload fromData(Path path) { return fromData(path, path.getFileName().toString()); }

    /**
     * Creates an upload from a file.
     *
     * @param path the file
     * @param name the file name shown in the client
     * @return the upload
     * @throws UncheckedIOException if reading fails
     */
    public static FileUpload fromData(Path path, String name) {
        try { return new FileUpload(Files.readAllBytes(path), name); }
        catch (IOException error) { throw new UncheckedIOException("Could not read " + path.getFileName(), error); }
    }

    /**
     * Sets the alternative text.
     *
     * @param description 1-4096 characters, or {@code null} for none
     * @return this upload
     */
    public FileUpload setDescription(String description) {
        if (description != null && (description.isEmpty() || description.codePointCount(0, description.length()) > MAX_DESCRIPTION_LENGTH))
            throw new IllegalArgumentException("Description must be 1-4096 characters");
        this.description = description;
        return this;
    }

    /**
     * Marks the attachment as a spoiler.
     *
     * @return this upload
     */
    public FileUpload asSpoiler() { spoiler = true; return this; }

    /**
     * Returns the file name shown in the client.
     *
     * @return the file name shown in the client
     */
    public String getName() { return name; }
    /**
     * Returns the alternative text, or {@code null}.
     *
     * @return the alternative text, or {@code null}
     */
    public String getDescription() { return description; }
    /**
     * Returns whether the attachment is a spoiler.
     *
     * @return whether the attachment is a spoiler
     */
    public boolean isSpoiler() { return spoiler; }
    /**
     * Returns the size in bytes.
     *
     * @return the size in bytes
     */
    public int getSize() { return data.length; }
    /**
     * Returns a copy of the content.
     *
     * @return a copy of the content
     */
    public byte[] getData() { return data.clone(); }

    /**
     * Returns the media type guessed from the name, or {@code application/octet-stream}.
     *
     * @return the media type guessed from the name, or {@code application/octet-stream}
     */
    public String getContentType() {
        String type = java.net.URLConnection.guessContentTypeFromName(name);
        return type == null ? "application/octet-stream" : type;
    }

    /**
     * Returns the raw bytes for the multipart encoder without copying. Not part of the supported public API.
     *
     * @return the content; must not be modified
     */
    public byte[] rawData() { return data; }
}
