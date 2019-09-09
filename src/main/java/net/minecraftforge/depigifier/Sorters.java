/*
 * DePigifier
 * Copyright (c) 2016-2019.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.depigifier;

import java.util.Comparator;

public class Sorters {

    private static final String PADDING = "00000000000000000000000000000000000000000000000000";

    public static Comparator<String> CLASSES = (o1, o2) -> {
        if (o1.equals(o2))
            return 0;

        String[] pts1 = o1.split("\\$");
        String[] pts2 = o2.split("\\$");

        int s1 = pts1[0].indexOf('/');
        int s2 = pts2[0].indexOf('/');
        if (s1 != -1 && s2 != -1)
            return o1.compareTo(o2);
        if (s1 != -1)
            return 1;
        if (s2 != -1)
            return -1;

        if (pts1[0].length() != pts2[0].length())
            return pts1[0].length() - pts2[0].length();

        int ret = pts1[0].compareTo(pts2[0]);
        if (ret != 0)
            return ret;

        for (int x = 1; x < pts1.length; x++) {
            if (x >= pts2.length)
                return 1;

            String l = PADDING.substring(pts1[x].length()) + pts1[x];
            String r = PADDING.substring(pts2[x].length()) + pts2[x];
            ret = l.compareTo(r);
            if (ret != 0)
                return ret;
        }
        return -1;
    };

    public static Comparator<String> FIELDS = (o1, o2) -> {
        String[] pts1 = o1.split("\\.");
        String[] pts2 = o2.split("\\.");
        int ret = CLASSES.compare(pts1[0], pts2[0]);
        if (ret != 0)
            return ret;
        return pts1[1].compareTo(pts2[1]);
    };

    public static Comparator<String> METHODS = (o1, o2) -> {
        String[] pts1 = o1.split(" ");
        String[] pts2 = o2.split(" ");
        int ret = FIELDS.compare(pts1[0], pts2[0]);
        if (ret != 0)
            return ret;
        return pts1[1].compareTo(pts2[1]);
    };
}
