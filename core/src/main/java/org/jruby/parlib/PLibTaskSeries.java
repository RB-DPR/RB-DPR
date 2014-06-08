/*****
 * BEGIN LICENSE BLOCK ***** Version: CPL 1.0/GPL 2.0/LGPL 2.1
 * 
 * The contents of this file are subject to the Common Public License Version
 * 1.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"), in
 * which case the provisions of the GPL or the LGPL are applicable instead of
 * those above. If you wish to allow use of your version of this file only under
 * the terms of either the GPL or the LGPL, and not to allow others to use your
 * version of this file under the terms of the CPL, indicate your decision by
 * deleting the provisions above and replace them with the notice and other
 * provisions required by the GPL or the LGPL. If you do not delete the
 * provisions above, a recipient may use your version of this file under the
 * terms of any one of the CPL, the GPL or the LGPL. END LICENSE BLOCK
 *****/

package org.jruby.parlib;

import org.jruby.RubyProc;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * Each PLibTask object describes an executable task entity in a RubyThread's
 * task, including its depth and entry point. Note that a task may create
 * another task during its execution.
 **/

public class PLibTaskSeries {
    // information for a task
    protected int guid;
    protected int depth;
    protected int scopeDepth = 0;
    protected RubyProc entryPoint;
    protected Scheduler manager;
    protected ParallelContext pContext;
    // May be accessed concurrently
    protected volatile boolean enabled = true;
    
    // Initialize an empty task for subclasses
    protected PLibTaskSeries() {}
    
    // initialize a task
    public PLibTaskSeries (int thid, int seqNo, int depth, RubyProc entryPoint,
                          Scheduler manager, ParallelContext pContext) {
        this.guid = (thid << 26) + seqNo;
        this.depth = depth;
        this.entryPoint = entryPoint;
        this.manager = manager;
        this.pContext = pContext;
        this.scopeDepth = 0;
        this.enabled = true;
    }

    public int getGuid() {
        return guid;
    }

    public int getDepth() {
        return depth;
    }

    public void setScopeDepth(int d){
        this.scopeDepth = d;
    }
    
    public int getScopeDepth(){
        return this.scopeDepth;
    }
    
    public ParallelContext getParallelContext() {
        return this.pContext;
    }
    
    public boolean isEnabled() {
        return this.enabled;
    }
    
    public void disable() {
        this.enabled = false;
    }
    
    // Execute the task
    public void exec() {
        entryPoint.call(manager.getContext(Thread.currentThread()), IRubyObject.NULL_ARRAY);
        //pContext.arrive();
    }
    public void arrive()
    {
        // System.out.println("arrived");
        pContext.arrive();
    }

}
