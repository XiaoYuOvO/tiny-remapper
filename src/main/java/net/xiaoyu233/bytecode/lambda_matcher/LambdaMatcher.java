package net.xiaoyu233.bytecode.lambda_matcher;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.xiaoyu233.bytecode.lambda_matcher.filter.method.AllInsnFilter;
import net.xiaoyu233.bytecode.lambda_matcher.filter.method.ContextFilter;
import net.xiaoyu233.bytecode.lambda_matcher.filter.method.FirstFiveInsnFilter;
import net.xiaoyu233.bytecode.lambda_matcher.filter.method.SignatureFilter;
import net.xiaoyu233.bytecode.lambda_matcher.processor.SyntheticProcessor;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

public class LambdaMatcher {
    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser() {
            {
                accepts("targetJar","The target jar to look for lambda name")
                        .withRequiredArg()
                        .ofType(File.class);;
                accepts("referenceJar","The jar file to search lambda name")
                        .withRequiredArg()
                        .ofType(File.class);;
            }
        };
        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException ex) {
            System.out.println(ex.getLocalizedMessage());
            System.exit(-1);
            return;
        }

        if (options == null || options.has("?")) {
            try {
                parser.printHelpOn(System.err);
            } catch (IOException ex) {
                System.out.println(ex.getLocalizedMessage());
            }
            System.exit(-1);
            return;
        }

        File targetJar = (File) options.valueOf("targetJar");
        File referenceJar = (File) options.valueOf("referenceJar");
        JarClassProvider target = new JarClassProvider(new JarFile(targetJar));
        JarClassProvider reference = new JarClassProvider(new JarFile(referenceJar));
        SyntheticProcessor processor = new SyntheticProcessor(reference);
        processor.addMethodFilter(new SignatureFilter());
        processor.addMethodFilter(new ContextFilter());
        processor.addMethodFilter(new FirstFiveInsnFilter());
        processor.addMethodFilter(new AllInsnFilter());
        processor.addFieldFilter(new net.xiaoyu233.bytecode.lambda_matcher.filter.field.SignatureFilter());
        target.addProcessor(processor);
        target.run();
    }
}
