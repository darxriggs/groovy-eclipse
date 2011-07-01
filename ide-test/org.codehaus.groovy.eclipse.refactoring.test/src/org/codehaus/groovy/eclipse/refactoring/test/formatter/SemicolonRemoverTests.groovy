/*
 * Copyright 2011 the original author or authors.
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
package org.codehaus.groovy.eclipse.refactoring.test.formatter

import junit.framework.TestCase

import org.codehaus.groovy.eclipse.refactoring.formatter.SemicolonRemover
import org.eclipse.jface.text.Document

class SemicolonRemoverTests extends TestCase {

    void testNullContent() {
        assertContentChangedFromTo(null, '')
    }

    void testEmptyDocument() {
        assertContentChangedFromTo('', '')
    }

    void testFullLineComment() {
        assertContentChangedFromTo('// def a',      '// def a')
        assertContentChangedFromTo('/* def a; */',  '/* def a; */')
        assertContentChangedFromTo('/* def a;\n*/', '/* def a;\n*/')
    }

    void testSimpleComment() {
        assertContentChangedFromTo('def a; // comment;',     'def a // comment;')
        assertContentChangedFromTo('def a; /* comment; */',  'def a /* comment; */')
        assertContentChangedFromTo('def a; /* comment;\n*/', 'def a /* comment;\n*/')
    }

    void testCommentInComment() {
        assertContentChangedFromTo('def a; /* comment 1; // comment 2; */', 'def a /* comment 1; // comment 2; */')
        assertContentChangedFromTo('def a; /* comment 1; /* comment 2; */', 'def a /* comment 1; /* comment 2; */')
        assertContentChangedFromTo('def a; // comment 1; /* comment 2; */', 'def a // comment 1; /* comment 2; */')
        assertContentChangedFromTo('def a; // comment 1; /* comment 2;',    'def a // comment 1; /* comment 2;')
    }

    void testMultipleTrailingComments() {
        assertContentChangedFromTo('def a; /* comment 1; */ // comment 2;',     'def a /* comment 1; */ // comment 2;')
        assertContentChangedFromTo('def a; /* comment 1; */ /* comment 2; */',  'def a /* comment 1; */ /* comment 2; */')
        assertContentChangedFromTo('def a; /* comment 1; */ /* comment 2;\n*/', 'def a /* comment 1; */ /* comment 2;\n*/')
    }

    void testMultipleComments() {
        assertContentChangedFromTo('a = 1; /* comment 1; */ b = 2; // comment 2;',     'a = 1; /* comment 1; */ b = 2 // comment 2;')
        assertContentChangedFromTo('a = 1; /* comment 1; */ b = 2; /* comment 2; */',  'a = 1; /* comment 1; */ b = 2 /* comment 2; */')
        assertContentChangedFromTo('a = 1; /* comment 1; */ b = 2; /* comment 2;\n*/', 'a = 1; /* comment 1; */ b = 2 /* comment 2;\n*/')
    }

    void testCommentInString() {
        assertContentChangedFromTo("def a = 'foo; // bar'; // baz;", "def a = 'foo; // bar' // baz;")
        assertContentChangedFromTo('def a = "foo; // bar"; // baz;', 'def a = "foo; // bar" // baz;')
    }

    void testSimpleRemoval() {
        assertContentChangedFromTo('def a = 10;', 'def a = 10')
        assertContentChangedFromTo('def a = {};', 'def a = {}')
        assertContentChangedFromTo('def a = [];', 'def a = []')
    }

    void testNothingToRemove() {
        assertContentChangedFromTo('def a = 10', 'def a = 10')
        assertContentChangedFromTo('def a = {}', 'def a = {}')
        assertContentChangedFromTo('def a = []', 'def a = []')
    }

    void testTrailingSpacesAndTabs() {
        assertContentChangedFromTo('def a = 1 ; ',   'def a = 1  ')
        assertContentChangedFromTo('def a = 1\t;\t', 'def a = 1\t\t')
    }

    private void assertContentChangedFromTo(String input, String expectedOutput) {
        def document = new Document(input)
        def formatter = new SemicolonRemover(null, document)

        def semicolonRemoval = formatter.format()
        semicolonRemoval.apply(document)
        String actualOutput = document.get()

        assertEquals(expectedOutput, actualOutput)
    }
}
