package org.codehaus.groovy.eclipse.refactoring.formatter;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * Removes trailing semicolons as they are optional in Groovy.
 */
public class SemicolonRemover extends GroovyFormatter {

    public SemicolonRemover(ITextSelection sel, IDocument doc) {
        super(sel, doc);
    }

    @Override
    public TextEdit format() throws BadLocationException {
        TextEdit textEdit = new MultiTextEdit();

        for (int i = 0; i < document.getNumberOfLines(); i++) {
            IRegion lineInfo = document.getLineInformation(i);
            String line = document.get(lineInfo.getOffset(), lineInfo.getLength());

            if (hasUnnecessarySemicolon(line)) {
                int semicolonOffset = lineInfo.getOffset() + line.lastIndexOf(';');
                TextEdit deleteSemicolon = new DeleteEdit(semicolonOffset, 1);
                textEdit.addChild(deleteSemicolon);
            }
        }

        return textEdit;
    }

    private boolean hasUnnecessarySemicolon(String line) {
        String trimmedLine = line.replace('\t', ' ').trim();

        boolean isComment = trimmedLine.startsWith("/*") || trimmedLine.startsWith("*") || trimmedLine.startsWith("//");
        boolean hasTrailingSemicolon = trimmedLine.endsWith(";");

        return !isComment && hasTrailingSemicolon;
    }
}
