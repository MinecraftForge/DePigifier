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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class EditDistance {
	
	public static double computeDistance(InsnList m, InsnList n, IMapper mapperM, IMapper mapperN) {
		int[][] matrix = new int[m.size()+1][n.size()+1];
		//initialise
		for(int i = 0; i < matrix.length; i++) {
			matrix[i][0] = i;
		}
		for(int j = 0; j < matrix[0].length; j++) {
			matrix[0][j] = j;
		}
		
		//fill matrix, see: https://en.wikipedia.org/wiki/Levenshtein_distance
		for(int i = 1; i < matrix.length; i++) {
			for(int j = 1; j < matrix[0].length; j++) {
				boolean areEqual = insnEqual(m.get(i-1), n.get(j-1), mapperM, mapperN);
				int replace = matrix[i-1][j-1] + (areEqual ? 0 : 1);
				int remove = matrix[i-1][j] + 1;
				int insert = matrix[i][j-1] + 1;
				
				matrix[i][j] = Math.min(Math.min(replace, remove), insert);
			}
		}
		
		int distance = matrix[m.size()][n.size()];
		return distance / (double)Math.max(m.size(), n.size());
	}
	
	/**
	 * Compares two AbstractInsnNodes if they are equal.
	 */
	private static boolean insnEqual(AbstractInsnNode a, AbstractInsnNode b, IMapper mapperA, IMapper mapperB) {
		if(a.getOpcode() != b.getOpcode() || !a.getClass().equals(b.getClass())) {
			return false;
		}
		
		if(a instanceof FieldInsnNode && b instanceof FieldInsnNode) {
			FieldInsnNode fieldA = (FieldInsnNode)a;
			FieldInsnNode fieldB = (FieldInsnNode)b;
			
			String fieldTypeA = mapperA.mapClass(Type.getType(fieldA.desc).getClassName());
			String fieldTypeB = mapperB.mapClass(Type.getType(fieldB.desc).getClassName());
			if(!fieldTypeA.equals(fieldTypeB)) {
				return false;
			}
			
			String fieldOwnerA = mapperA.mapClass(fieldA.owner);
			String fieldOwnerB = mapperB.mapClass(fieldB.owner);
			if(!fieldOwnerA.equals(fieldOwnerB)) {
				return false;
			}
			
			String fieldNameA = mapperA.mapField(fieldA.owner, fieldA.name);
			String fieldNameB = mapperB.mapField(fieldB.owner, fieldB.name);
			if(!fieldNameA.equals(fieldNameB)) {
				return false;
			}
			
			return true;
		}
		
		if(a instanceof MethodInsnNode && b instanceof MethodInsnNode) {
			MethodInsnNode methodA = (MethodInsnNode)a;
			MethodInsnNode methodB = (MethodInsnNode)b;
			
			if(methodA.itf != methodB.itf) {
				return false;
			}
			
			String methodOwnerA = mapperA.mapClass(methodA.owner);
			String methodOwnerB = mapperB.mapClass(methodB.owner);
			if(!methodOwnerA.equals(methodOwnerB)) {
				return false;
			}
			
			String methodDescA = mapperA.mapDescriptor(methodA.desc);
			String methodDescB = mapperB.mapDescriptor(methodB.desc);
			if(!methodDescA.equals(methodDescB)) {
				return false;
			}
			
			String methodNameA = mapperA.mapMethod(methodA.owner, methodA.name, methodA.desc);
			String methodNameB = mapperB.mapMethod(methodB.owner, methodB.name, methodB.desc);
			if(!methodNameA.equals(methodNameB)) {
				return false;
			}
			
			return true;
		}
		
		if(a instanceof VarInsnNode && b instanceof VarInsnNode) {
			VarInsnNode varA = (VarInsnNode)a;
			VarInsnNode varB = (VarInsnNode)b;
			
			return varA.var == varB.var;
		}
		
		return true;
	}

}
