package app.logis.core.geo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a tiny IPv4 MaxMind DB file (format 2.0, 24-bit records) for tests, with city records shaped like DB-IP
 * Lite: {@code country.iso_code}, {@code country.names}, {@code city.names}. Only what the tests need.
 */
public final class MmdbTestWriter {

    private final Map<String, Map<String, Object>> networks = new LinkedHashMap<>();

    /** Adds an IPv4 network such as {@code 203.0.113.0/24}. */
    public MmdbTestWriter network(String cidr, String countryCode, String countryNameDe, String cityDe) {
        var record = new LinkedHashMap<String, Object>();
        record.put("country", Map.of("iso_code", countryCode, "names", Map.of("de", countryNameDe, "en", countryCode)));
        if (cityDe != null) {
            record.put("city", Map.of("names", Map.of("de", cityDe)));
        }
        networks.put(cidr, record);
        return this;
    }

    public void write(Path file) throws IOException {
        Files.write(file, bytes());
    }

    // --- Search tree -----------------------------------------------------------------------------------

    /** A node of the binary trie; a child is either another node or a data offset (or nothing). */
    private static final class Node {
        Node[] child = new Node[2];
        Integer[] data = new Integer[2];
        int index;
    }

    byte[] bytes() throws IOException {
        var data = new ByteArrayOutputStream();
        var root = new Node();
        for (var entry : networks.entrySet()) {
            var offset = data.size();
            data.write(encode(entry.getValue()));
            var parts = entry.getKey().split("/");
            var prefix = Integer.parseInt(parts[1]);
            var ip = 0L;
            for (var octet : parts[0].split("\\.")) {
                ip = (ip << 8) | Integer.parseInt(octet);
            }
            var node = root;
            for (int bit = 0; bit < prefix; bit++) {
                var b = (int) ((ip >> (31 - bit)) & 1);
                if (bit == prefix - 1) {
                    node.data[b] = offset;
                } else {
                    if (node.child[b] == null) {
                        node.child[b] = new Node();
                    }
                    node = node.child[b];
                }
            }
        }
        var nodes = new ArrayList<Node>();
        number(root, nodes);
        var nodeCount = nodes.size();
        var out = new ByteArrayOutputStream();
        for (var node : nodes) {
            for (int b = 0; b < 2; b++) {
                long record;
                if (node.child[b] != null) {
                    record = node.child[b].index;
                } else if (node.data[b] != null) {
                    record = nodeCount + 16L + node.data[b];
                } else {
                    record = nodeCount;
                }
                out.write((int) (record >> 16) & 0xff);
                out.write((int) (record >> 8) & 0xff);
                out.write((int) record & 0xff);
            }
        }
        out.write(new byte[16]);
        out.write(data.toByteArray());
        out.write(new byte[] {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF});
        out.write("MaxMind.com".getBytes(StandardCharsets.US_ASCII));
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("binary_format_major_version", new U16(2));
        metadata.put("binary_format_minor_version", new U16(0));
        metadata.put("build_epoch", new U64(1_700_000_000L));
        metadata.put("database_type", "DBIP-City-Lite-Test");
        metadata.put("description", Map.of("en", "Logis test database"));
        metadata.put("ip_version", new U16(4));
        metadata.put("languages", List.of("de", "en"));
        metadata.put("node_count", new U32(nodeCount));
        metadata.put("record_size", new U16(24));
        out.write(encode(metadata));
        return out.toByteArray();
    }

    private static void number(Node node, List<Node> nodes) {
        node.index = nodes.size();
        nodes.add(node);
        for (var child : node.child) {
            if (child != null) {
                number(child, nodes);
            }
        }
    }

    // --- Data section ----------------------------------------------------------------------------------

    private record U16(int value) {
    }

    private record U32(long value) {
    }

    private record U64(long value) {
    }

    private static byte[] encode(Object value) throws IOException {
        var out = new ByteArrayOutputStream();
        switch (value) {
            case String s -> {
                var bytes = s.getBytes(StandardCharsets.UTF_8);
                control(out, 2, bytes.length);
                out.write(bytes);
            }
            case U16 u -> unsigned(out, 5, u.value(), 2);
            case U32 u -> unsigned(out, 6, u.value(), 4);
            case U64 u -> unsigned(out, 9, u.value(), 8);
            case Map<?, ?> map -> {
                control(out, 7, map.size());
                for (var entry : map.entrySet()) {
                    out.write(encode(entry.getKey()));
                    out.write(encode(entry.getValue()));
                }
            }
            case List<?> list -> {
                control(out, 11, list.size());
                for (var item : list) {
                    out.write(encode(item));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported " + value);
        }
        return out.toByteArray();
    }

    private static void unsigned(ByteArrayOutputStream out, int type, long value, int maxBytes) {
        var bytes = new ArrayList<Integer>();
        for (var v = value; v != 0; v >>>= 8) {
            bytes.addFirst((int) (v & 0xff));
        }
        if (bytes.size() > maxBytes) {
            throw new IllegalArgumentException("Too large: " + value);
        }
        control(out, type, bytes.size());
        bytes.forEach(out::write);
    }

    /** Control byte(s): type in the top 3 bits (extended types after it), size in the low 5 bits. */
    private static void control(ByteArrayOutputStream out, int type, int size) {
        var sizeBits = size < 29 ? size : size < 29 + 256 ? 29 : 30;
        if (type <= 7) {
            out.write((type << 5) | sizeBits);
        } else {
            out.write(sizeBits);
            out.write(type - 7);
        }
        if (sizeBits == 29) {
            out.write(size - 29);
        } else if (sizeBits == 30) {
            var rest = size - 285;
            out.write((rest >> 8) & 0xff);
            out.write(rest & 0xff);
        }
    }
}
