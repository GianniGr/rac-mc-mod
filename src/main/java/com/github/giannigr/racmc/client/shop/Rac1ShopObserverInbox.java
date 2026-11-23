package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.Rac1ShopObserverNetwork.State;
import java.util.*;
import java.lang.ref.WeakReference;

/** Main-client-thread, bounded revision/incarnation fence, scoped to one live client world. */
public final class Rac1ShopObserverInbox<K> {
    private record Entry(WeakReference<Object> instance,State state) {}
    private final Map<K,Entry> states=new LinkedHashMap<>();
    public boolean receive(K key,Object instance,State state) {
        Objects.requireNonNull(instance); Objects.requireNonNull(state);
        var old=states.get(key);
        if (old!=null && (state.revision()<old.state.revision()
                || state.revision()==old.state.revision() && (!state.incarnation().equals(old.state.incarnation())
                || old.instance.get()==instance))) return false;
        if (old==null && states.size()>=4096) return false;
        states.put(key,new Entry(new WeakReference<>(instance),state)); return true;
    }
    public State state(K key,Object instance) {
        var entry=states.get(key);
        return entry!=null && entry.instance.get()==instance ? entry.state:null;
    }
    /** Retain only the revision fence after unload; never pin a chunk/BE instance. */
    public void unload(K key) {
        var entry=states.get(key);
        if (entry!=null) states.put(key,new Entry(new WeakReference<>(null),entry.state));
    }
    public void clear() { states.clear(); }
}
