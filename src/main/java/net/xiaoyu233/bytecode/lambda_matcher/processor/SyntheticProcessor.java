package net.xiaoyu233.bytecode.lambda_matcher.processor;

import com.mojang.datafixers.util.Either;
import net.minecraftforge.srgutils.IMappingBuilder;
import net.minecraftforge.srgutils.IMappingFile;
import net.xiaoyu233.bytecode.lambda_matcher.EmptyResult;
import net.xiaoyu233.bytecode.lambda_matcher.JarClassProvider;
import net.xiaoyu233.bytecode.lambda_matcher.filter.IMemberFilter;
import net.xiaoyu233.bytecode.lambda_matcher.filter.field.FieldFilter;
import net.xiaoyu233.bytecode.lambda_matcher.filter.method.BytecodeFilter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.stream.Collectors;

public class SyntheticProcessor implements JarFileProcessor {
    private final JarClassProvider referenceJar;
    private final List<BytecodeFilter> methodFilters = new ArrayList<>();
    private final List<FieldFilter> fieldFilter = new ArrayList<>();
    private final List<String> matched = new ArrayList<>();
    private int failedCount;
    private IMappingBuilder mapping;
    private final PrintWriter errWriter = new PrintWriter(new FileWriter("lambda_match_err.txt",false));
    private final Map<String, IMappingBuilder.IClass> classMap = new HashMap<>();

    public SyntheticProcessor(JarClassProvider referenceJar) throws IOException {
        this.referenceJar = referenceJar;
    }

    public void addMethodFilter(BytecodeFilter filter){
        this.methodFilters.add(filter);
    }

    public void addFieldFilter(FieldFilter filter){
        this.fieldFilter.add(filter);
    }

    private static boolean syntheticMethod(MethodNode method) {
        return (Opcodes.ACC_SYNTHETIC & method.access) == Opcodes.ACC_SYNTHETIC;
    }

    private static boolean syntheticField(FieldNode field) {
        return (Opcodes.ACC_SYNTHETIC & field.access) == Opcodes.ACC_SYNTHETIC;
    }

    private static boolean bridgeMethod(MethodNode method){
        return (Opcodes.ACC_SYNTHETIC & method.access) == Opcodes.ACC_SYNTHETIC && (Opcodes.ACC_BRIDGE & method.access) == Opcodes.ACC_BRIDGE;
    }

    @Override
    public void startProcess(String fileName) {
        mapping = IMappingBuilder.create("left","right");
    }

    @Override
    public void finishProcess() {
        try {
            mapping.build().write(new File("./lambda_match.tiny").toPath(), IMappingFile.Format.TINY);
            errWriter.write("Total failed matching: " + failedCount);
            errWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean suitableFor(JarEntry entry) {
        return entry.getName().endsWith(".class");
    }

    @Override
    public void process(JarEntry entry, InputStream in) throws IOException {
        ClassReader targetClassReader = new ClassReader(in);
        ClassNode targetClass = new ClassNode();
        targetClassReader.accept(targetClass,0);
        ClassReader refClassReader = new ClassReader(referenceJar.findFile(entry.getName()));
        ClassNode refClass = new ClassNode();
        refClassReader.accept(refClass,0);
        List<MethodNode> targetMethods = targetClass.methods.stream().filter(SyntheticProcessor::syntheticMethod).collect(Collectors.toList());
        List<MethodNode> refMethods = refClass.methods.stream().filter(SyntheticProcessor::syntheticMethod).collect(Collectors.toList());
        List<FieldNode> targetFields = targetClass.fields.stream().filter(SyntheticProcessor::syntheticField).collect(Collectors.toList());
        List<FieldNode> refFields = refClass.fields.stream().filter(SyntheticProcessor::syntheticField).collect(Collectors.toList());
        this.matchLambdaNames(targetClass.name,targetMethods,refMethods,targetClass,refClass);
        this.matchSyntheticFields(targetClass.name,targetFields,refFields,targetClass,refClass);
    }

    private void matchSyntheticFields(String className,List<FieldNode> targetFields,List<FieldNode> refFields,ClassNode targetClass,ClassNode refClass){
        this.matchCandidates(MatchingType.FIELD,className,targetFields,refFields,targetClass,refClass,this.fieldFilter);
    }

    private void matchLambdaNames(String className,List<MethodNode> targetMethods,List<MethodNode> refMethods,ClassNode targetClass,ClassNode refClass){
        this.matchCandidates(MatchingType.METHOD,className,targetMethods,refMethods,targetClass,refClass,this.methodFilters);
    }

    private <T,F extends IMemberFilter<T>> void matchCandidates(MatchingType<T,F> type,String className,List<T> targets,List<T> references,ClassNode targetClass,ClassNode refClass,List<F> filters){
        List<T> backup = new ArrayList<>(references);
        IMemberInfoGetter<T> getter = type.getInfoGetter();
        for (T target : targets) {
            for (F filter : filters) {
                Either<EmptyResult, List<T>> filterResult = filter.filter(target, references,targetClass,refClass);
                Optional<EmptyResult> left = filterResult.left();
                Optional<List<T>> right = filterResult.right();
                if (left.isPresent()) {
                    errWriter.println("No matching reference for " + className + "." + getter.getName(target) + getter.getDesc(target) + " was found");
                    errWriter.println("    " + left.get().getInfo());
                    failedCount++;
                    references.clear();
                    break;
                }else if (right.isPresent()){
                    List<T> matched = right.get();
                    references.clear();
                    references.addAll(matched);
                    if (references.size() == 1) {
                        T ref = references.get(0);
                        System.out.println(type.getName() + " matched: " + className + "." + getter.getName(target) + " -> " + getter.getName(ref));
                        String key = className + "." + getter.getName(target) + getter.getDesc(target);
                        if (!this.matched.contains(key)) {
                            IMappingBuilder.IClass iClass = classMap.computeIfAbsent(className,
                                    (name) -> mapping.addClass(name, name));
                            type.mappingApplier.applyToMap(iClass,target,ref);
                            this.matched.add(key);
                        }
                        //Remove the selected one
                        backup.remove(ref);
                        break;
                    }
                }
            }
            if (references.size() > 1){
                errWriter.println(type.getName() + " " + className + "."+ getter.getName(target) + " "+ getter.getDesc(target) + " has multiple matched candidates");
                errWriter.println("    They are:");
                for (T ref : references) {
                    errWriter.println("    " + getter.getName(ref) + " "+ getter.getDesc(ref));
                }
                failedCount++;
            }
            //Resume the refMethods but no the selected one
            references.clear();
            references.addAll(backup);
        }
    }

    interface IMemberInfoGetter<T>{
        String getName(T member);
        String getDesc(T member);
    }

    interface IMappingApplier<T>{
        void applyToMap(IMappingBuilder.IClass cls,T target,T ref);
    }

    static class MatchingType<T,F extends IMemberFilter<T>>{
        public static final MatchingType<MethodNode,BytecodeFilter> METHOD = new MatchingType<>("Method",
                new IMemberInfoGetter<MethodNode>() {
                    @Override
                    public String getName(MethodNode member) {
                        return member.name;
                    }

                    @Override
                    public String getDesc(MethodNode member) {
                        return member.desc;
                    }
                }, ((cls, target, ref) -> cls.method(ref.desc, target.name, ref.name)));
        public static final MatchingType<FieldNode, FieldFilter> FIELD = new MatchingType<>("Field", new IMemberInfoGetter<FieldNode>(){
            @Override
            public String getName(FieldNode member) {
                return member.name;
            }

            @Override
            public String getDesc(FieldNode member) {
                return member.desc;
            }
        }, (cls,target,ref) -> cls.field(target.name,ref.name).descriptor(target.desc));
        private final String name;
        private final IMemberInfoGetter<T> infoGetter;
        private final IMappingApplier<T> mappingApplier;
        private MatchingType(String name, IMemberInfoGetter<T> infoGetter, IMappingApplier<T> mappingApplier) {
            this.name = name;
            this.infoGetter = infoGetter;
            this.mappingApplier = mappingApplier;
        }

        public String getName() {
            return name;
        }

        public IMemberInfoGetter<T> getInfoGetter() {
            return infoGetter;
        }
    }
}
