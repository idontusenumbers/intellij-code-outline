package net.kano.codeoutline;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;


public class Util {
    public static int getLineMinusFolds(EditorEx editorEx, int unfoldedLineNumber){
        FoldingModel foldingModel = editorEx.getFoldingModel();
        DocumentEx document = editorEx.getDocument();
        int y = unfoldedLineNumber;
        for (int i = 0; i < foldingModel.getAllFoldRegions().length; i++) {
            FoldRegion f = foldingModel.getAllFoldRegions()[i];
            int startLine = document.getLineNumber(f.getStartOffset());
            int endLine = document.getLineNumber(f.getEndOffset());
            if (unfoldedLineNumber < startLine)
                break;
            if (!f.isExpanded()) {
                if (unfoldedLineNumber >startLine && unfoldedLineNumber < endLine) {
                    y -= (unfoldedLineNumber - startLine);
                    break;
                }else if (unfoldedLineNumber >endLine) {
                    y -= (endLine - startLine);
                }
            }
        }
        return y;
    }
    public static int getLinePlusFolds(EditorEx editorEx, int foldedLineNumber){
        FoldingModel foldingModel = editorEx.getFoldingModel();
        DocumentEx document = editorEx.getDocument();
        int yF = 0;
        int yU = 0;

        for (int i = 0; i < foldingModel.getAllFoldRegions().length; i++) {
            FoldRegion f = foldingModel.getAllFoldRegions()[i];
            int startLine = document.getLineNumber(f.getStartOffset());
            int endLine = document.getLineNumber(f.getEndOffset());
            int d = endLine - startLine;

            int skip = startLine - yU;

            if (foldedLineNumber > yF +skip) {
                // folded line is at least past he start of this fold
                yF += skip;
                yU += skip;

                if (!f.isExpanded()){
                    yU += d;
                }else{
                    if (foldedLineNumber > yF + d) {
                        // folded line is past this fold
                        yU += d;
                        yF += d;
                    }else if(foldedLineNumber > yF){
                        // folded line was within this fold
                        return yU + (foldedLineNumber - yF);
                    }
                }
            }else{
                // folded line was before the start of this fold
                return yU + (foldedLineNumber - yF);
            }
        }
        return yU + (foldedLineNumber - yF);

    }
}
