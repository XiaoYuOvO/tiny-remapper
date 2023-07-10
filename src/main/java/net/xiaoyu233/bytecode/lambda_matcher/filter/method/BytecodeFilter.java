package net.xiaoyu233.bytecode.lambda_matcher.filter.method;

import com.mojang.datafixers.util.Either;
import net.xiaoyu233.bytecode.lambda_matcher.EmptyResult;
import net.xiaoyu233.bytecode.lambda_matcher.filter.IMemberFilter;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.*;

import java.util.List;

public interface BytecodeFilter extends IMemberFilter<MethodNode> {
    @NotNull
    Either<EmptyResult, List<MethodNode>> filter(MethodNode target,List<MethodNode> candidates,ClassNode targetClass,ClassNode refClass);
    static boolean shouldCompareInsn(AbstractInsnNode insn){
        return !(insn instanceof LineNumberNode) && !(insn instanceof InvokeDynamicInsnNode) && !(insn instanceof LabelNode);
    }

    static boolean insnEquals(AbstractInsnNode a,AbstractInsnNode b,MethodNode aNode,MethodNode bNode){
        if (a.getClass().equals(b.getClass())){
            if (a.getOpcode() == b.getOpcode()){
                if (a.getType() == b.getType()){
                    if (a instanceof MethodInsnNode){
                        MethodInsnNode methodInsnA = (MethodInsnNode) a;
                        MethodInsnNode methodInsnB = (MethodInsnNode) b;
                        return methodInsnA.name.equals(methodInsnB.name) && methodInsnA.desc.equals(methodInsnB.desc) && methodInsnA.owner.equals(methodInsnB.owner);
                    }else if (a instanceof FieldInsnNode){
                        FieldInsnNode fieldInsnA = (FieldInsnNode) a;
                        FieldInsnNode fieldInsnB = (FieldInsnNode) b;
                        return fieldInsnA.name.equals(fieldInsnB.name) && fieldInsnA.desc.equals(fieldInsnB.desc) && fieldInsnA.owner.equals(fieldInsnB.owner);
                    }else if ((a instanceof VarInsnNode && b instanceof FieldInsnNode)){
                        VarInsnNode varA = (VarInsnNode) a;
                        FieldInsnNode fieldB = (FieldInsnNode) b;
                        return aNode.localVariables.get(varA.var).desc.equals(fieldB.desc);
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
