package com.github.giannigr.racmc.asset.pda;

import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.wad.WadDecoder;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;

/** Bounded original remote-branch instruction facts only, not UI/cue fidelity acceptance. */
@EnabledIfSystemProperty(named="racmc.pdaRemoteResearch",matches="true")
class RealDiscPdaRemoteResearchTest {
    @TempDir Path temp;
    @Test void inspectOnlyNamedRemoteVendorBranchesThroughVerifiedLease() throws Exception {
        var path=Path.of(System.getProperty("racmc.isoImage")).toAbsolutePath().normalize();
        var validator=new Rac1DiscValidator();
        try (var media=new Rac1MediaManager(validator,temp.resolve("remote-media.properties"))) {
            media.start(); media.select(path);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
            while ((media.isChecking() || media.completedAttempts()==0) && System.nanoTime()<deadline) Thread.sleep(5);
            assertTrue(media.isReady(),() -> media.attemptResult().toString());
            var result=media.attemptResult().orElseThrow();
            var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==result.revision()).findFirst().orElseThrow();
            try (var lease=media.lease(path,Rac1DiscIdentity.key(result.revision(),profile))) {
                assertEquals(Rac1DiscRevision.NTSC_U_V1_00,lease.revision(),"Initial bounded address map is NTSC only");
                var tables=Rac1DiscTables.read(lease);
                var level=Rac1LevelHeader.read(lease,tables.levelSector(1));
                long base=level.dataSector()*2048L;
                var container=lease.readBytes(base,0x58);
                int offset=word(container,0),size=word(container,4);
                assertTrue(offset>0 && size>0 && size<=4*1024*1024);
                var overlay=lease.readBytes(base+offset,size);
                if (WadDecoder.isWad(overlay)) overlay=WadDecoder.decode(overlay,-1);
                final int start=0x2adef0,end=0x2b4520;
                var code=Rac1HudImporter.overlayRange(overlay,start,end-start);
                System.out.printf("PDA REMOTE bounded NTSC code 0x%x..0x%x%n",start,end);
                Long[] registers=new Long[32]; registers[0]=0L;
                for (int at=0;at<code.length;at+=4) {
                    int v=word(code,at),op=v>>>26,imm=v&65535,rs=v>>>21&31,rt=v>>>16&31,rd=v>>>11&31,fn=v&63;
                    Long baseAddress=registers[rs];
                    boolean structureFlag=baseAddress!=null && baseAddress+(short)imm==0x1ca980L
                            && (op==35 || op==43 || op==32 || op==36 || op==40);
                    boolean flag=imm==0xa980 && (op==35 || op==43 || op==32 || op==36 || op==40);
                    boolean sound=op==3 && ((v&0x3ffffff)<<2)==0x2a1618;
                    boolean byClass=op==3 && ((v&0x3ffffff)<<2)==0x2a16c0;
                    if (flag || structureFlag || sound || byClass) {
                        System.out.printf("PDA SITE 0x%x %s%n",start+at,flag || structureFlag ? "remote-flag":sound ? "class-sound":"class-sound-by-class");
                        int radius=flag || structureFlag ? 112:48;
                        for (int n=Math.max(0,at-radius);n<Math.min(code.length,at+radius+16);n+=4)
                            System.out.printf("  0x%x %s%n",start+n,decode(word(code,n),start+n));
                    }
                    if (op==15) registers[rt]=(long)imm<<16;
                    else if (op==9 || op==25) registers[rt]=baseAddress==null ? null:baseAddress+(short)imm;
                    else if (op==13) registers[rt]=baseAddress==null ? null:baseAddress|imm;
                    else if (op==0 && (fn==33 || fn==45 || fn==37))
                        registers[rd]=registers[rs]==null || registers[rt]==null ? null:fn==37 ? registers[rs]|registers[rt]:registers[rs]+registers[rt];
                    else if (op==35 || op==36 || op==37 || op==32 || op==33 || op==30 || op==49) registers[rt]=null;
                    else if (op==0 && fn!=8 && fn!=9) registers[rd]=null;
                    registers[0]=0L;
                }
                for (int[] range:new int[][]{{0x2ae1a0,0x2ae340},{0x2ae660,0x2ae7b0},{0x2b3130,0x2b32c0},{0x2b4020,0x2b4200}}) {
                    System.out.printf("PDA ENTRY 0x%x..0x%x%n",range[0],range[1]);
                    for (int pc=range[0];pc<range[1];pc+=4)
                        System.out.printf("  0x%x %s%n",pc,decode(word(code,pc-start),pc));
                }
                for (int[] range:new int[][]{{0x2ae544,0x2ae660},{0x2ae7ac,0x2ae7f8},
                        {0x2b07c4,0x2b0868},{0x2b1280,0x2b13e0},{0x2b41fc,0x2b4450}}) {
                    System.out.printf("PDA DETAIL 0x%x..0x%x%n",range[0],range[1]);
                    for (int pc=range[0];pc<range[1];pc+=4)
                        System.out.printf("  0x%x %s%n",pc,decode(word(code,pc-start),pc));
                }
                // Only the class proven to be created by the remote branch, eight referenced UI slots.
                var assets=Rac1LevelAssets.load(lease,1);
                System.out.println("PDA HAND CLASS619 soundDefinitions="+(assets.gadgetClass(619)[13]&255));
                var hero=Rac1HudImporter.overlayRange(overlay,0x240ed8,0x140);
                System.out.println("PDA HERO ENTRY bounded0x240ed8..0x241018; switch table not yet presumed");
                for (int p=0;p<hero.length;p+=4) System.out.printf("  0x%x %s%n",0x240ed8+p,decode(word(hero,p),0x240ed8+p));
                int pdaCase=word(Rac1HudImporter.overlayRange(overlay,0x209350,4),0);
                assertTrue(pdaCase>=0x240ed8 && pdaCase<0x241938);
                assertEquals(0x24145c,pdaCase);
                int caseEnd=0x2414b0; // Item32 exits unconditionally at0x2414a8; do not inspect adjacent cases.
                var pdaCode=Rac1HudImporter.overlayRange(overlay,pdaCase,caseEnd-pdaCase);
                System.out.printf("PDA HERO ITEM32 bounded0x%x..0x%x%n",pdaCase,caseEnd);
                for (int p=0;p<pdaCode.length;p+=4)
                    System.out.printf("  0x%x %s%n",pdaCase+p,decode(word(pdaCode,p),pdaCase+p));
                assertEquals(0x0c000000|(0x2ae1a0>>>2),word(pdaCode,0x2414a0-pdaCase));
                assertEquals(0x0000202d,word(pdaCode,0x2414a4-pdaCase)); // a0=0 in call delay slot.
                var entry=assets.coreIndex().mobyClass(11);
                var blob=assets.classBlob(entry.offset());
                var levelHeader=Rac1LevelHeader.read(lease,tables.levelSector(1));
                var data=Rac1LevelData.read(lease,levelHeader);
                var bank=new com.github.giannigr.racmc.sound.Rac1SoundBank(data.soundBank(lease));
                for (int slot=0;slot<8;slot++) {
                    var definition=com.github.giannigr.racmc.sound.Rac1SoundMap.resolve(assets.coreIndex().bytes(),blob,11,slot);
                    var tone=bank.tone(definition.cue());
                    var decoded=tone.decoded();
                    System.out.printf("PDA CLASS11 SLOT %d cue=%d grain=%d flags=%d center=%d fine=%d frames=%d loop=%s hash=%s%n",
                            slot,definition.cue(),tone.grain(),tone.toneFlags(),tone.centerNote(),tone.fine(),decoded.frames(),decoded.repeat(),tone.adpcmHash());
                }
                lease.checkValid(); assertEquals(1,media.completedAttempts());
            }
        }
    }
    private static String decode(int v,int pc) {
        int op=v>>>26,rs=v>>>21&31,rt=v>>>16&31,rd=v>>>11&31,fn=v&63;
        short imm=(short)v;
        if (v==0) return "nop";
        if (op==2 || op==3) return (op==3 ? "jal":"j")+" 0x"+Integer.toHexString((v&0x3ffffff)<<2);
        if (op==4 || op==5 || op==20 || op==21)
            return "branch"+op+" r"+rs+",r"+rt+" ->0x"+Integer.toHexString(pc+4+imm*4);
        if (op==0) return "special"+fn+" r"+rd+"=r"+rs+",r"+rt;
        return "op"+op+" r"+rt+",r"+rs+",imm="+imm+"(0x"+Integer.toHexString(v&65535)+")";
    }
}
