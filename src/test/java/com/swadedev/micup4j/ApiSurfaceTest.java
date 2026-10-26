package com.swadedev.micup4j;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks the supported public API. Every public or protected type and member outside the {@code internal} package is
 * recorded in {@code api/Micup4J.api}. An unintended change fails this test; an intended one is accepted with
 * {@code ./gradlew test --tests '*ApiSurfaceTest*' -PupdateApi} and reviewed in the diff.
 */
class ApiSurfaceTest {
    private static final Path SNAPSHOT = Path.of("api", "Micup4J.api");

    @Test void publicApiMatchesTheRecordedSnapshot() throws Exception {
        String actual = String.join("\n", surface()) + "\n";
        if (Boolean.getBoolean("api.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, actual);
            return;
        }
        assertTrue(Files.exists(SNAPSHOT), "Missing " + SNAPSHOT + "; generate it with -PupdateApi");
        var expected = new TreeSet<>(Files.readAllLines(SNAPSHOT));
        var current = new TreeSet<>(Arrays.asList(actual.split("\n")));
        var removed = new TreeSet<>(expected); removed.removeAll(current);
        var added = new TreeSet<>(current); added.removeAll(expected);
        assertTrue(removed.isEmpty() && added.isEmpty(), () -> "Public API changed; review and run with -PupdateApi.\nRemoved:\n  "
                + String.join("\n  ", removed) + "\nAdded:\n  " + String.join("\n  ", added));
    }

    static List<String> surface() throws Exception {
        Path root = Path.of(Micup.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var lines = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file).toString().replace(java.io.File.separatorChar, '.').replaceAll("\\.class$", "");
                if (name.contains(".internal.") || name.endsWith("package-info") || name.matches(".*\\$\\d.*")) continue;
                Class<?> type = Class.forName(name, false, ApiSurfaceTest.class.getClassLoader());
                if (!exported(type)) continue;
                lines.add(describe(type));
                for (Constructor<?> constructor : type.getDeclaredConstructors())
                    if (visible(constructor.getModifiers()) && !constructor.isSynthetic()) lines.add(type.getName() + " :: " + executable(constructor));
                for (Method method : type.getDeclaredMethods())
                    if (visible(method.getModifiers()) && !method.isSynthetic() && !method.isBridge()) lines.add(type.getName() + " :: " + executable(method));
                for (Field field : type.getDeclaredFields())
                    if (visible(field.getModifiers()) && !field.isSynthetic()) lines.add(type.getName() + " :: " + deprecated(field) + field.toGenericString());
            }
        }
        return new ArrayList<>(lines);
    }

    private static boolean exported(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getDeclaringClass())
            if (!Modifier.isPublic(current.getModifiers()) && !(current != type && Modifier.isProtected(current.getModifiers()))) return false;
        return !type.isAnonymousClass() && !type.isLocalClass() && !type.isSynthetic();
    }

    private static boolean visible(int modifiers) { return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers); }

    private static String deprecated(java.lang.reflect.AnnotatedElement element) { return element.isAnnotationPresent(Deprecated.class) ? "@Deprecated " : ""; }

    private static String executable(Executable executable) {
        // Parameter names are not part of binary compatibility; generic signatures and exceptions are.
        return deprecated(executable) + executable.toGenericString().replace(executable.getDeclaringClass().getName() + ".", "");
    }

    private static String describe(Class<?> type) {
        String kind = type.isAnnotation() ? "annotation" : type.isEnum() ? "enum" : type.isRecord() ? "record" : type.isInterface() ? "interface" : "class";
        int modifiers = type.getModifiers() & ~Modifier.INTERFACE & ~Modifier.ABSTRACT;
        if (kind.equals("class")) modifiers |= type.getModifiers() & Modifier.ABSTRACT;
        var line = new StringBuilder(deprecated(type)).append(Modifier.toString(modifiers)).append(' ').append(kind).append(' ').append(type.getName());
        if (type.getSuperclass() != null && type.getSuperclass() != Object.class && !type.isEnum() && !type.isRecord())
            line.append(" extends ").append(type.getGenericSuperclass().getTypeName());
        var interfaces = Arrays.stream(type.getGenericInterfaces()).map(java.lang.reflect.Type::getTypeName).sorted().toList();
        if (!interfaces.isEmpty()) line.append(type.isInterface() ? " extends " : " implements ").append(String.join(", ", interfaces));
        if (type.isSealed()) line.append(" permits ").append(String.join(", ", Arrays.stream(type.getPermittedSubclasses()).map(Class::getName).sorted().toList()));
        return line.toString();
    }
}
