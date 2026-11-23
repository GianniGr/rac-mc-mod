package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import java.util.*;

/** Original Novalis GS-resident environment maps, in memory only. NOT FX0x19 or class texture slots. */
public record Rac1ShopEnvironmentMaps(String discSha1, int sourceLevel, Map<Role, Image> images) {
    public enum Role {
        CHROME(-2,0x90,128), GLASS_ENVIRONMENT(-3,0x98,64);
        private final int selector, field, side;
        Role(int selector,int field,int side) { this.selector=selector; this.field=field; this.side=side; }
        public int selector() { return selector; }
        public int side() { return side; }
    }
    /** Row-major RGBA bytes in ORIGINAL GS units, including unexpanded alpha0..128. */
    public record Image(Role role, int pixelAddress, int paletteAddress, byte[] rgbaGs) {
        public Image {
            Objects.requireNonNull(role);
            if (pixelAddress < 0 || paletteAddress < 0 || (pixelAddress&255) != 0 || (paletteAddress&255) != 0
                    || (long)pixelAddress+role.side()*role.side() > 4*1024*1024
                    || (long)paletteAddress+1024 > 4*1024*1024
                    || rgbaGs.length != role.side()*role.side()*4)
                throw new IllegalArgumentException("Invalid original environment map extent/role");
            rgbaGs=rgbaGs.clone();
            for (int i=3;i<rgbaGs.length;i+=4)
                if ((rgbaGs[i]&255) > 128) throw new IllegalArgumentException("Unsupported original environment alpha");
        }
        @Override public byte[] rgbaGs() { return rgbaGs.clone(); }
    }
    public Rac1ShopEnvironmentMaps {
        if (discSha1 == null || !discSha1.matches("[0-9a-f]{40}") || sourceLevel != 1
                || !images.keySet().equals(Set.of(Role.CHROME,Role.GLASS_ENVIRONMENT)))
            throw new IllegalArgumentException("Invalid original environment-map identity");
        images=Map.copyOf(images);
        for (var role:Role.values())
            if (images.get(role).role() != role) throw new IllegalArgumentException("Environment role mismatch");
    }
    public Image forSelector(int selector) {
        return switch (selector) {
            case -2 -> images.get(Role.CHROME);
            case -3 -> images.get(Role.GLASS_ENVIRONMENT);
            default -> throw new IllegalArgumentException("Not an original environment-map selector");
        };
    }

    /** Caller supplies the existing validated level/disc digest; no path/config/media manager or network. */
    public static Rac1ShopEnvironmentMaps read(Rac1LevelAssets level,String sha1) throws AssetFormatException {
        return read(level.coreIndex().bytes(),level.gsRam(),sha1,level.level());
    }

    /** Bounded source-data seam for independent synthetic descriptor/texture tests. */
    public static Rac1ShopEnvironmentMaps read(byte[] index,byte[] gs,String sha1,int level) throws AssetFormatException {
        if (level != 1 || gs.length > 4*1024*1024)
            throw new AssetFormatException("Unsupported shop environment source");
        int count=word(index,0), table=word(index,4);
        if (count < 1 || count > 4096 || table < 0 || (long)table+count*16L > index.length)
            throw new AssetFormatException("Invalid GS upload descriptor table");
        Map<Role,Image> images=new EnumMap<>(Role.class);
        for (var role:Role.values()) {
            int pixelAddress=word(index,role.field), paletteAddress=word(index,role.field+4);
            int pixels=descriptor(index,table,count,pixelAddress,0x13,role.side,role.side);
            int palette=descriptor(index,table,count,paletteAddress,0,0,0);
            Rac1HudBankDecoder.range(gs,pixels,role.side*role.side);
            Rac1HudBankDecoder.range(gs,palette,1024);
            // The compatible common decoder expands alpha. Check its invertible domain first.
            for (int i=3;i<1024;i+=4)
                if ((gs[palette+i]&255)>128) throw new AssetFormatException("Environment palette alpha cannot be losslessly decoded");
            byte[] argb=Rac1HudBankDecoder.indexed(gs,pixels,role.side,role.side,gs,palette).pixels();
            byte[] rgba=new byte[argb.length];
            for (int i=0;i<argb.length;i+=4) {
                rgba[i]=argb[i+1]; rgba[i+1]=argb[i+2]; rgba[i+2]=argb[i+3];
                int expanded=argb[i]&255;
                rgba[i+3]=(byte)(expanded==255 ? 128 : expanded/2);
            }
            try { images.put(role,new Image(role,pixelAddress,paletteAddress,rgba)); }
            catch (IllegalArgumentException failure) { throw new AssetFormatException("Invalid original environment map",failure); }
        }
        try { return new Rac1ShopEnvironmentMaps(sha1,level,images); }
        catch (IllegalArgumentException failure) { throw new AssetFormatException("Invalid environment identity",failure); }
    }
    private static int descriptor(byte[] index,int table,int count,int address,int psm,int width,int height)
            throws AssetFormatException {
        if (address < 0 || (address&255) != 0) throw new AssetFormatException("Invalid environment GS address");
        int match=-1;
        for (int p=0;p<count;p++) {
            int at=table+p*16;
            if (word(index,at+8)==address) {
                if (match>=0) throw new AssetFormatException("Ambiguous environment GS descriptor");
                match=at;
            }
        }
        if (match<0 || word(index,match)!=psm || unsignedShort(index,match+4)!=width
                || unsignedShort(index,match+6)!=height)
            throw new AssetFormatException("Missing/unsupported environment GS descriptor format");
        int offset=word(index,match+12);
        if (offset<0 || (offset&255)!=0) throw new AssetFormatException("Invalid environment staging offset");
        return offset;
    }
    private static int word(byte[] bytes,int at) throws AssetFormatException { return Rac1HudBankDecoder.word(bytes,at); }
    private static int unsignedShort(byte[] bytes,int at) throws AssetFormatException {
        Rac1HudBankDecoder.range(bytes,at,2); return (bytes[at]&255)|((bytes[at+1]&255)<<8);
    }
}
