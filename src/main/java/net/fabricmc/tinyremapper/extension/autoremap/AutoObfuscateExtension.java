package net.fabricmc.tinyremapper.extension.autoremap;

import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class AutoObfuscateExtension implements TinyRemapper.Extension {
    private final String autoObfPrefix;
    private final List<String> autoObfFilters;
    private File autoObfMappingFile = new File("./auto_obfuscate.tiny");

    public AutoObfuscateExtension(String autoObfPrefix, List<String> autoObfFilters) {
        this.autoObfPrefix = autoObfPrefix;
        this.autoObfFilters = autoObfFilters;
    }

    public void setOutputFile(File autoObfMappingFile) {
        this.autoObfMappingFile = autoObfMappingFile;
    }

    @Override
    public void attach(TinyRemapper.Builder builder) {
        try {
            AutoObfuscator remapper = new AutoObfuscator();
            remapper.setAutoRemapPrefix(autoObfPrefix);
            remapper.addAutoRemapFilter(autoObfFilters);
            remapper.setOutput(autoObfMappingFile);
            builder.extraRemapper(remapper).extraStateProcessor(env ->
                    remapper.setDefaultRemapper(env.getRemapper())
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
