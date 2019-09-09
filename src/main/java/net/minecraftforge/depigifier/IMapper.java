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

import org.objectweb.asm.Type;

public interface IMapper {
    String mapClass(String cls);
    String mapField(String cls, String field);
    String mapMethod(String cls, String method, String desc);

    default String mapDescriptor(String desc) {
        return mapDescriptor(Type.getArgumentTypes(desc), Type.getReturnType(desc));
    }

    default String mapDescriptor(Type[] args, Type ret) {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (Type arg : args) {
            if (arg.getSort() == Type.ARRAY) {
                for (int x = 0; x < arg.getDimensions(); x++)
                    buf.append('[');
                arg = arg.getElementType();
            }
            if (arg.getSort() == Type.OBJECT)
                buf.append('L').append(mapClass(arg.getInternalName())).append(';');
            else
                buf.append(arg.getDescriptor());
        }
        buf.append(')');

        if (ret.getSort() == Type.ARRAY) {
            for (int x = 0; x < ret.getDimensions(); x++)
                buf.append('[');
            ret = ret.getElementType();
        }
        if (ret.getSort() == Type.OBJECT)
            buf.append('L').append(mapClass(ret.getInternalName())).append(';');
        else
            buf.append(ret.getDescriptor());

        return buf.toString();
    }
}
