package cz.forgebound;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reading the project's files in tests (Maven runs tests from the project folder). */
final class TestFiles {

    static final Path ROOT = Path.of("").toAbsolutePath();
    static final Path RESOURCES = ROOT.resolve("src/main/resources");
    static final Path PACK = ROOT.resolve("resourcepack");

    private TestFiles() {
    }

    static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** YAML - and JSON, which is (almost) YAML too. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(Path file) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(64 * 1024 * 1024);
        Object data = new Yaml(options).load(read(file));
        return data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    static List<Object> list(Object o) {
        return o instanceof List<?> l ? new ArrayList<>(l) : List.of();
    }

    static List<Path> files(Path dir, String suffix) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix)).sorted().toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** "customcraft:item/echo_blade" -> assets/customcraft/<folder>/item/echo_blade<suffix> */
    static Path asset(String ref, String folder, String suffix) {
        String ns = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : "minecraft";
        String path = ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref;
        return PACK.resolve("assets").resolve(ns).resolve(folder).resolve(path + suffix);
    }
}
