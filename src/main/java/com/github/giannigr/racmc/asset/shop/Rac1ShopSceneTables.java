package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.MobyClassReader;
import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.disc.DiscFormatException;
import java.util.*;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;

/** Converted source tables only. Importing item15 is not authorization to sell it. */
public final class Rac1ShopSceneTables {
    private final Rac1ShopLedFont led;
    private final int[] tickerIds;
    private final float[] margins;
    private final float[] blasterPanel;
    private final float[] blasterHolograms;
    private final int ammoClass;
    private final int[] monitorTargets;
    private final float vendorPackedScale;

    public Rac1ShopSceneTables(Rac1ShopLedFont led, int[] tickerIds, float[] margins, float[] blasterPanel,
                               float[] blasterHolograms, int ammoClass, int[] monitorTargets, float vendorPackedScale) {
        this.led = Objects.requireNonNull(led);
        if (tickerIds.length != 24 || margins.length != 24 || blasterPanel.length != 8 || blasterHolograms.length != 20
                || ammoClass != 1006 || monitorTargets.length != 4)
            throw new IllegalArgumentException("Invalid original shop scene table counts");
        for (int id : tickerIds) if (id < 20000 || id > 22000)
            throw new IllegalArgumentException("Invalid original ticker string reference");
        for (float v : margins) if (!Float.isFinite(v) || v < 0 || v > .25f)
            throw new IllegalArgumentException("Invalid original monitor margin");
        for (float v : blasterPanel) if (!Float.isFinite(v) || Math.abs(v) > 100)
            throw new IllegalArgumentException("Invalid original product panel vector");
        for (float v : blasterHolograms) if (!Float.isFinite(v) || Math.abs(v) > 100)
            throw new IllegalArgumentException("Invalid original product hologram vector");
        this.tickerIds = tickerIds.clone(); this.margins = margins.clone(); this.blasterPanel = blasterPanel.clone();
        this.blasterHolograms = blasterHolograms.clone(); this.ammoClass = ammoClass;
        for (int target : monitorTargets) if (target < 0 || target >= 256)
            throw new IllegalArgumentException("Invalid monitor manipulator target");
        this.monitorTargets = monitorTargets.clone();
        if (!Float.isFinite(vendorPackedScale) || vendorPackedScale <= 0 || vendorPackedScale > 1000)
            throw new IllegalArgumentException("Invalid original vendor packed scale");
        this.vendorPackedScale = vendorPackedScale;
    }
    public Rac1ShopLedFont led() { return led; }
    public int[] tickerIds() { return tickerIds.clone(); }
    public float[] margins() { return margins.clone(); }
    /** rotation xyzw then position xyzw; position.w is the original extra weapon Z offset. */
    public float[] blasterPanel() { return blasterPanel.clone(); }
    /** ammo rotation/position; weapon local shift/rotation/position, four floats each. */
    public float[] blasterHolograms() { return blasterHolograms.clone(); }
    public int ammoClass() { return ammoClass; }
    public int[] monitorTargets() { return monitorTargets.clone(); }
    public float vendorPackedScale() { return vendorPackedScale; }
    public static Rac1ShopSceneTables read(byte[] overlay, boolean pal, Rac1LevelAssets level)
            throws AssetFormatException {
        if (level.level() != 1) throw new AssetFormatException("Shop manipulator source must be Novalis");
        try {
            var entry = level.coreIndex().mobyClass(11);
            if (entry == null || entry.offset() <= 0) throw new AssetFormatException("Missing vendor manipulator source");
            var reader = MobyClassReader.of(level.classBlob(entry.offset()));
            var lists = reader.readJointLists();
            if (lists.size() < 24) throw new AssetFormatException("Missing vendor manipulator joint lists");
            int[] targets = new int[4];
            for (int i = 0; i < 4; i++) {
                byte[] target = lists.get(20+i).target();
                if (target.length == 0 || (target[0] & 255) >= reader.jointCount())
                    throw new AssetFormatException("Invalid original vendor manipulator target");
                targets[i] = target[0] & 255;
            }
            return read(overlay,pal,targets,reader.scale());
        } catch (DiscFormatException failure) { throw new AssetFormatException(failure.getMessage(),failure); }
    }
    public Set<Integer> requiredTextIds() {
        Set<Integer> ids = new TreeSet<>(Rac1ShopText.REQUIRED);
        for (int id : tickerIds) ids.add(id);
        return Set.copyOf(ids);
    }
    public static Rac1ShopSceneTables read(byte[] novalisOverlay, boolean pal, int[] verifiedManipulatorTargets,
                                           float verifiedVendorPackedScale)
            throws AssetFormatException {
        // P35 direct both-disc unique byte-equality probe: all these table bases relocate +0x280.
        int delta = pal ? 0x280 : 0;
        var led = Rac1ShopLedFont.read(
                Rac1HudImporter.overlayRange(novalisOverlay, 0x1ca598 + delta, 256),
                Rac1HudImporter.overlayRange(novalisOverlay, 0x1ca698 + delta, 256));
        byte[] refs = Rac1HudImporter.overlayRange(novalisOverlay, 0x1ca538 + delta, 96);
        byte[] inset = Rac1HudImporter.overlayRange(novalisOverlay, 0x1ca798 + delta, 96);
        // 48-byte item-panel records, not 32. Independently matches documented item16 example.
        byte[] panel = Rac1HudImporter.overlayRange(novalisOverlay, 0x1c8d90 + delta + 15 * 48, 32);
        // The hologram records are64 bytes. Source class1006 at item15 and class214 at
        // item16 independently distinguish this from an incorrect48-byte interpretation.
        byte[] ammo = Rac1HudImporter.overlayRange(novalisOverlay, 0x1c9480 + delta + 15 * 64, 36);
        byte[] weapon = Rac1HudImporter.overlayRange(novalisOverlay, 0x1c9db0 + delta + 15 * 64, 48);
        int[] ids = new int[24]; float[] margins = new float[24], placement = new float[8];
        for (int i = 0; i < 24; i++) { ids[i] = word(refs, i * 4); margins[i] = Float.intBitsToFloat(word(inset, i * 4)); }
        for (int i = 0; i < 8; i++) placement[i] = Float.intBitsToFloat(word(panel, i * 4));
        float[] holograms = new float[20];
        for (int i = 0; i < 8; i++) holograms[i] = Float.intBitsToFloat(word(ammo, i * 4));
        for (int i = 0; i < 12; i++) holograms[8 + i] = Float.intBitsToFloat(word(weapon, i * 4));
        try { return new Rac1ShopSceneTables(led, ids, margins, placement, holograms, word(ammo, 32),
                verifiedManipulatorTargets,verifiedVendorPackedScale); }
        catch (IllegalArgumentException failure) { throw new AssetFormatException(failure.getMessage(), failure); }
    }
}
