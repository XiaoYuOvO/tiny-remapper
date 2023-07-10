package net.xiaoyu233.bytecode.lambda_matcher.filter.method;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.xiaoyu233.bytecode.lambda_matcher.EmptyResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

public class ContextFilter implements BytecodeFilter{
    private static final Collection<Handle> META_FACTORIES = Arrays.asList(
            new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
            new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "altMetafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false)
    );

    private Map<MethodNode,LambdaContext> usageCache = new HashMap<>();
    @Override
    public @NotNull Either<EmptyResult, List<MethodNode>> filter(MethodNode target, List<MethodNode> candidates, ClassNode targetClass, ClassNode refClass) {
        LambdaContext targetContext = this.searchLambdaContext(targetClass,target);
        List<Pair<MethodNode,LambdaContext>> firstPassMethodNodes = new ArrayList<>();
        for (MethodNode candidate : candidates) {
            LambdaContext candidateContext = this.searchLambdaContext(refClass, candidate);
            if (candidateContext != null && targetContext != null) {
                if (candidateContext.refMethodDesc.equals(targetContext.refMethodDesc) && candidateContext.refMethodName.equals(targetContext.refMethodName)) {
                    firstPassMethodNodes.add(Pair.of(candidate, candidateContext));
                }
            } else {
                //Cannot determine if the context is null so throw it to next filter
                firstPassMethodNodes.add(Pair.of(candidate, null));
            }
        }

        List<Pair<MethodNode,LambdaContext>> secondPass = new ArrayList<>();
        if (firstPassMethodNodes.size() == 1){
            return Either.right(Lists.newArrayList(firstPassMethodNodes.get(0).getFirst()));
        }else if (firstPassMethodNodes.isEmpty()){
            StringBuilder sb = new StringBuilder();
            sb.append("Lambda context first pass matched no methods\n");
            sb.append("\tTarget lambda context: \n\t\t").append(targetContext).append("\n");
            sb.append("\tCandidate lambda context: \n");
            for (MethodNode candidate : candidates) {
                sb.append("\t\t").append(candidate.name).append(candidate.desc).append(" @ ").append(usageCache.get(candidate)).append("\n");
            }
            return Either.left(new EmptyResult(sb.toString()));
        }else {
            StringBuilder sb = new StringBuilder();
            sb.append("Lambda context second pass matched no methods\n");
            sb.append("\tTarget lambda context: \n\t\t").append(targetContext).append("\n");
            sb.append("\tCandidate lambda context: \n");
            for (Pair<MethodNode, LambdaContext> firstPassMethodNode : firstPassMethodNodes) {
                LambdaContext context = firstPassMethodNode.getSecond();
                MethodNode node = firstPassMethodNode.getFirst();
                sb.append("\t\t").append(node.name).append(node.desc).append(" @ ").append(context).append("\n");
                //Cannot determine if the context is null so throw it to next filter
                if (context == null || targetContext == null || (( context.refIndex == targetContext.refIndex && context.totalLambdaInRefMethod == targetContext.totalLambdaInRefMethod))){
                    secondPass.add(firstPassMethodNode);
                }
            }
            if (secondPass.isEmpty()){
                return Either.left(new EmptyResult(sb.toString()));
            }else if (secondPass.size() == 1){
                return Either.right(Lists.newArrayList(secondPass.get(0).getFirst()));
            }else {
                List<MethodNode> thirdPass = new ArrayList<>();
                for (Pair<MethodNode, LambdaContext> second : secondPass) {
                    LambdaContext context = second.getSecond();
                    if (context == null || targetContext == null || (context.usage != null && targetContext.usage != null && context.usage.equals(targetContext.usage))){
                        thirdPass.add(second.getFirst());
                    }
                }

//                if (thirdPass.size() > 1){
//                    StringBuilder builder = new StringBuilder();
//                    builder.append("Lambda context third pass matched too many methods\n");
//                    builder.append("\tTarget lambda context: \n\t\t").append(targetContext).append("\n");
//                    builder.append("\tCandidate lambda context: \n");
//                    for (MethodNode node : thirdPass) {
//                        builder.append("\t\t").append(node.name).append(node.desc).append(" @ ").append(usageCache.get(node)).append("\n");
//                    }
//                    return Either.left(new EmptyResult(builder.toString()));
//                }

                return Either.right(thirdPass);
            }


        }
    }

    /**
     * Search the lambda method's usage in the class file
     * We assume than a lambda method will only have a usage even through there's other lambda methods with same instructions
     * */
    private LambdaContext searchLambdaContext(ClassNode classNode,MethodNode lambda){
        int foundCount = 0;
        //Use the cache
        if (usageCache.containsKey(lambda)){
            return usageCache.get(lambda);
        }

        LambdaContext lambdaContext = null;
        for (MethodNode method : classNode.methods) {
            boolean isLambda = (method.access & Opcodes.ACC_SYNTHETIC) == Opcodes.ACC_SYNTHETIC;
            boolean lambdaFound = false;
            Type invokeDynRet = null;
            int insnIndex = 0;
            int totalLambdas = 0;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof InvokeDynamicInsnNode){
                    //Find lambda if missing
                    if (!lambdaFound){
                        InvokeDynamicInsnNode invdyn = (InvokeDynamicInsnNode) instruction;
                        String desc = invdyn.desc;
                        Handle bsm = invdyn.bsm;
                        Object[] bsmArgs = invdyn.bsmArgs;
                        if (META_FACTORIES.contains(bsm)) {
                            Handle lambdaMethod = ((Handle) bsmArgs[1]);
                            totalLambdas++;
                            lambdaFound = lambdaMethod.getName().equals(lambda.name) && lambdaMethod.getDesc().equals(lambda.desc);
                            if (lambdaFound) {
                                if (isLambda){
                                    LambdaContext parentContext = this.searchLambdaContext(classNode, method);
                                    lambdaContext = new LambdaContext(parentContext.refMethod,parentContext.refMethodName,parentContext.refMethodDesc,parentContext.refIndex * 7 + insnIndex);
                                }else {
                                    lambdaContext = new LambdaContext(method, method.name,method.desc,insnIndex);
                                }
                                foundCount++;
                                invokeDynRet = Type.getReturnType(desc);
                            }
                        }
                    }
                    insnIndex++;
                }

                if (instruction instanceof MethodInsnNode) {
                    if (lambdaFound){
                        MethodInsnNode methodInsn = (MethodInsnNode) instruction;
                        String desc = methodInsn.desc;
                        for (Type argumentType : Type.getArgumentTypes(desc)) {
                            //Find lambda's true consuming method by look up args
                            //TODO: might not accurate here without arg index comparing
                            if (argumentType.equals(invokeDynRet)){
                                lambdaContext.setUsage(LambdaUsage.ofMethodInsn(methodInsn));
                                //Consumed
                                lambdaFound = false;
                            }
                        }
                    }
                }
                if (instruction instanceof FieldInsnNode){
                    if (lambdaFound){
                        FieldInsnNode fieldInsnNode = (FieldInsnNode) instruction;
                        if (fieldInsnNode.getOpcode() == Opcodes.PUTFIELD || fieldInsnNode.getOpcode() == Opcodes.PUTSTATIC && Type.getType(fieldInsnNode.desc).equals(invokeDynRet)){
                            lambdaContext.setUsage(LambdaUsage.ofFieldInsn(fieldInsnNode));
                            //Consumed
                            lambdaFound = false;
                        }
                    }
                }

                if (instruction instanceof VarInsnNode) {
                    if (lambdaFound){
                        VarInsnNode varInsn = (VarInsnNode) instruction;
                        if (varInsn.getOpcode() == Opcodes.ALOAD && method.localVariables.size() > varInsn.var && Type.getType(method.localVariables.get(varInsn.var).desc).equals(invokeDynRet)){
                            lambdaContext.setUsage(LambdaUsage.ofVarInsn(varInsn));
                            //Consumed
                            lambdaFound = false;
                        }
                    }
                }

                if (instruction instanceof InsnNode){
                    if (lambdaFound && instruction.getOpcode() == Opcodes.ARETURN){
                        lambdaContext.setUsage(LambdaUsage.ofReturnInsn((InsnNode) instruction));
                        lambdaFound = false;
                    }
                }

            }
            if (lambdaContext != null){
                lambdaContext.setTotalLambdaInRefMethod(totalLambdas);
            }
        }
        if (foundCount > 1){
            System.err.println("[Lambda Usage Search] Found more than one usage for lambda method \n\t" + lambda.name + lambda.desc + "\n in " + classNode.name);
        }
        usageCache.put(lambda,lambdaContext);
        return lambdaContext;
    }

    private static class LambdaContext{
        private final MethodNode refMethod;
        private final String refMethodName;
        private final String refMethodDesc;
        private final int refIndex;
        private int totalLambdaInRefMethod;
        @Nullable
        private LambdaUsage<?> usage;

        private LambdaContext(MethodNode refMethod, String refMethodName, String refMethodDesc, int refIndex) {
            this.refMethod = refMethod;
            this.refMethodName = refMethodName;
            this.refMethodDesc = refMethodDesc;
            this.refIndex = refIndex;
        }

        public void setTotalLambdaInRefMethod(int totalLambdaInRefMethod) {
            this.totalLambdaInRefMethod = totalLambdaInRefMethod;
        }

        public void setUsage(LambdaUsage<?> usage) {
            this.usage = usage;
        }

        @Override
        public String toString() {
            return "LambdaContext{" +
                    "refMethodName='" + refMethodName + '\'' +
                    ", refMethodDesc='" + refMethodDesc + '\'' +
                    ", refIndex=" + refIndex +
                    ", totalLambdaInRefMethod=" + totalLambdaInRefMethod +
                    ", usage=" + usage +
                    '}';
        }
    }

    private interface LambdaUsage<T extends AbstractInsnNode>{
        static LambdaUsage<MethodInsnNode> ofMethodInsn(MethodInsnNode methodInsnNode){
            return new LambdaUsage<MethodInsnNode>() {
                @Override
                public MethodInsnNode getInsn() {
                    return methodInsnNode;
                }

                @Override
                public boolean equals(LambdaUsage<?> t) {
                    AbstractInsnNode insn = t.getInsn();
                    if (insn instanceof MethodInsnNode){
                        MethodInsnNode methodInsn = (MethodInsnNode) insn;
                        return methodInsn.getOpcode() == methodInsnNode.getOpcode() && methodInsn.name.equals(methodInsnNode.name) && methodInsn.desc.equals(methodInsnNode.desc);
                    }
                    return false;
                }

                @Override
                public String toString() {
                    return "MethodInsn[" + methodInsnNode.owner + "." +methodInsnNode.name + methodInsnNode.desc +"]";
                }
            };
        }

        static LambdaUsage<FieldInsnNode> ofFieldInsn(FieldInsnNode fieldInsnNode){
            return new LambdaUsage<FieldInsnNode>() {
                @Override
                public FieldInsnNode getInsn() {
                    return fieldInsnNode;
                }

                @Override
                public boolean equals(LambdaUsage<?> t) {
                    AbstractInsnNode insn = t.getInsn();
                    if (insn instanceof FieldInsnNode){
                        FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                        return fieldInsn.getOpcode() == fieldInsnNode.getOpcode() && fieldInsn.name.equals(fieldInsnNode.name) && fieldInsn.desc.equals(fieldInsnNode.desc);
                    }
                    return false;
                }

                @Override
                public String toString() {
                    return "FieldInsn[" + fieldInsnNode.owner + "." + fieldInsnNode.name + fieldInsnNode.desc + "]";
                }
            };
        }

        static LambdaUsage<VarInsnNode> ofVarInsn(VarInsnNode varInsnNode){
            return new LambdaUsage<VarInsnNode>() {
                @Override
                public VarInsnNode getInsn() {
                    return varInsnNode;
                }

                @Override
                public boolean equals(LambdaUsage<?> t) {
                    AbstractInsnNode insn = t.getInsn();
                    if (insn instanceof VarInsnNode){
                        VarInsnNode varInsn = (VarInsnNode) insn;
                        return varInsn.getOpcode() == varInsnNode.getOpcode() && varInsn.var == varInsnNode.var && varInsn.getType() == varInsnNode.getType();
                    }
                    return false;
                }

                @Override
                public String toString() {
                    return "VarInsn[" + varInsnNode.var + "]";
                }
            };
        }
        static LambdaUsage<InsnNode> ofReturnInsn(InsnNode insn){
            return new LambdaUsage<InsnNode>() {
                @Override
                public InsnNode getInsn() {
                    return insn;
                }

                @Override
                public boolean equals(LambdaUsage<?> t) {
                    AbstractInsnNode absinsn = t.getInsn();
                    if (absinsn instanceof InsnNode){
                        InsnNode insnNode = (InsnNode) absinsn;
                        return insnNode.getOpcode() == insn.getOpcode();
                    }
                    return false;
                }

                @Override
                public String toString() {
                    return "ReturnInsn";
                }
            };
        }

        T getInsn();
        boolean equals(LambdaUsage<?> t);
    }
}
