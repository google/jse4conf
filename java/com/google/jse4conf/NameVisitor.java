// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.jse4conf;

import java.util.HashSet;
import java.util.Set;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NodeVisitor;
import org.mozilla.javascript.ast.ObjectProperty;
import org.mozilla.javascript.ast.PropertyGet;

/**
 * Visit every AST node to collect all used names.
 *
 * <p>Known limitations:
 *
 * <ul>
 *   <li>A key's value can be a function, which is not evaluated yet. So in most cases it can be
 *       defined before other keys. However, it can be called by another key, and the names used in
 *       its body will be needed before the other key. Hence, we just make all names used in a
 *       function body as dependent names. The extra dependency means "potential" run-time recursive
 *       calls and wrong evaluation order. User's best strategy to avoid such problem is to define
 *       key values completely without cyclic reference.
 *   <li>A function's parameter is not a key. But if its name is the same as some other key, wrong
 *       dependency could be introduced here. This can be worked around by renaming the parameter,
 *       or we should ignore parameter names when visiting a function body.
 * </ul>
 *
 * <p>To fix any dependency error produced from this simple visitor, or for any reason to specify
 * output order of keys, users can add a special (unused) key that contains a list of key names. For
 * example, the following definition
 *
 * <pre>
 *    myKeyOrder = [k2, k3, k1]
 * </pre>
 *
 * tells the jse4conf Section compiler to output k2 before k3, and k3 before k1. User only need to
 * specify a partial order to resolve errors. In this example, other used keys of k3 will still be
 * emitted before k3, either before or after k2.
 */
class NameVisitor implements NodeVisitor {
  private final String name; // name of the current key
  private final Logger logger; // to dump debug/trace messages
  public Set<String> usedNames; // names used by the current key

  public NameVisitor(String name, Logger logger) {
    this.name = name;
    this.logger = logger;
    usedNames = new HashSet<>();
  }

  @Override
  public boolean visit(AstNode node) {
    switch (node.getType()) {
      case Token.ERROR: // skip error nodes
        logger.debug("    skip ErrorNode.");
        return false;
      case Token.NAME: // capture it if not the current key name
        {
          logger.traceVisitNameNode((Name) node);
          String id = ((Name) node).getIdentifier();
          // Do not count calls to self. There is no way to forwardly
          // declare self and fix wrong recursive calls.
          // Do not count local names, which have non-null defining scope.
          if (!id.equals(name) && null == ((Name) node).getDefiningScope()) {
            usedNames.add(id);
          }
          return true; // no child node, but return true anyway
        }
      case Token.GETPROP:
        {
          // visit only the target part of a taret.property expression
          if (node instanceof PropertyGet && ((PropertyGet) node).getRight() instanceof Name) {
            logger.traceVisitNode("GETPROP", node);
            ((PropertyGet) node).getLeft().visit(this);
            return false;
          }
          break;
        }
      case Token.COLON:
        {
          // visit only the right part of a name:value ObjectProperty
          if (node instanceof ObjectProperty && ((ObjectProperty) node).getLeft() instanceof Name) {
            logger.traceVisitNode("COLON", node);
            ((ObjectProperty) node).getRight().visit(this);
            return false;
          }
          break;
        }
      default: // fall out
    }
    // default: print trace message and visit child nodes
    logger.traceVisitNode("node", node);
    return true;
  }
}
