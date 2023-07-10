package net.fabricmc.tinyremapper.extension.autoremap;

import net.fabricmc.tinyremapper.api.TrRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.util.*;

public class AutoObfuscator extends Remapper {
    private TrRemapper defaultRemapper;
    private Map<String,String> obfuscatedMap = new HashMap<>();
    private String autoRemapPrefix = "net/minecraft/";
    private List<String> autoRemapFilter = new ArrayList<>();
    private int autoRemapNameIndex = 1;
    private FileWriter writer;

    public AutoObfuscator() throws IOException {
        setOutput(new File("./auto_obfuscate.tiny"));
    }

    public void setOutput(File outFile) throws IOException {
        writer = new FileWriter(outFile,true);
        checkForTinyHeader(outFile);
    }

    private void checkForTinyHeader(File file) throws IOException {
        try (Scanner fileReader = new Scanner(new FileReader(file))) {
            //Empty file -> Write header
            if (!fileReader.hasNextLine() || fileReader.nextLine().trim().isEmpty()) {
                this.writer.write("tiny\t2\t0\tleft\tright\n");
            }
        }
    }


    public void setDefaultRemapper(TrRemapper defaultRemapper) {
        this.defaultRemapper = defaultRemapper;
    }

    public void setAutoRemapPrefix(String autoRemapPrefix) {
        this.autoRemapPrefix = autoRemapPrefix;
    }

    public void addAutoRemapFilter(List<String> autoRemapFilter) {
        this.autoRemapFilter.addAll(autoRemapFilter);
    }

    @Override
    public synchronized String map(String internalName) {
        return obfuscatedMap.computeIfAbsent(internalName,(key)-> this.getNextAutoRemapName(this.defaultRemapper.getClassMap(),internalName));
    }

    public String getNextAutoRemapName(Map<String,String> classes,String className){
        if (autoRemapFilter.isEmpty() || autoRemapFilter.stream().anyMatch(className::contains)) {
            if (classes != null) {
                String next = autoRemapPrefix + toBase26(autoRemapNameIndex);
                while (classes.containsValue(next)) {
                    next = autoRemapPrefix + toBase26(autoRemapNameIndex++);
                }
                classes.put(className, next);
                try {
                    writer.write("c\t" + className + "\t" + next+ "\n");
                    writer.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return next;
            }else {
                return className;
            }
        }else return className;
    }

    public static String toBase26(int n) {
        StringBuilder s = new StringBuilder();
        while (n > 0){
            n--;
            int m = n % 26;
            s.insert(0, (char) (m + 'a'));
            n = (n - m) / 26;
        }
        return s.toString();
    }
}
