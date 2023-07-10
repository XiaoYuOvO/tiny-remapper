package net.xiaoyu233.bytecode.lambda_matcher.filter.field;

import com.mojang.datafixers.util.Either;
import net.xiaoyu233.bytecode.lambda_matcher.EmptyResult;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.List;
import java.util.stream.Collectors;

public class SignatureFilter implements FieldFilter{
    @Override
    public @NotNull Either<EmptyResult, List<FieldNode>> filter(FieldNode target, List<FieldNode> candidates, ClassNode targetClass, ClassNode refClass) {
        List<FieldNode> collect = candidates.stream().filter((fieldNode -> fieldNode.desc.equals(target.desc))).collect(
                Collectors.toList());
        if (collect.isEmpty()){
            return Either.left(new EmptyResult("Cannot find a matching field with signature: " + target.desc + " of mehtod " + target.name));
        }else {
            return Either.right(collect);
        }
    }
}
