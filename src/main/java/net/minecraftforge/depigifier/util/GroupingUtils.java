package net.minecraftforge.depigifier.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for managing grouping of objects
 * in a collection, based on some key.
 */
public final class GroupingUtils
{

    private GroupingUtils()
    {
        throw new IllegalStateException("Tried to initialize: GroupingUtils but this is a Utility class.");
    }

    public static <T, O> Multimap<O, T> groupByUsingSet(final Iterable<T> source, Function<T, O> extractor) {
        return groupBy(HashMultimap.create(), source, extractor);
    }

    public static <T, O> Multimap<O, T> groupByUsingList(final Iterable<T> source, Function<T, O> extractor) {
        return groupBy(ArrayListMultimap.create(), source, extractor);
    }


    private static <T, O> Multimap<O, T> groupBy(final Multimap<O, T> groups, final Iterable<T> source, Function<T, O> extractor) {
        source.forEach(
          e -> {
              groups.put(extractor.apply(e), e);
          }
        );

        return groups;
    }
}
