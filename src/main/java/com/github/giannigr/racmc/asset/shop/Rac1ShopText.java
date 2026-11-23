package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.util.*;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.*;

/** Original byte strings, not Java/UTF-8 text. No retail strings are bundled or networked. */
public final class Rac1ShopText {
    public static final int MAX_STRING = 2048;
    public static final Set<Integer> REQUIRED = Set.of(20039, 20192, 20317, 20319, 20320,
            21043, 21044, 21046, 21048, 21067, 21070);
    private final Map<Integer, byte[]> strings;

    public Rac1ShopText(Map<Integer, byte[]> strings) {
        if (strings.size() > 128) throw new IllegalArgumentException("Too many shop strings");
        Map<Integer, byte[]> copy = new TreeMap<>();
        strings.forEach((id, bytes) -> {
            if (id < 0 || bytes.length > MAX_STRING)
                throw new IllegalArgumentException("Invalid shop string");
            for (byte b : bytes) if (b == 0) throw new IllegalArgumentException("Embedded string terminator");
            copy.put(id, bytes.clone());
        });
        this.strings = Collections.unmodifiableMap(copy);
    }

    public Set<Integer> ids() { return strings.keySet(); }
    public byte[] get(int id) {
        byte[] value = strings.get(id);
        if (value == null) throw new IllegalArgumentException("Missing original shop text ID " + id);
        return value.clone();
    }
    public boolean complete() { return strings.keySet().containsAll(REQUIRED); }

    public static int languageSlot(String locale) {
        String language = locale == null ? "" : locale.toLowerCase(Locale.ROOT).split("[_-]", 2)[0];
        return switch (language) { case "fr" -> 2; case "de" -> 3; case "es" -> 4; case "it" -> 5; default -> 0; };
    }

    /** all_text: eight block offsets; block-relative text offsets and 16-byte ID records. */
    public static Rac1ShopText read(byte[] allText, int language, Set<Integer> ids)
            throws AssetFormatException {
        if (allText.length > 8 * 1024 * 1024 || ids.size() > 128
                || !Set.of(0, 2, 3, 4, 5).contains(language))
            throw new AssetFormatException("Invalid original shop string selection");
        range(allText, 0, 32);
        int block = word(allText, language * 4);
        range(allText, block, 8);
        int count = word(allText, block), size = word(allText, block + 4);
        if (block < 32 || count < 0 || count > 20000 || size < 8L + count * 16L)
            throw new AssetFormatException("Invalid original string block");
        range(allText, block, size);
        Map<Integer, byte[]> selected = new HashMap<>();
        int textStart = 8 + count * 16;
        for (int i = 0; i < count; i++) {
            int at = block + 8 + i * 16, id = word(allText, at + 4);
            if (!ids.contains(id)) continue;
            int offset = word(allText, at);
            if (offset < textStart || offset >= size)
                throw new AssetFormatException("Original shop text offset outside string area");
            int length = 0;
            while (offset + length < size && allText[block + offset + length] != 0) {
                if (++length > MAX_STRING) throw new AssetFormatException("Oversized original shop string");
            }
            if (offset + length >= size) throw new AssetFormatException("Unterminated original shop string");
            if (selected.put(id, Arrays.copyOfRange(allText, block + offset, block + offset + length)) != null)
                throw new AssetFormatException("Duplicate original shop text ID");
        }
        if (!selected.keySet().containsAll(ids)) throw new AssetFormatException("Missing required original shop text IDs");
        return new Rac1ShopText(selected);
    }
}
