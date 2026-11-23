package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.hud.*;
import com.github.giannigr.racmc.asset.shop.*;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.wad.WadDecoder;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;
import static com.github.giannigr.racmc.client.shop.Rac1ShopGsPixelProgram.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopMaterials.*;

/**
 * Owner-approved C1-A source-asset slices, NOT an installed/playable original shop.
 * Explicit fixture: vendor origin/yaw0, source cut camera, stable sampled poses,
 * no dynamic point lights, flat background instead of world snapshot, origin sphere for shine/E.
 */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscShopC1GpuProofTest {
    private static final float[] ID={1,0,0,0,1,0,0,0,1};
    private static final float[] VIEW={0,0,-1,1,0,0,0,-1,0};
    private record Lighting(float[] directions,float[] colors,int[] ambient) {}
    private record Inputs(Rac1ShopSceneLightSource light,Rac1ShopPrototypeTextIds ids,Rac1ShopText text) {}
    private record Camera(float tanX,float tanY,int width,int height,float farPacked,Rac1ShopSceneLightSource.Fog fog) {
        Rac1ShopOrderedGpu.Vertex project(float x,float y,float z,int[] rgba,float s,float t,boolean metal) {
            float d=3.8f-x; // original cut eye(3.8,0,1.5), looking-X; x-right is+Y, y-down is-Z
            if (d<=0) throw new IllegalArgumentException("Fixture source vertex behind cut camera");
            double n=32,f=farPacked;
            double zgs=8388096-8388080*((f+n)/(f-n)-2*f*n/((f-n)*d*1024));
            if (metal) zgs+=1000*n/(d*1024);
            float clipZ=(float)((zgs/16777216*2-1)*d);
            return new Rac1ShopOrderedGpu.Vertex(y/tanX,(z-1.5f)/tanY,clipZ,d,s,t,
                    rgba[0],rgba[1],rgba[2],rgba[3],Rac1ShopLightingProgram.fogByte(d*1024,fog));
        }
        double[] pixel(float x,float y,float z) {
            float d=3.8f-x;
            if (d<=0) throw new IllegalArgumentException("Fixture monitor behind source camera");
            return new double[]{(y/tanX/d+1)*width/2, (1-(z-1.5)/tanY/d)*height/2};
        }
    }
    @Test void originalPopupMetalAndImportedMonitorPoseProducePrivateViewableSlices() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU")),"Owned hidden C1 GPU opt-in");
        String out=System.getProperty("racmc.shopC1ProofOutput");
        assertNotNull(out,"Explicit owned output directory required");
        Path output=Path.of(out); assertTrue(Files.isDirectory(output));
        var validator=new Rac1DiscValidator();
        try (var outcome=validator.inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported());
            boolean pal=outcome.image().revision()==Rac1DiscRevision.PAL_V2_00;
            String region=pal ? "pal":"ntsc";
            String sha=validator.catalog().profiles().stream().filter(p -> p.revision()==outcome.image().revision())
                    .findFirst().orElseThrow().imageSha1();
            var level=Rac1LevelAssets.load(outcome.image(),1);
            var shop=Rac1ShopImporter.read(outcome.image(),level,sha,0);
            var maps=Rac1ShopEnvironmentMaps.read(level,sha);
            var body=Rac1AssetExtractor.readAnimatedAsset(level,11);
            var logo=Rac1AssetExtractor.readAnimatedAsset(level,1143);
            var popup=shop.models().popup(); var bolt=shop.models().backdrop();
            var inputs=inputs(outcome.image(),pal,sha); var light=inputs.light;
            assertTrue(printable(inputs.text.get(inputs.ids.buy())).endsWith("Buy"));
            assertTrue(printable(inputs.text.get(inputs.ids.exit())).endsWith("Exit"));
            assertTrue(printable(inputs.text.get(inputs.ids.denied())).toLowerCase(Locale.ROOT).contains("afford"));
            int height=pal ? 448:416;
            var worldFog=light.worldFog();
            var rttFog=new Rac1ShopSceneLightSource.Fog(worldFog.red(),worldFog.green(),worldFog.blue(),0,524288,255,0);
            Camera main=new Camera(.63f,.63f*(pal ? .756f:.775f),512,height,745472,worldFog);
            Camera rttCamera=new Camera(1,1*(pal ? .756f:.775f),512,128,524288,rttFog);
            var open=new Rac1ShopKioskPose(body).stage(Rac1ShopKioskPose.Stage.OPEN,0,
                    Rac1ShopKioskPose.monitorArms(shop.sceneTables(),8)); // FIXTURE count8, NOT catalog policy
            float[] popupF=Rac1AnimationEvaluator.evaluate(popup.skeleton(),sequence(popup,1),0,1);
            float[] boltF=Rac1AnimationEvaluator.evaluate(bolt.skeleton(),sequence(bolt,0),0,1);
            var normalBody=Rac1ShopNormalGeometry.read(level,11);
            var normalPopup=Rac1ShopNormalGeometry.read(level,1137);
            var normalBolt=Rac1ShopNormalGeometry.read(level,13);
            float[] bodyRaw=normalBody.originalPalette(open.skinning());
            float[] popupRaw=normalPopup.originalPalette(popupF),boltRaw=normalBolt.originalPalette(boltF);
            List<String> report=new ArrayList<>();
            report.add("C1-A FIRST PROOF ONLY; not playable UI, runtime camera, original snapshot, animation/pixel/AV acceptance.");
            report.add("disc="+sha+" region="+region+"; explicit original cut camera at vendor origin/yaw0.");
            report.add("FIXTURES: empty dynamic point bank; origin sphere for E/shine; static source poses; display count8; flat gray clear, NOT original world snapshot.");
            report.add("Slices preserve original geometry/material roles and source light; no fake popup plane or envmap/FX0x19 substitution.");
            try (var context=new Rac1ShopOrderedGpuTest.Hidden(); var gpu=new Rac1ShopOrderedGpu(512,height)) {
                var scene=gpu.surface(512,height); var popupScene=gpu.surface(512,height);
                var logoScene=gpu.surface(512,height);
                var metalTarget=gpu.surface(512,128); var text=gpu.surface(512,128);
                gpu.clearDepth(0);
                gpu.clear(scene,new Rgba(24,24,24,128),false,0);
                gpu.clear(popupScene,new Rgba(24,24,24,128),false,0);
                gpu.clear(metalTarget,new Rgba(0,0,0,0),false,0);
                gpu.clear(text,new Rgba(0,0,0,0),false,0);
                gpu.clear(logoScene,new Rgba(24,24,24,128),false,0);
                var bodyTextures=images(gpu,body.textures(),level,11);
                var boltTextures=images(gpu,bolt.textures(),level,13);
                var normalState=state(0x44,128,96,false,false,true,worldFog);
                Lighting bodyLight=placed(light);
                drawNormal(gpu,scene,normalBody.pose(11,bodyRaw),bodyTextures,main,ID,new float[3],bodyLight,normalState);
                var bodyOnly=gpu.read(scene);
                assertChanged(bodyOnly,new Rgba(24,24,24,128),1000);
                // Source sampled bone-triplet extents, no invented monitor rectangles.
                var font=shop.graphics().fonts().get("small");
                var atlas=gpu.image(font.atlas().width(),font.atlas().height(),rawFont(level,font),new Sampler(Filter.LINEAR,Address.CLAMP));
                for (int s=0;s<6;s++) {
                    var quad=Rac1ShopLayout.monitorVendor(body,open.chain(),s,shop.sceneTables().margins());
                    double[] p=main.pixel((float)quad.corner().x(),(float)-quad.corner().z(),(float)quad.corner().y());
                    var end=quad.opposite();
                    double[] q=main.pixel((float)end.x(),(float)-end.z(),(float)end.y());
                    double x=Math.min(p[0],q[0]),y=Math.min(p[1],q[1]),w=Math.abs(q[0]-p[0]),h=Math.abs(q[1]-p[1]);
                    assertTrue(w>0 && h>0);
                    report.add(String.format(Locale.ROOT,"source monitor%d projected rect %.5f,%.5f %.5fx%.5f",s,x,y,w,h));
                    // Only original button/prompt content is rendered; other monitor contents remain omitted.
                    if (s==3 || s==4) {
                        gpu.clear(text,new Rgba(0,0,0,0),true,0);
                        byte[] bytes=inputs.text.get(s==3 ? inputs.ids.buy():inputs.ids.exit());
                        drawText(gpu,text,atlas,font,bytes,Math.min(512,(int)Math.ceil(w)),Math.min(128,(int)Math.ceil(h)));
                        copyRect(gpu,scene,text,x,y,w,h,main.width,main.height);
                    }
                }
                var bodyLayout=gpu.read(scene);
                // Popup uses actual class1137 normal triangles and sampled sequence1, not a rectangle replacement.
                gpu.clearDepth(0);
                Lighting menu=menu(light,ID,16);
                var posedPopup=normalPopup.pose(1137,popupRaw);
                assertTrue(posedPopup.triangles().stream().allMatch(t -> t.selector()==-1));
                drawNormal(gpu,popupScene,posedPopup,List.of(),main,ID,new float[3],menu,normalState);
                var popupRead=gpu.read(popupScene); assertChanged(popupRead,new Rgba(24,24,24,128),100);
                // Source class13 at its original item-panel backdrop placement/rotation; normal pass, then -3 metal.
                gpu.clearDepth(0);
                float angle=-.36f,c=(float)Math.cos(angle),sn=(float)Math.sin(angle);
                float[] rotation={c,0,-sn,0,1,0,sn,0,c},placement={-10.2f,-4.25f,0};
                var rttState=state(0x44,128,96,false,false,true,rttFog);
                drawNormal(gpu,metalTarget,normalBolt.pose(13,boltRaw),boltTextures,rttCamera,rotation,placement,menu(light,rotation,32),rttState);
                var withoutMetal=gpu.read(metalTarget);
                var metal=Rac1ShopMetalGeometry.read(level,13);
                var posed=metal.poseConverted(13,boltF);
                var original=MobyClassReader.of(level.classBlob(level.coreIndex().mobyClass(13).offset()));
                var metadata=original.readMaterialMetadata();
                var env=gpu.image(64,64,maps.forSelector(-3).rgbaGs(),new Sampler(Filter.LINEAR,Address.CLAMP));
                float[] directions=menu(light,rotation,32).directions,colors=new float[9];
                System.arraycopy(light.menuColor(),0,colors,0,3);
                float[] e=Rac1ShopLightingProgram.environmentBasis(VIEW,rotation,new float[]{placement[0]-3.8f,placement[1],placement[2]-1.5f});
                int alpha=Rac1ShopMetalProgram.shineAlpha(24,(int)((3.8f-placement[0])*1024));
                int triangleCount=0;
                for (var packet:posed) {
                    List<Rac1ShopOrderedGpu.Vertex> vertices=new ArrayList<>();
                    for (var v:packet.vertices()) {
                        var sample=Rac1ShopMetalProgram.evaluate(v.sourceNormalX(),v.sourceNormalY(),v.sourceNormalZ(),directions,colors,e,alpha);
                        float[] world=world(rotation,placement,v.packedX()*original.scale()/1024f,v.packedY()*original.scale()/1024f,v.packedZ()*original.scale()/1024f);
                        vertices.add(rttCamera.project(world[0],world[1],world[2],new int[]{sample.red(),sample.green(),sample.blue(),sample.alpha()},sample.textureS(),sample.textureT(),true));
                    }
                    for (var t:packet.triangles()) {
                        var material=metadata.packets().stream().filter(p -> p.packetIndex()==t.materialPacket()).findFirst().orElseThrow().materials().get(t.materialBlock());
                        assertEquals(-3,material.textureSelector());
                        gpu.triangle(metalTarget,env,rttState,vertices.get(t.a()),vertices.get(t.b()),vertices.get(t.c())); triangleCount++;
                    }
                }
                assertEquals(52,triangleCount);
                var metalRead=gpu.read(metalTarget);
                assertFalse(Arrays.equals(withoutMetal.rgba(),metalRead.rgba()),"Original map pass must actually alter output");
                // More legible -2 slice: original approach-logo placement/scale, viewed by the explicit cut-camera fixture.
                // Ambient64/bank0 are the source InitMoby defaults, a declared fixture here, NOT a verified live child assignment.
                gpu.clearDepth(0);
                float[] logoF=Rac1AnimationEvaluator.evaluate(logo.skeleton(),sequence(logo,0),0,1);
                var logoNormal=Rac1ShopNormalGeometry.read(level,1143);
                var baseLight=placed(light);
                var logoLight=new Lighting(baseLight.directions,baseLight.colors,new int[]{64,64,64});
                drawNormal(gpu,logoScene,logoNormal.pose(1143,logoNormal.originalPalette(logoF)),images(gpu,logo.textures(),level,1143),
                        main,ID,new float[]{0,0,2.95f},logoLight,normalState,2.5f);
                var beforeChrome=gpu.read(logoScene);
                var logoMetal=Rac1ShopMetalGeometry.read(level,1143);
                var logoReader=MobyClassReader.of(level.classBlob(level.coreIndex().mobyClass(1143).offset()));
                var chrome=gpu.image(128,128,maps.forSelector(-2).rgbaGs(),new Sampler(Filter.LINEAR,Address.CLAMP));
                float[] logoE=Rac1ShopLightingProgram.environmentBasis(VIEW,ID,new float[]{-3.8f,0,1.45f});
                float[] logoColors=new float[9];
                System.arraycopy(baseLight.colors,0,logoColors,0,3); System.arraycopy(baseLight.colors,4,logoColors,3,3);
                int logoTriangles=0;
                for (var packet:logoMetal.poseConverted(1143,logoF)) {
                    List<Rac1ShopOrderedGpu.Vertex> vertices=new ArrayList<>();
                    for (var v:packet.vertices()) {
                        var sample=Rac1ShopMetalProgram.evaluate(v.sourceNormalX(),v.sourceNormalY(),v.sourceNormalZ(),
                                baseLight.directions,logoColors,logoE,128); // source shine32 at fixture depth3.8, radius0
                        vertices.add(main.project(v.packedX()*logoReader.scale()/1024f*2.5f,
                                v.packedY()*logoReader.scale()/1024f*2.5f,v.packedZ()*logoReader.scale()/1024f*2.5f+2.95f,
                                new int[]{sample.red(),sample.green(),sample.blue(),sample.alpha()},sample.textureS(),sample.textureT(),true));
                    }
                    for (var t:packet.triangles()) {
                        gpu.triangle(logoScene,chrome,normalState,vertices.get(t.a()),vertices.get(t.b()),vertices.get(t.c()));
                        logoTriangles++;
                    }
                }
                assertEquals(698,logoTriangles);
                var logoRead=gpu.read(logoScene); assertChanged(logoRead,new Rgba(24,24,24,128),100);
                assertFalse(Arrays.equals(beforeChrome.rgba(),logoRead.rgba()),"Original chrome map must change the actual source logo");
                // Source text/font target is a separate proof slice; window placement is FIXTURE, not popup layout certification.
                gpu.clear(text,new Rgba(0,0,0,0),true,0);
                drawText(gpu,text,atlas,font,inputs.text.get(inputs.ids.denied()),230,64);
                var textRead=gpu.read(text); assertChanged(textRead,new Rgba(0,0,0,0),20);
                write(output.resolve("resume10-"+region+"-body-monitors.png"),bodyLayout);
                write(output.resolve("resume10-"+region+"-popup.png"),popupRead);
                write(output.resolve("resume10-"+region+"-metal-rtt.png"),metalRead);
                write(output.resolve("resume10-"+region+"-source-text.png"),textRead);
                write(output.resolve("resume10-"+region+"-chrome-logo.png"),logoRead);
                BufferedImage sheet=new BufferedImage(1536,height+128,BufferedImage.TYPE_INT_RGB);
                paste(sheet,bodyLayout,0,0); paste(sheet,popupRead,512,0);
                paste(sheet,logoRead,1024,0);
                paste(sheet,metalRead,0,height); paste(sheet,textRead,512,height);
                Path png=output.resolve("resume10-"+region+"-proof.png"); assertFalse(Files.exists(png),"Preserve earlier proof output");
                assertTrue(ImageIO.write(sheet,"png",png.toFile()));
                report.add("contact sheet: top-left source vendor + source-projected button/exit monitors; top-middle actual popup; top-right source approach-logo placement/scale, original -2 map, cut-camera/default-light fixture; bottom-left class13 original -3 RTT; bottom-middle original denied-text/font fixture window.");
                report.add("Primary regional text roles: buy="+inputs.ids.buy()+" exit="+inputs.ids.exit()+" denied="+inputs.ids.denied()+"; English semantic assertions pass, not ID-existence-only checks.");
                report.add("Logo fixture: ambient64/bank0 InitMoby defaults, NOT verified runtime child lighting; source position(0,0,2.95), scale2.5; sampled sequence0; glow overwrite omitted.");
                report.add("Source units: normal moby textures LINEAR+REPEAT; envmaps/font LINEAR+CLAMP; raw original palette RGBA, not display-expanded alpha or normalized font RGB.");
                report.add("NOT RENDERED: original world snapshot, salesman/weapon/icons/ticker/noise/scan/glass/cone/HUD, popup target onto popup bones, quantity backdrop, voice, live scene inputs.");
                report.add("metal triangles="+triangleCount+" shineAlpha="+alpha+"; body and popup nonflat source geometry coverage asserted.");
                report.add("chrome triangles="+logoTriangles+" actual source map changes frame; RGBA hash="+hash(logoRead.rgba()));
            report.add("RGBA hashes: body="+hash(bodyLayout.rgba())+" popup="+hash(popupRead.rgba())+" metal="+hash(metalRead.rgba())+" text="+hash(textRead.rgba()));
                Path manifest=output.resolve("resume10-"+region+"-proof.txt");
                Files.write(manifest,report,StandardOpenOption.CREATE_NEW);
                System.out.println(String.join("\n",report)); assertEquals(GL_NO_ERROR,glGetError()); context.unfocused();
            }
        }
    }
    private static void drawNormal(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface surface,Rac1ShopNormalGeometry.Posed posed,
                                   List<Rac1ShopOrderedGpu.Image> textures,Camera camera,float[] axes,float[] placement,
                                   Lighting light,Rac1ShopOrderedGpu.State state) {
        drawNormal(gpu,surface,posed,textures,camera,axes,placement,light,state,1);
    }
    private static void drawNormal(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface surface,Rac1ShopNormalGeometry.Posed posed,
                                   List<Rac1ShopOrderedGpu.Image> textures,Camera camera,float[] axes,float[] placement,
                                   Lighting light,Rac1ShopOrderedGpu.State state,float actorScale) {
        List<Rac1ShopOrderedGpu.Vertex> vertices=new ArrayList<>();
        for (var v:posed.vertices()) {
            int[] rgba=Rac1ShopLightingProgram.normal(v.nx(),v.ny(),v.nz(),light.directions,light.colors,light.ambient,128,new int[]{v.red(),v.green(),v.blue(),v.alpha()});
            float[] world=world(axes,placement,v.x()*actorScale,v.y()*actorScale,v.z()*actorScale);
            vertices.add(camera.project(world[0],world[1],world[2],rgba,v.s(),v.t(),false));
        }
        for (var t:posed.triangles())
            gpu.triangle(surface,t.selector()==-1 ? null:textures.get(t.selector()),state,vertices.get(t.a()),vertices.get(t.b()),vertices.get(t.c()));
    }
    private static Lighting placed(Rac1ShopSceneLightSource source) {
        float[] rec=source.vendorLightRecord(),d=new float[9],colors=new float[12];
        for (int k=0;k<2;k++) {
            System.arraycopy(rec,k*8,colors,k*4,4);
            float[] direction=Rac1ShopLightingProgram.toLight(ID,Arrays.copyOfRange(rec,k*8+4,k*8+7));
            System.arraycopy(direction,0,d,k*3,3);
        }
        return new Lighting(d,colors,source.vendorAmbient());
    }
    private static Lighting menu(Rac1ShopSceneLightSource source,float[] axes,int ambient) {
        float[] d=new float[9],colors=new float[12];
        System.arraycopy(Rac1ShopLightingProgram.toLight(axes,Arrays.copyOf(source.menuDirection(),3)),0,d,0,3);
        System.arraycopy(source.menuColor(),0,colors,0,4);
        return new Lighting(d,colors,new int[]{ambient,ambient,ambient});
    }
    private static float[] world(float[] axes,float[] p,float x,float y,float z) {
        return new float[]{axes[0]*x+axes[3]*y+axes[6]*z+p[0],axes[1]*x+axes[4]*y+axes[7]*z+p[1],axes[2]*x+axes[5]*y+axes[8]*z+p[2]};
    }
    private static Rac1ShopOrderedGpu.State state(int blend,int fixed,int ref,boolean alphaAlways,boolean depthAlways,boolean fog,Rac1ShopSceneLightSource.Fog f) {
        return new Rac1ShopOrderedGpu.State(blend,fixed,ref,alphaAlways,depthAlways,false,0,fog,f.red(),f.green(),f.blue());
    }
    private static List<Rac1ShopOrderedGpu.Image> images(Rac1ShopOrderedGpu gpu,Rac1Texture[] textures,Rac1LevelAssets level,int id) throws Exception {
        List<Rac1ShopOrderedGpu.Image> out=new ArrayList<>();
        var entry=level.coreIndex().mobyClass(id); assertEquals(textures.length,entry.textureCount());
        var metadata=MobyClassReader.of(level.classBlob(entry.offset())).readMaterialMetadata();
        for (var packet:metadata.packets()) if (!packet.metal())
            for (var material:packet.materials()) assertEquals(0,material.clamp(),"Source normal packet uses REPEAT, not environment clamp");
        for (int i=0;i<textures.length;i++) {
            var t=textures[i];
            var source=level.coreIndex().mobyTextures().get(entry.textureSlots()[i]&255);
            assertEquals(source.width(),t.width()); assertEquals(source.height(),t.height());
            byte[] rgba=rawIndexed(level.coreData(),Math.addExact(level.coreIndex().texturesBase(),source.pixelOffset()),
                    t.width(),t.height(),level.gsRam(),source.paletteOffset());
            out.add(gpu.image(t.width(),t.height(),rgba,new Sampler(Filter.LINEAR,Address.REPEAT)));
        }
        return out;
    }
    private static byte[] rawIndexed(byte[] pixels,int offset,int width,int height,byte[] palette,int paletteOffset) throws Exception {
        // Reuse the validated common CSM1 RGB decoder, but retain literal GS alpha from the primary palette.
        var decoded=Rac1HudBankDecoder.indexed(pixels,offset,width,height,palette,paletteOffset);
        byte[] argb=decoded.pixels(),out=new byte[argb.length];
        for (int i=0;i<argb.length;i+=4) {
            int index=pixels[offset+i/4]&255;
            int slot=(index&~24)|((index&8)<<1)|((index&16)>>1);
            out[i]=argb[i+1]; out[i+1]=argb[i+2]; out[i+2]=argb[i+3];
            out[i+3]=palette[paletteOffset+slot*4+3];
        }
        return out;
    }
    private static byte[] rawFont(Rac1LevelAssets level,Rac1HudFont font) throws Exception {
        byte[] index=level.coreIndex().bytes(),core=level.coreData();
        int count=word(index,0x58),table=word(index,0x5c),base=word(index,0x68);
        assertTrue(count>2 && count<=256 && table>=0 && (long)table+count*16<=index.length);
        int at=table+2*16,w=word(index,at+8),h=word(index,at+12);
        assertEquals(font.atlas().width(),w); assertEquals(font.atlas().height(),h);
        return rawIndexed(core,Math.addExact(base,word(index,at+4)),w,h,core,Math.addExact(base,word(index,at)));
    }
    private static void drawText(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface target,Rac1ShopOrderedGpu.Image atlas,
                                 Rac1HudFont font,byte[] bytes,int width,int height) {
        var window=new Rac1ShopTextWindow.Window(0,0,Math.max(1,width),Math.max(1,height),width/2,height/2,16,true,true);
        var layout=Rac1ShopTextWindow.layout(font,bytes,window);
        var state=new Rac1ShopOrderedGpu.State(0x44,128,0,true,true,false,0,false,0,0,0);
        for (var line:layout.lines()) if (line.visible())
            for (var glyph:Rac1ShopGlyphLayout.line(font,line.bytes(),Rac1ShopPrototypeTextIds.GS_TEXT_COLOR,true).glyphs()) {
                double x=line.x()+glyph.x(),y=line.y()+glyph.y();
                quad(gpu,target,atlas,null,state,x,y,glyph.width(),glyph.height(),target.width(),target.height(),
                        glyph.u()/(float)font.atlas().width(),glyph.v()/(float)font.atlas().height(),
                        (glyph.u()+glyph.width())/(float)font.atlas().width(),(glyph.v()+glyph.height())/(float)font.atlas().height(),
                        new Rgba(glyph.gsColor()&255,glyph.gsColor()>>>8&255,glyph.gsColor()>>>16&255,glyph.gsColor()>>>24&255));
            }
    }
    private static void copyRect(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface target,Rac1ShopOrderedGpu.Surface source,
                                 double x,double y,double w,double h,int width,int height) {
        var state=new Rac1ShopOrderedGpu.State(0x64,128,0,true,true,false,0,false,0,0,0);
        quad(gpu,target,null,source,state,x,y,w,h,width,height,0,0,(float)(w/source.width()),(float)(h/source.height()));
    }
    private static void quad(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface target,Rac1ShopOrderedGpu.Image image,Rac1ShopOrderedGpu.Surface source,
                             Rac1ShopOrderedGpu.State state,double x,double y,double w,double h,int width,int height,float s0,float t0,float s1,float t1) {
        quad(gpu,target,image,source,state,x,y,w,h,width,height,s0,t0,s1,t1,new Rgba(128,128,128,128));
    }
    private static void quad(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface target,Rac1ShopOrderedGpu.Image image,Rac1ShopOrderedGpu.Surface source,
                             Rac1ShopOrderedGpu.State state,double x,double y,double w,double h,int width,int height,float s0,float t0,float s1,float t1,Rgba color) {
        var a=Rac1ShopOrderedGpuTest.vertex((float)(2*x/width-1),(float)(1-2*y/height),0xfffff0,s0,t0,color);
        var b=Rac1ShopOrderedGpuTest.vertex((float)(2*(x+w)/width-1),(float)(1-2*y/height),0xfffff0,s1,t0,color);
        var c=Rac1ShopOrderedGpuTest.vertex((float)(2*x/width-1),(float)(1-2*(y+h)/height),0xfffff0,s0,t1,color);
        var d=Rac1ShopOrderedGpuTest.vertex((float)(2*(x+w)/width-1),(float)(1-2*(y+h)/height),0xfffff0,s1,t1,color);
        if (source==null) { gpu.triangle(target,image,state,a,b,c); gpu.triangle(target,image,state,c,b,d); }
        else { gpu.copyTriangle(target,source,state,a,b,c); gpu.copyTriangle(target,source,state,c,b,d); }
    }
    private static Rac1Animation sequence(Rac1AnimatedAsset a,int slot) {
        return Arrays.stream(a.animations()).filter(s -> s.name().equals("sequence_"+slot)).findFirst().orElseThrow();
    }
    private static Inputs inputs(Rac1DiscImage image,boolean pal,String sha) throws Exception {
        long sector=Rac1DiscTables.read(image).levelSector(1);
        byte[] header=image.readBytes(sector*2048,0x2434);
        long base=word(header,8)*2048L;
        byte[] container=image.readBytes(base,0x58);
        int offset=word(container,0),size=word(container,4); assertTrue(offset>=0 && size>0 && size<=4*1024*1024);
        byte[] overlay=image.readBytes(base+offset,size);
        if (WadDecoder.isWad(overlay)) overlay=WadDecoder.decode(overlay,-1);
        int field=pal ? 0x18:0x10,count=word(header,field+4); assertTrue(count>0 && count<=4096);
        byte[] gameplay=WadDecoder.decode(image.readBytes(word(header,field)*2048L,count*2048),-1);
        var ids=Rac1ShopPrototypeTextIds.read(overlay,pal);
        byte[] toc=Rac1DiscTables.read(image).bytes();
        int textSector=word(toc,0x1528),textSectors=word(toc,0x152c);
        assertTrue(textSector>0 && textSectors>0 && textSectors<=4096);
        byte[] allText=image.readBytes(textSector*2048L,textSectors*2048);
        if (WadDecoder.isWad(allText)) allText=WadDecoder.decode(allText,-1);
        return new Inputs(Rac1ShopSceneLightSource.read(overlay,pal,gameplay,sha),ids,Rac1ShopText.read(allText,0,ids.required()));
    }
    private static String printable(byte[] bytes) {
        StringBuilder out=new StringBuilder();
        for (byte b:bytes) if ((b&255)>=32 && (b&255)<=126) out.append((char)b);
        return out.toString();
    }
    private static void assertChanged(Rac1ShopOrderedGpu.Readback r,Rgba background,int minimum) {
        byte[] b=r.rgba(); int changed=0;
        for (int i=0;i<b.length;i+=4)
            if ((b[i]&255)!=background.r() || (b[i+1]&255)!=background.g() || (b[i+2]&255)!=background.b()) changed++;
        assertTrue(changed>minimum,"Meaningful actual rendered coverage: "+changed);
    }
    private static String hash(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
    private static void write(Path path,Rac1ShopOrderedGpu.Readback r) throws Exception {
        assertFalse(Files.exists(path),"Preserve prior proof output");
        BufferedImage b=new BufferedImage(r.width(),r.height(),BufferedImage.TYPE_INT_RGB); paste(b,r,0,0);
        assertTrue(ImageIO.write(b,"png",path.toFile()));
    }
    private static void paste(BufferedImage image,Rac1ShopOrderedGpu.Readback r,int x,int y) {
        byte[] rgba=r.rgba();
        for (int row=0;row<r.height();row++) for (int col=0;col<r.width();col++) {
            int at=(row*r.width()+col)*4;
            image.setRGB(x+col,y+row,((rgba[at]&255)<<16)|((rgba[at+1]&255)<<8)|(rgba[at+2]&255));
        }
    }
}
