package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.hud.*;
import com.github.giannigr.racmc.asset.shop.*;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.wad.WadDecoder;
import java.nio.file.Path;
import java.util.*;
import com.github.giannigr.racmc.cache.shop.Rac1ShopCacheStore;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.*;

/** Selected-disc preview data; optional reuse of existing RACSHOP payload, never a schema extension or voice cache. */
public record Rac1ShopPreviewSource(boolean pal,Rac1ShopAssets shop,Rac1AnimatedAsset body,
        Rac1ShopNormalGeometry normal,byte[] font,List<Pixels> textures,List<Pixels> icons,
        Rac1ShopSceneLightSource light,Rac1ShopPrototypeTextIds ids,Rac1ShopText text,
        Rac1ShopPreviewOffer.Facts facts,Rac1ShopUiSource ui,Rac1ShopNormalGeometry popup,Pixels led,
        Actor product,Actor presenter,Actor ammo,Rac1ShopPresentationSource presentation,Rac1ShopVoiceData voice,
        com.github.giannigr.racmc.sound.Rac1AudioCatalog.Clip deniedCue) {
    public record Actor(int id,Rac1AnimatedAsset animated,Rac1ShopNormalGeometry normal,List<Pixels> textures) {
        public Actor { textures=List.copyOf(textures); }
    }
    public record Pixels(int width,int height,byte[] rgba) {}
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(Rac1ShopPreviewSource.class);
    /** Runtime fast path: only a manager-issued capability, not cache metadata, replaces repeat verification. */
    public static Rac1ShopPreviewSource load(Rac1MediaLease lease,String expectedDiscKey,Rac1AnimatedAsset mainProduct,
                                             Path cacheRoot,Rac1ShopImportProfile profileTimes) throws Exception {
        lease.checkValid();
        if (!lease.discKey().equals(expectedDiscKey)) throw new AssetFormatException("Shop lease/main cache mismatch");
        var source=readValidated(lease,expectedDiscKey,mainProduct,profileTimes,cacheRoot);
        lease.checkValid();
        return source;
    }
    /** PDA remote graphics only. Its independently verified cue importer owns sound; no spoken data read. */
    public static Rac1ShopPreviewSource loadRemoteGraphics(Rac1MediaLease lease,String expectedDiscKey,
                                                          Rac1AnimatedAsset mainProduct,Path cacheRoot) throws Exception {
        lease.checkValid();
        if (!lease.discKey().equals(expectedDiscKey)) throw new AssetFormatException("PDA graphics lease/main cache mismatch");
        var source=readValidated(lease,expectedDiscKey,mainProduct,Rac1ShopImportProfile.disabled(),cacheRoot,false);
        lease.checkValid(); return source;
    }
    public static Rac1ShopPreviewSource load(Path path,String expectedDiscKey) throws Exception {
        return load(path,expectedDiscKey,null);
    }
    /** Runtime reuses the main-cache weapon; the source fixture may import it in memory. */
    public static Rac1ShopPreviewSource load(Path path,String expectedDiscKey,Rac1AnimatedAsset mainProduct) throws Exception {
        return load(path,expectedDiscKey,mainProduct,Rac1ShopImportProfile.disabled());
    }
    public static Rac1ShopPreviewSource load(Path path,String expectedDiscKey,Rac1AnimatedAsset mainProduct,
                                             Rac1ShopImportProfile profileTimes) throws Exception {
        var validator=new Rac1DiscValidator();
        try (var outcome=profileTimes.measure("disc-validation",() -> validator.inspect(path))) {
            if (!outcome.isSupported()) throw new AssetFormatException("Unsupported selected preview disc");
            return readValidated(outcome.image(),expectedDiscKey,mainProduct,profileTimes);
        }
    }
    /** Measurement seam only. Runtime still validates through load(Path,...), with no lease/bypass installed. */
    static Rac1ShopPreviewSource readValidated(Rac1DiscImage validated,String expectedDiscKey,
                                               Rac1AnimatedAsset mainProduct,Rac1ShopImportProfile profileTimes) throws Exception {
        return readValidated(validated,expectedDiscKey,mainProduct,profileTimes,null);
    }
    private static Rac1ShopPreviewSource readValidated(Rac1DiscImage validated,String expectedDiscKey,
                                                Rac1AnimatedAsset mainProduct,Rac1ShopImportProfile profileTimes,Path cacheRoot) throws Exception {
        return readValidated(validated,expectedDiscKey,mainProduct,profileTimes,cacheRoot,true);
    }
    private static Rac1ShopPreviewSource readValidated(Rac1DiscImage validated,String expectedDiscKey,
                                                Rac1AnimatedAsset mainProduct,Rac1ShopImportProfile profileTimes,Path cacheRoot,
                                                boolean spokenVendor) throws Exception {
            var validator=new Rac1DiscValidator();
            var image=profileTimes.image(validated);
            var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==image.revision()).findFirst().orElseThrow();
            if (!Rac1DiscIdentity.key(image.revision(),profile).equals(expectedDiscKey))
                throw new AssetFormatException("Preview selected disc/main cache mismatch");
            boolean pal=image.revision()==Rac1DiscRevision.PAL_V2_00;
            var level=profileTimes.measure("level-read-decode",() -> Rac1LevelAssets.load(image,1));
            var shop=shop(validated,image,level,profile.imageSha1(),cacheRoot,profileTimes);
            var body=profileTimes.measure("body-conversion",() -> Rac1AssetExtractor.readAnimatedAsset(level,11));
            byte[] toc=Rac1DiscTables.read(image).bytes();
            byte[] header=image.readBytes(Rac1DiscTables.of(toc).levelSector(1)*2048,0x2434);
            long base=word(header,8)*2048L;
            byte[] container=image.readBytes(base,0x58);
            int offset=word(container,0),size=word(container,4);
            if (offset<0 || size<=0 || size>4*1024*1024) throw new AssetFormatException("Preview overlay bounds");
            byte[] overlay=image.readBytes(base+offset,size);
            if (WadDecoder.isWad(overlay)) overlay=WadDecoder.decode(overlay,-1);
            int field=pal ? 0x18:0x10;
            byte[] gameplay=WadDecoder.decode(sectors(image,header,field,8*1024*1024),-1);
            var ids=Rac1ShopPrototypeTextIds.read(overlay,pal);
            var ui=Rac1ShopUiSource.read(overlay,pal);
            Rac1ShopPreviewOffer.verifyOriginalAmmoPrograms(overlay,pal);
            int strip=pal ? 0x2b2e28:0x2b1c10;
            int[] offsets={0x70,0x138,0x13c,0x140};
            int[] expected={0x2413000c,0x24060006,0x24070030,0x24080030};
            for (int i=0;i<offsets.length;i++)
                if (word(Rac1HudImporter.overlayRange(overlay,strip+offsets[i],4),0)!=expected[i])
                    throw new AssetFormatException("Original one-offer icon-strip placement mismatch");
            byte[] allText=sectors(image,toc,0x1528,8*1024*1024);
            if (WadDecoder.isWad(allText)) allText=WadDecoder.decode(allText,-1);
            List<Pixels> textures=new ArrayList<>();
            var entry=level.coreIndex().mobyClass(11);
            var metadata=MobyClassReader.of(level.classBlob(entry.offset())).readMaterialMetadata();
            for (var packet:metadata.packets()) if (!packet.metal())
                for (var material:packet.materials()) if (material.clamp()!=0)
                    throw new AssetFormatException("Preview normal texture address mismatch");
            for (int slot:entry.textureSlots()) {
                if (textures.size()==body.textures().length) break;
                var t=level.coreIndex().mobyTextures().get(slot&255);
                textures.add(new Pixels(t.width(),t.height(),raw(level.coreData(),
                        Math.addExact(level.coreIndex().texturesBase(),t.pixelOffset()),t.width(),t.height(),level.gsRam(),t.paletteOffset())));
            }
            byte[] index=level.coreIndex().bytes(),core=level.coreData();
            int table=word(index,0x5c),fxbase=word(index,0x68),at=table+2*16;
            if (word(index,0x58)<=2) throw new AssetFormatException("Preview font absent");
            int w=word(index,at+8),h=word(index,at+12);
            if (w!=256 || h!=128) throw new AssetFormatException("Preview font dimensions");
            byte[] font=raw(core,Math.addExact(fxbase,word(index,at+4)),w,h,core,Math.addExact(fxbase,word(index,at)));
            var required=new HashSet<>(ids.required()); required.addAll(ui.required()); required.addAll(Set.of(20039,20317));
            return new Rac1ShopPreviewSource(pal,shop,body,profileTimes.measure("normal-body",() -> Rac1ShopNormalGeometry.read(level,11)),font,
                    List.copyOf(textures),profileTimes.measure("hud-icons-read-decode-convert",() -> icons(image,toc)),Rac1ShopSceneLightSource.read(overlay,pal,gameplay,profile.imageSha1()),
                    ids,Rac1ShopText.read(allText,0,required),Rac1ShopPreviewOffer.Facts.read(overlay,pal),ui,Rac1ShopNormalGeometry.read(level,1137),
                    profileTimes.measure("hud-led-read-decode-convert",() -> hud(image,toc,59701,1,64,64)).getFirst(),
                    profileTimes.measure("product-conversion",() -> actor(level,168,mainProduct==null ? Rac1AssetExtractor.readGadgetAsset(level,168):mainProduct)),
                    profileTimes.measure("presenter-conversion",() -> actor(level,12,shop.models().salesman())),
                    profileTimes.measure("ammo-conversion",() -> actor(level,1006,null)),Rac1ShopPresentationSource.read(overlay,pal),
                      spokenVendor ? Rac1ShopVoiceData.read(image,toc,overlay,pal):null,
                      spokenVendor ? Rac1ShopDeniedCue.optional(image,level):null);
    }
    private static Rac1ShopAssets shop(Rac1DiscImage validated,Rac1DiscImage image,Rac1LevelAssets level,
                                       String sha1,Path root,Rac1ShopImportProfile profile) throws Exception {
        Rac1ShopCacheStore store=root==null ? null:new Rac1ShopCacheStore(root);
        boolean corrupt=false;
        if (store!=null) try {
            var cached=profile.measure("shop-sidecar-read",() -> store.read(sha1,0));
            if (cached.isPresent()) return cached.get();
        } catch (java.io.IOException unavailable) {
            corrupt=true;
            LOGGER.warn("Optional shop sidecar unavailable; preserving it and importing in memory: {}",unavailable.toString());
        }
        var imported=profile.measure("shop-import",() -> Rac1ShopImporter.read(image,level,sha1,0));
        if (store!=null && !corrupt) {
            if (validated instanceof Rac1MediaLease lease) lease.checkValid();
            try {
                profile.measure("shop-sidecar-write",() -> { store.write(imported); return null; });
            } catch (java.io.IOException unavailable) {
                LOGGER.warn("Optional shop sidecar write failed; retaining usable in-memory assets: {}",unavailable.toString());
            }
        }
        return imported;
    }
    private static Actor actor(Rac1LevelAssets level,int id,Rac1AnimatedAsset animated) throws Exception {
        if (animated!=null && animated.classNumber()!=id) throw new AssetFormatException("Shop actor/main cache identity mismatch");
        var entry=level.coreIndex().mobyClass(id);
        List<Pixels> textures=new ArrayList<>();
        for (int slot:entry.textureSlots()) {
            if (textures.size()==entry.textureCount()) break;
            var t=level.coreIndex().mobyTextures().get(slot&255);
            textures.add(new Pixels(t.width(),t.height(),raw(level.coreData(),
                    Math.addExact(level.coreIndex().texturesBase(),t.pixelOffset()),t.width(),t.height(),level.gsRam(),t.paletteOffset())));
        }
        return new Actor(id,animated,Rac1ShopNormalGeometry.read(level,id),textures);
    }
    private static List<Pixels> icons(Rac1DiscImage image,byte[] toc) throws Exception {
        return hud(image,toc,60015,5,32,32);
    }
    private static List<Pixels> hud(Rac1DiscImage image,byte[] toc,int id,int count,int width,int height) throws Exception {
        byte[] header=sectors(image,toc,0x14f8,64*1024);
        byte[][] banks=new byte[5][];
        for (int i=0;i<5;i++) banks[i]=WadDecoder.decode(sectors(image,toc,0x1500+i*8,4*1024*1024),word(header,0x54+i*4));
        // Validate all table/page references with the established reader before the raw-alpha path.
        var decoded=new Rac1HudBankDecoder(header,banks).decode(id);
        if (decoded.size()!=count) throw new AssetFormatException("Preview original HUD frame count");
        int definitions=word(header,4),frames=word(header,8),palettes=word(header,12),images=word(header,16);
        for (int at=definitions;at<frames;at+=8) if (shortAt(header,at)==id) {
            int first=shortAt(header,at+4);
            List<Pixels> result=new ArrayList<>();
            for (int i=0;i<count;i++) {
                int p=shortAt(header,frames+(first+i)*4),t=shortAt(header,frames+(first+i)*4+2);
                int pb=bank(header,0x14,p),tb=bank(header,0x34,t);
                int pp=word(header,palettes+p*8)&0x7fffffff,tp=word(header,images+t*8)&0x7fffffff;
                int w=decoded.get(i).width(),h=decoded.get(i).height();
                if (w!=width || h!=height) throw new AssetFormatException("Preview original HUD dimensions");
                result.add(new Pixels(w,h,raw(banks[tb],tp,w,h,banks[pb],pp)));
            }
            return List.copyOf(result);
        }
        throw new AssetFormatException("Preview original icons absent");
    }
    private static int bank(byte[] header,int ends,int page) throws Exception {
        for (int i=0;i<5;i++) if (page<word(header,ends+i*4)) return i;
        throw new AssetFormatException("Preview HUD page bank");
    }
    private static int shortAt(byte[] b,int at) throws Exception { range(b,at,2); return (b[at]&255)|((b[at+1]&255)<<8); }
    private static byte[] sectors(Rac1DiscImage image,byte[] table,int at,int limit) throws Exception {
        int sector=word(table,at),count=word(table,at+4);
        if (sector<=0 || count<=0 || count>limit/2048) throw new AssetFormatException("Preview sector bounds");
        return image.readSectors(sector,count);
    }
    private static byte[] raw(byte[] pixels,int offset,int w,int h,byte[] palette,int paletteOffset) throws Exception {
        var decoded=Rac1HudBankDecoder.indexed(pixels,offset,w,h,palette,paletteOffset);
        byte[] argb=decoded.pixels(),rgba=new byte[argb.length];
        for (int i=0;i<argb.length;i+=4) {
            int index=pixels[offset+i/4]&255,slot=(index&~24)|((index&8)<<1)|((index&16)>>1);
            rgba[i]=argb[i+1]; rgba[i+1]=argb[i+2]; rgba[i+2]=argb[i+3]; rgba[i+3]=palette[paletteOffset+slot*4+3];
        }
        return rgba;
    }
}
