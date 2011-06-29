/*
 * Copyright 2003-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.eclipse.refactoring.test.formatter;

import junit.framework.TestCase;

import org.codehaus.groovy.eclipse.refactoring.formatter.SemicolonRemover;

public class SemicolonRemoverTests extends TestCase {

    public void testNullString() {
        assertEquals(null, SemicolonRemover.stripTrailingComments(null));
    }

    public void testEmptyString() {
        assertEquals("", SemicolonRemover.stripTrailingComments(""));
    }

    public void testFullLineComment() {
        assertEquals("", SemicolonRemover.stripTrailingComments("// def a"));
        assertEquals("", SemicolonRemover.stripTrailingComments("/* def a */"));
        assertEquals("", SemicolonRemover.stripTrailingComments("/* def a"));
    }

    public void testSimpleComment() {
        assertEquals("def a", SemicolonRemover.stripTrailingComments("def a // comment"));
        assertEquals("def a", SemicolonRemover.stripTrailingComments("def a /* comment */"));
        assertEquals("def a", SemicolonRemover.stripTrailingComments("def a /* comment"));
    }

    public void testCommentInComment() {
        assertEquals("def a", SemicolonRemover.stripTrailingComments("def a /* comment 1 // comment 2 */"));
        assertEquals("def a", SemicolonRemover.stripTrailingComments("def a /* comment 1 /* comment 2 */"));
        assertEquals("def a", SemicolonRemover.stripTrailingComments("def a // comment 1 /* comment 2 */"));
        assertEquals("def a", SemicolonRemover.stripTrailingComments("def a // comment 1 /* comment 2"));
    }

    public void testMultipleTrailingComments() {
        assertEquals("def a", SemicolonRemover.stripTrailingComments("def a /* comment 1 */ // comment 2"));
        assertEquals("def a", SemicolonRemover.stripTrailingComments("def a /* comment 1 */ /* comment 2 */"));
        assertEquals("def a", SemicolonRemover.stripTrailingComments("def a /* comment 1 */ /* comment 2"));
    }

    public void testMultipleComments() {
        assertEquals("a = 1; /* comment 1 */ b = 2", SemicolonRemover.stripTrailingComments("a = 1; /* comment 1 */ b = 2 // comment 2"));
        assertEquals("a = 1; /* comment 1 */ b = 2", SemicolonRemover.stripTrailingComments("a = 1; /* comment 1 */ b = 2 /* comment 2 */"));
        assertEquals("a = 1; /* comment 1 */ b = 2", SemicolonRemover.stripTrailingComments("a = 1; /* comment 1 */ b = 2 /* comment 2"));
    }

    // TODO: known error case
//    public void testTODO() {
//        assertEquals("a = \"foo ;;;// bar\" // baz", SemicolonRemover.stripTrailingComments("a = \"foo ;;;// bar\" // baz"));
//    }
}
