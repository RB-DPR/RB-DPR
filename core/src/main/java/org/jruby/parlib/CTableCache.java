package org.jruby.parlib;

import java.util.ArrayList;

import org.jruby.RubyModule;
import org.jruby.internal.runtime.methods.DynamicMethod;


/**
 * Cache class for method commutativity
 * Cache is represented by a binary matrix cachedCTable, each column is a long. 
 * cachedCTable[i][j] is 1 iff. method[i] is not commutative with method[j], 
 * else, cachedCTable[i][j] is 0. 
 * All methods in matrix is stored (sequentially) in methodsInMatrix
 * 
 * @author llu
 *
 */
public class CTableCache {
    protected long[] cachedCTable = null;
    public static final int CACHE_MATRIX_DIM = 64;
    protected ArrayList<DynamicMethod> methodsInMatrix = null;
    protected RubyModule module = null;
    
    /** Initialize matrix cache for the commutative table
     */
    public CTableCache(RubyModule m) {
        this.cachedCTable = new long[CACHE_MATRIX_DIM];
        this.methodsInMatrix = new ArrayList<DynamicMethod>();
        this.module = m;
    }
    
    /**
     * Add method's commutativity to cache
     * @param method
     */
    public void addMethod(DynamicMethod method) {
        // Get my column
        methodsInMatrix.add(method);
        int newPos = methodsInMatrix.size() - 1;
        if (newPos >= CACHE_MATRIX_DIM) {
            System.out.println("Integer overflow");
            return;
        }
        cachedCTable[newPos] = 0;
        // Add commutativity for the method itself
//        if (!module.isCommutative(method, method)) {
//            cachedCTable[newPos] = (1 << newPos);
//        }
        // Add commutativity for the method and other methods in cache
        for (int i = 0; i < methodsInMatrix.size(); i++) {
            DynamicMethod methodInTable = methodsInMatrix.get(i);
            // FIXME: Temporarily disabled
            /*
            if(!module.isCommutative(method, methodInTable)) {
                // Set M[newPos][i] to 1
                cachedCTable[i] |= (1 << newPos);
                if (newPos != i) {
                    // Set M[i][newPos] to 1
                    cachedCTable[newPos] |= (1 << i);
                }
            }*/
        }
    }
    
    /**
     * Check whether vector1 and vector2 (built from two access sets) commute 
     * with each other. Computed by result = V1 M V2T
     * 
     * @param vector1 (lowest bit on right)
     * @param vector2 (lowest bit on right)
     * @return
     */
    public boolean commutesWith(long vector1, long vector2) {
        // Compute V' = V1 M
        long V1M = 0;
        for (int i = 0; i < methodsInMatrix.size(); i++) {
            if ((vector1 & cachedCTable[i]) != 0) {
                V1M |= (1 >> i);
            }
        }
        // Compute result = V' V2T
        long result = V1M | vector2;
        return (result == 0);
    }
    
    /**
     * @return The module associated with this cache
     */
    public RubyModule getAssociatedModule() {
        return this.module;
    }
}
