package net.xiaoyu233.bytecode.lambda_matcher.filter.method;

import com.mojang.datafixers.util.Either;
import net.xiaoyu233.bytecode.lambda_matcher.EmptyResult;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.stream.Collectors;

public class SignatureFilter implements BytecodeFilter{
    @Override
    public @NotNull Either<EmptyResult, List<MethodNode>> filter(MethodNode target, List<MethodNode> candidates, ClassNode targetClass, ClassNode refClass) {
        List<MethodNode> collect = candidates.stream().filter((methodNode -> methodNode.desc.equals(target.desc))).collect(Collectors.toList());
        if (collect.isEmpty()){
            return Either.left(new EmptyResult("Cannot find a matching method with signature: " + target.desc + " of mehtod " + target.name));
        }else {
            return Either.right(collect);
        }
    }
}
