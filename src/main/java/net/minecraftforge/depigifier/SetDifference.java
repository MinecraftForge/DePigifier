package net.minecraftforge.depigifier;

import java.util.HashSet;
import java.util.Set;

public class SetDifference<T> {
    private final HashSet<T> leftOnly;
    private final HashSet<T> common;
    private final HashSet<T> rightOnly;

    public SetDifference(final Set<T> left, final Set<T> right) {
        leftOnly = new HashSet<>(left);
        leftOnly.removeAll(right);
        rightOnly = new HashSet<>(right);
        rightOnly.removeAll(left);
        common = new HashSet<>(left);
        common.removeAll(leftOnly);
    }

    public HashSet<T> getLeftOnly() {
        return leftOnly;
    }

    public HashSet<T> getCommon() {
        return common;
    }

    public HashSet<T> getRightOnly() {
        return rightOnly;
    }
}
