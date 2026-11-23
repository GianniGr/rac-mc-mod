package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.Rac1ShopPurchases;
import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Supplemental MC text in a RESERVED footer only. Never covers the original ticker or replaces retail art. */
public final class Rac1CommerceCatalogDrawing {
    private Rac1CommerceCatalogDrawing() {}
    private static List<net.minecraft.util.FormattedCharSequence> lines(Font font,int width,Rac1CommerceSelection selection,
                                                                      Rac1ShopPurchases.Quote quote,boolean browsing) {
        var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        var text=new ArrayList<String>();
        text.add("MC selection: "+selection.label());
        if (!selection.blaster()) {
            if (!browsing && quote==null) text.add("Awaiting result / Backspace returns; no automatic purchase");
            else {
                var details=selection.details(quote);
                // label() is already above; the starter adaptation heading is a distinct required fact.
                text.addAll(details.subList(details.getFirst().equals(selection.label()) ? 1:0,details.size()));
            }
        }
        text.add(browsing ? "Left/Right: previous/next | Enter: request quote | Esc: close"
                :"Arrows: quantity in quote | Enter: explicit Yes | Backspace: No | Esc: close");
        for (var line:text) lines.addAll(font.split(Component.literal(line),Math.max(1,width-12)));
        return lines;
    }
    public static int height(Font font,int width,Rac1CommerceSelection selection,
                             Rac1ShopPurchases.Quote quote,boolean browsing) {
        return lines(font,width,selection,quote,browsing).size()*font.lineHeight+8;
    }
    public static boolean drawAt(GuiGraphics g,Font font,Rac1CommerceSelection selection,
                                 Rac1ShopPurchases.Quote quote,boolean browsing,int top,int availableHeight) {
        int width=g.guiWidth();
        var lines=lines(font,width,selection,quote,browsing);
        int panelHeight=lines.size()*font.lineHeight+8;
        // A clipped quote must never authorize confirmation.
        if (top<0 || availableHeight<panelHeight || top+panelHeight>g.guiHeight() || width<80) {
            if (availableHeight>=font.lineHeight+4)
                g.drawString(font,"MC: resize to review quote; Yes disabled",4,top+2,0xffffff);
            return false;
        }
        int y=top+4;
        for (var line:lines) { g.drawString(font,line,6,y,0xffffff); y+=font.lineHeight; }
        return true;
    }
}
