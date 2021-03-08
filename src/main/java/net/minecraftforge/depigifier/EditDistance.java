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
			
			return Type.getType(fieldA.desc).getSort() == Type.getType(fieldB.desc).getSort();
		}
		
		if(a instanceof MethodInsnNode && b instanceof MethodInsnNode) {
			MethodInsnNode methodA = (MethodInsnNode)a;
			MethodInsnNode methodB = (MethodInsnNode)b;
			
			return Type.getReturnType(methodA.desc).getSort() == Type.getReturnType(methodB.desc).getSort();
		}
		
		if(a instanceof VarInsnNode && b instanceof VarInsnNode) {
			VarInsnNode varA = (VarInsnNode)a;
			VarInsnNode varB = (VarInsnNode)b;
			
			return varA.var == varB.var;
		}
		
		return true;
	}

}