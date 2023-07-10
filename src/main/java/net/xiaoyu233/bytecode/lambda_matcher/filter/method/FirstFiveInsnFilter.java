package net.xiaoyu233.bytecode.lambda_matcher.filter.method;

import com.mojang.datafixers.util.Either;
import net.xiaoyu233.bytecode.lambda_matcher.EmptyResult;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FirstFiveInsnFilter implements BytecodeFilter{
    @Override
    public @NotNull Either<EmptyResult, List<MethodNode>> filter(MethodNode target, List<MethodNode> candidates, ClassNode targetClass, ClassNode refClass) {
        StringBuilder notMatchReasons = new StringBuilder("Cannot find reference for " + target.name + target.desc + " - None of the candidates matches the first five instructions\n");
        List<MethodNode> collect = candidates.stream().filter(reference -> matches(target, reference).ifRight(notMatchReasons::append).left().isPresent()).collect(Collectors.toList());
        if (collect.isEmpty()){
            return Either.left(new EmptyResult(notMatchReasons.toString()));
        }else return Either.right(collect);
    }

    private Either<Boolean,String> matches(MethodNode target,MethodNode reference){
        int index = 0;
        List<AbstractInsnNode> refInstructions = filterInsn(reference.instructions);
        for (AbstractInsnNode instruction : filterInsn(target.instructions)) {
            if (index > 5){
                return Either.left(true);
            }
            AbstractInsnNode insnNode = refInstructions.get(index);
            if (!BytecodeFilter.insnEquals(instruction, insnNode,target,reference)) {
                return Either.right("    Insn not equals at " + index + " : "+ instruction + " <==> " + insnNode + "\n");
            }
            index++;
        }
        return Either.left(true);
    }

    private List<AbstractInsnNode> filterInsn(InsnList instructions){
        ArrayList<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : instructions) {
            if (BytecodeFilter.shouldCompareInsn(instruction)){
                result.add(instruction);
            }
        }
        return result;
    }
}
