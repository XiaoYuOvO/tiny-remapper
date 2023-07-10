package net.xiaoyu233.bytecode.lambda_matcher.filter;

import com.mojang.datafixers.util.Either;
import net.xiaoyu233.bytecode.lambda_matcher.EmptyResult;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.List;

public interface IMemberFilter<T> {
    @NotNull
    Either<EmptyResult, List<T>> filter(T target, List<T> candidates, ClassNode targetClass, ClassNode refClass);
}
