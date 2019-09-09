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
