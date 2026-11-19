package com.github.giannigr.racmc.asset.clank;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.cache.Rac1CacheCodec;
import java.io.IOException;
import java.util.HashSet;

/** Separate optional609 assembly. Existing three-role CLNK envelope and equipment save format stay unchanged. */
public final class Rac1HydroAsset {
    private final String discKey;
    private final byte[] encoded;
    public Rac1HydroAsset(String discKey,Rac1AnimatedAsset asset)throws IOException {
        Rac1ClankAssets.requireDiscKey(discKey);validate(asset);
        this.discKey=discKey;encoded=Rac1CacheCodec.encodeAnimatedModel(asset);
        validate(Rac1CacheCodec.decodeAnimatedModel(encoded));
    }
    private static void validate(Rac1AnimatedAsset asset)throws IOException {
        if(asset==null||asset.classNumber()!=609||asset.skeleton().jointCount()!=10
                ||asset.mesh().vertexCount()==0||asset.textures().length==0||asset.animations().length!=5)
            throw new IOException("Wrong/incomplete original Hydro609");
        var sequences=new HashSet<String>();
        for(var clip:asset.animations()) {
            if(!sequences.add(clip.name())||clip.keyCount()==0||!clip.sourceTiming().available())
                throw new IOException("Invalid Hydro source animation");
        }
        for(int i=0;i<5;i++)if(!sequences.contains("sequence_"+i))throw new IOException("Missing Hydro clip"+i);
    }
    public String discKey(){return discKey;}
    public Rac1AnimatedAsset copyModel()throws IOException{return Rac1CacheCodec.decodeAnimatedModel(encoded);}
}
