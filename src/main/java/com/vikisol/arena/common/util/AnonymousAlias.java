package com.vikisol.arena.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Display names for anonymous posts, replies and chats ("Anonymous Otter"). Deterministic from a
 * seed, so the same person gets the same alias everywhere the seed is the same (one thread, one
 * chat) - and a different, unlinkable one anywhere else. Seeds always include a per-thread or
 * per-chat id, never a user id alone.
 */
public final class AnonymousAlias {

    public static final String EMOJI = "🎭";

    private static final List<String> ANIMALS = List.of(
            "Otter", "Falcon", "Panda", "Lynx", "Heron", "Koala", "Badger", "Dolphin", "Gecko", "Ibis",
            "Jaguar", "Kestrel", "Lemur", "Marten", "Narwhal", "Ocelot", "Puffin", "Quokka", "Raven", "Stork",
            "Tapir", "Urchin", "Vole", "Walrus", "Yak", "Zebra", "Bison", "Crane", "Dingo", "Egret",
            "Ferret", "Gazelle", "Hornbill", "Impala", "Jackal", "Kiwi", "Llama", "Mongoose", "Newt", "Oriole",
            "Peacock", "Robin", "Sparrow", "Tiger", "Wombat", "Mynah", "Langur", "Chital", "Gharial", "Bulbul");

    private AnonymousAlias() {
    }

    public static String of(String seed) {
        return "Anonymous " + ANIMALS.get(bucket(seed, ANIMALS.size()));
    }

    private static int bucket(String seed, int size) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            int v = ((digest[0] & 0xff) << 16) | ((digest[1] & 0xff) << 8) | (digest[2] & 0xff);
            return v % size;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
