package com.github.giannigr.racmc.client.screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh JVMs establish the order dependence without opening a window or changing this JVM. */
class Rac1AwtHeadlessStateTest {
    @TempDir Path temp;

    @Test void settingPropertyEarlyDiffersFromSettingItAfterEffectiveStateWasRead() throws Exception {
        var source=temp.resolve("HeadlessStateProbe.java");
        Files.writeString(source, """
                public class HeadlessStateProbe {
                    public static void main(String[] args) {
                        if (args[0].equals("late") && !java.awt.GraphicsEnvironment.isHeadless())
                            throw new AssertionError("Expected initial effective headless state");
                        System.setProperty("java.awt.headless", "false");
                        System.out.println(System.getProperty("java.awt.headless") + "/"
                                + java.awt.GraphicsEnvironment.isHeadless());
                    }
                }
                """);
        assertEquals("false/false",probe(source,"early"));
        assertEquals("false/true",probe(source,"late"));
    }

    private String probe(Path source,String order) throws Exception {
        var java=Path.of(System.getProperty("java.home"),"bin","java");
        if (!Files.isExecutable(java)) java=java.resolveSibling("java.exe");
        var process=new ProcessBuilder(java.toString(),"-Djava.awt.headless=true",source.toString(),order)
                .redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(20,TimeUnit.SECONDS),"AWT state probe timed out");
            var output=new String(process.getInputStream().readAllBytes()).strip();
            assertEquals(0,process.exitValue(),output);
            return output.lines().reduce((first,last) -> last).orElse("");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
