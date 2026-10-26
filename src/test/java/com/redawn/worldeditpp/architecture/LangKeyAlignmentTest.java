package com.redawn.worldeditpp.architecture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Both shipped languages must expose exactly the same keys. */
class LangKeyAlignmentTest {
    private static final Path LANG = Path.of("src/main/resources/assets/worldeditpp/lang");
    private static final Pattern KEY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:");

    @Test
    void englishAndChineseKeysMatchExactly() throws Exception {
        Set<String> en = keys("en_us.json");
        Set<String> zh = keys("zh_cn.json");
        assertEquals(en, zh);
    }

    private static Set<String> keys(String file) throws Exception {
        Set<String> keys = new TreeSet<>();
        Matcher matcher = KEY.matcher("");
        for (String line : Files.readAllLines(LANG.resolve(file))) {
            matcher.reset(line);
            if (matcher.find()) keys.add(matcher.group(1));
        }
        return keys;
    }
}
