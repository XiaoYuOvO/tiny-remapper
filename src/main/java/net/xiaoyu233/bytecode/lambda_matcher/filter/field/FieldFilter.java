package net.xiaoyu233.bytecode.lambda_matcher.filter.field;

import com.mojang.datafixers.util.Either;
import net.xiaoyu233.bytecode.lambda_matcher.EmptyResult;
import net.xiaoyu233.bytecode.lambda_matcher.filter.IMemberFilter;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public interface FieldFilter extends IMemberFilter<FieldNode> {
    @NotNull
    Either<EmptyResult, List<FieldNode>> filter(FieldNode target, List<FieldNode> candidates, ClassNode targetClass, ClassNode refClass);
}
