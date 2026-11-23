package com.github.giannigr.racmc.asset.pda;

import com.github.giannigr.racmc.asset.*;
import java.util.List;

/** Local-only original resources; no commerce, kiosk or third-party art. */
public record Rac1PdaAssets(List<Rac1Texture> icons,Rac1AnimatedAsset handheld) {
    public static final int ITEM=32, CLASS=619, ICON=60032;
    public Rac1PdaAssets {
        icons=List.copyOf(icons);
        if (icons.isEmpty() || handheld.classNumber()!=CLASS || handheld.mesh().positions().length==0
                || handheld.textures().length==0) throw new IllegalArgumentException("Incomplete original PDA resources");
    }
}
