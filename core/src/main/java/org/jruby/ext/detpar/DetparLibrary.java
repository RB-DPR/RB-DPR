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
 * Copyright (C) 2006 MenTaLguY <mental@rydia.net>
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

package org.jruby.ext.detpar;

import java.io.IOException;

import java.util.ArrayList;
import org.jruby.Ruby;
import org.jruby.RubyInteger;
import org.jruby.RubyObject;
import org.jruby.RubyClass;
import org.jruby.RubyThread;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.parlib.Scheduler;
import org.jruby.race.RaceDetector;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.load.Library;
import org.jruby.runtime.builtin.IRubyObject;

//import org.jruby.race.RaceDetector;

/**
 * Parallel Library in the VM
 */
public class DetparLibrary implements Library {

    public void load(final Ruby runtime, boolean wrap) throws IOException {
        // System.out.println("ParLib load");
        // Initialize parlib
        ParLib.setup(runtime);
    }

    @JRubyClass(name = "ParLib")
    public static class ParLib extends RubyObject {
        //private static ArrayList<RubyThread> pool   = null;
        private static int                   nCores = 0;

        @JRubyMethod(name = { "new", "init" }, meta = true)
        public static void newInstance(ThreadContext context, IRubyObject recv,
                IRubyObject args, Block block) {
            // System.out.println("ParLib init");
            nCores = RubyInteger.num2int(args);
            // System.out.println("pool size: " + poolSize);
            // Create the thread pool
            Scheduler.initThreadManager(nCores, context.getRuntime()
                    .getThreadService(), context.getRuntime());
            // configure the race detector
            String var = System.getenv("RACE");
            if (var != null) {
                if (var.compareTo("Y") == 0) {
                    RaceDetector.turnOff = false;
                    RaceDetector.init();
                    RaceDetector.createRaceDetector();
                }
            }
            if (!RaceDetector.turnOff) {
                RaceDetector.enable();
                RaceDetector.config(nCores, 0);
                RaceDetector.start();
                // System.out.println("detector started");
            }
        }

        public ParLib(Ruby runtime, RubyClass type) {
            super(runtime, type);
        }

        public static void setup(Ruby runtime) {
            RubyClass cParLib = runtime.defineClass("ParLib",
                    runtime.getObject(), new ObjectAllocator() {
                        public IRubyObject allocate(Ruby runtime,
                                RubyClass klass) {
                            return new ParLib(runtime, klass);
                        }
                    });
            cParLib.setReifiedClass(ParLib.class);
            cParLib.defineAnnotatedMethods(ParLib.class);
        }
    }
}
