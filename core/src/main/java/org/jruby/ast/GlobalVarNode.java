/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: EPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.ast;

import java.util.List;

import org.jruby.Ruby;
import org.jruby.RubyString;
import org.jruby.ast.types.INameNode;
import org.jruby.ast.visitor.NodeVisitor;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.race.RaceDetector;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;
import org.jruby.util.DefinedMessage;

/**
 *	access to a global variable.
 */
public class GlobalVarNode extends RefNode implements INameNode {
    private String name;

    public GlobalVarNode(ISourcePosition position, String name) {
        super(position);
        this.name = name;
    }

    public NodeType getNodeType() {
        return NodeType.GLOBALVARNODE;
    }
    
    /**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    public Object accept(NodeVisitor iVisitor) {
        return iVisitor.visitGlobalVarNode(this);
    }

    /**
     * Gets the name.
     * @return Returns a String
     */
    public String getName() {
        return name;
    }

    public List<Node> childNodes() {
        return EMPTY_LIST;
    }
    
    @Override
    public IRubyObject interpret(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock) {
	    /*Weixing Ji*/
	    if(!RaceDetector.turnOff)
	    {
	    	/*if(RaceDetector.raceType == RaceDetector.RACE_TYPE_LOG)
	    		RaceDetector.notifyRd(((RubyBasicObject)self).getObjectId(),
				                            this.name,
				                            0);
	    	else if(RaceDetector.raceType == RaceDetector.RACE_TYPE_SPD3){
				RaceDetector.rdWrtCheck((RubyBasicObject) self, 0, 0);
			} else if (RaceDetector.raceType == RaceDetector.RACE_TYPE_CILK) {
				RaceDetector.readCilk(((RubyBasicObject) self).getObjectId(),
						((RubyBasicObject) self).getCilkShadow(), 0);
			}*/
	    	RaceDetector.notifyRd(self, this.name, 0);

			/*
			 * RubyThread rt = context.getThread(); rt.re.eventId =
			 * RaceDetector.RACE_EVENT_READ; rt.re.objId =
			 * ((RubyBasicObject)self).getObjectId(); rt.re.offset = 0;
			 * rt.re.info = this.name; runtime.rd.notify(rt.re);
			 */
		}
		/*Weixing Ji*/
        return runtime.getGlobalVariables().get(name);
    }
    
    @Override
    public RubyString definition(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock) {
        return runtime.getGlobalVariables().isDefined(name) ? runtime.getDefinedMessage(DefinedMessage.GLOBAL_VARIABLE) : null;
    }
}
