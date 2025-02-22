package org.zaproxy.zap.extension.diff;

/*
Copyright 2010 Dmitry Naumenko (dm.naumenko@gmail.com)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import difflib.ChangeDelta;
import difflib.Chunk;
import difflib.DeleteDelta;
import difflib.Delta;
import difflib.DiffRow;
import difflib.DiffRow.Tag;
import difflib.DiffUtils;
import difflib.InsertDelta;
import difflib.Patch;

/**
 * ZAP note.
 * This is just a copy of DiffRowGenerator with the HTML normalization commented out.
 */

/**
* This class for generating DiffRows for side-by-sidy view.
* You can customize the way of generating. For example, show inline diffs on not, ignoring
* white spaces or/and blank lines and so on. All parameters for generating are optional. If you do
* not specify them, the class will use the default values.
*
* These values are:
* showInlineDiffs = false;
* ignoreWhiteSpaces = true;
* ignoreBlankLines = true;
* ...
*
* For instantiating the DiffRowGenerator you should use the its builder. Like in example
* <code>
*    DiffRowGenerator generator = new DiffRowGenerator.Builder().showInlineDiffs(true).
*    	ignoreWhiteSpaces(true).columnWidth(100).build();
* </code>
*
* @author <a href="dm.naumenko@gmail.com">Dmitry Naumenko</a>
*/
public class ZapDiffRowGenerator {
 private final boolean showInlineDiffs;
 private final boolean ignoreWhiteSpaces;
 private final boolean ignoreBlankLines;
 private final String InlineOldTag;
 private final String InlineNewTag;
 private final String InlineOldCssClass;
 private final String InlineNewCssClass;
 private final int columnWidth;
 private final Equalizer equalizer;

 public interface Equalizer {
	    public boolean equals(Object original, Object revised);
	}


 /**
  * This class used for building the DiffRowGenerator.
  * @author dmitry
  *
  */
 public static class Builder {
     private boolean showInlineDiffs = false;
     private boolean ignoreWhiteSpaces = false;
     private boolean ignoreBlankLines = false;
     private String InlineOldTag = "span";
     private String InlineNewTag = "span";
     private String InlineOldCssClass = "editOldInline";
     private String InlineNewCssClass = "editNewInline";
     private int columnWidth = 80;

     /**
      * Show inline diffs in generating diff rows or not.
      * @param val the value to set. Default: false.
      * @return builder with configured showInlineDiff parameter
      */
     public Builder showInlineDiffs(boolean val) {
         showInlineDiffs = val;
         return this;
     }

     /**
      * Ignore white spaces in generating diff rows or not.
      * @param val the value to set. Default: true.
      * @return builder with configured ignoreWhiteSpaces parameter
      */
     public Builder ignoreWhiteSpaces(boolean val) {
         ignoreWhiteSpaces = val;
         return this;
     }

     /**
      * Ignore blank lines in generating diff rows or not.
      * @param val the value to set. Default: true.
      * @return builder with configured ignoreBlankLines parameter
      */
     public Builder ignoreBlankLines(boolean val) {
         ignoreBlankLines = val;
         return this;
     }

     /**
      * Set the tag used for displaying changes in the original text.
      * @param tag the tag to set. Without angle brackets. Default: span.
      * @return builder with configured ignoreBlankLines parameter
      */
     public Builder InlineOldTag(String tag) {
         InlineOldTag = tag;
         return this;
     }

     /**
      * Set the tag used for displaying changes in the revised text.
      * @param tag the tag to set. Without angle brackets. Default: span.
      * @return builder with configured ignoreBlankLines parameter
      */
     public Builder InlineNewTag(String tag) {
         InlineNewTag = tag;
         return this;
     }

     /**
      * Set the css class used for displaying changes in the original text.
      * @param cssClass the tag to set. Without any quotes, just word. Default: editOldInline.
      * @return builder with configured ignoreBlankLines parameter
      */
     public Builder InlineOldCssClass(String cssClass) {
         InlineOldCssClass = cssClass;
         return this;
     }

     /**
      * Set the css class used for displaying changes in the revised text.
      * @param cssClass the tag to set. Without any quotes, just word. Default: editNewInline.
      * @return builder with configured ignoreBlankLines parameter
      */
     public Builder InlineNewCssClass(String cssClass) {
         InlineNewCssClass = cssClass;
         return this;
     }

     /**
      * Set the column with of generated lines of original and revised texts.
      * @param width the width to set. Making it < 0 doesn't have any sense. Default 80.
      * @return builder with configured ignoreBlankLines parameter
      */
     public Builder columnWidth(int width) {
         if (width > 0) {
             columnWidth = width;
         }
         return this;
     }

     /**
      * Build the DiffRowGenerator. If some parameters is not set, the default values are used.
      * @return the customized DiffRowGenerator
      */
     public ZapDiffRowGenerator build() {
         return new ZapDiffRowGenerator(this);
     }
 }

 private ZapDiffRowGenerator(Builder builder) {
     showInlineDiffs = builder.showInlineDiffs;
     ignoreWhiteSpaces = builder.ignoreWhiteSpaces; //
     ignoreBlankLines = builder.ignoreBlankLines; //
     InlineOldTag = builder.InlineOldTag;
     InlineNewTag = builder.InlineNewTag;
     InlineOldCssClass = builder.InlineOldCssClass;
     InlineNewCssClass = builder.InlineNewCssClass;
     columnWidth = builder.columnWidth; //
     equalizer = (original, revised) -> {
             if (ignoreWhiteSpaces) {
                 original = ((String)original).trim().replaceAll("\\s+", " ");
                 revised = ((String)revised).trim().replaceAll("\\s+", " ");
             }
             return original.equals(revised);
         };
 }

 /**
  * Get the DiffRows describing the difference between original and revised texts using the
  * given patch. Useful for displaying side-by-side diff.
  *
  * @param original the original text
  * @param revised the revised text
  * @return the DiffRows between original and revised texts
  */
 public List<DiffRow> generateDiffRows(List<String> original, List<String> revised) {
     return generateDiffRows(original, revised, DiffUtils.diff(original, revised));
 }

 private List<String> removeBlankLines(List<String> lines) {
     List<String> result = new ArrayList<>();
     for (String line: lines) {
         if (line.trim().length() == 0) {
             result.add("");
         }
         result.add(line);
     }
     return result;
 }

 /**
  * Generates the DiffRows describing the difference between original and revised texts using the
  * given patch. Useful for displaying side-by-side diff.
  *
  * @param original the original text
  * @param revised the revised text
  * @param patch the given patch
  * @return the DiffRows between original and revised texts
  */
 public List<DiffRow> generateDiffRows(List<String> original, List<String> revised, Patch<String> patch) {
     // normalize the lines (expand tabs, escape html entities)
	 // ZAP: Dont normalise the strings
     //original = StringUtills.normalize(original);
     //revised = StringUtills.normalize(revised);

     // wrap to the column width
     // ZAP: Don't word wrap, it adds br tags. The word wrap should be done by view component.
     // original = StringUtills.wrapText(original, this.columnWidth);
     // revised = StringUtills.wrapText(revised, this.columnWidth);

     List<DiffRow> diffRows = new ArrayList<>();
     int endPos = 0;
     final List<Delta<String>> deltaList = patch.getDeltas();
     for (int i = 0; i < deltaList.size(); i++) {
         Delta<String> delta = deltaList.get(i);
         Chunk<String> orig = delta.getOriginal();
         Chunk<String> rev = delta.getRevised();

         // We should normalize and wrap lines in deltas too.
    	 // ZAP: Dont normalise the strings
         //orig.setLines(StringUtills.normalize((List<String>) orig.getLines()));
         //rev.setLines(StringUtills.normalize((List<String>) rev.getLines()));

         // ZAP: Don't word wrap, it adds br tags. The word wrap should be done by view component.
         // orig.setLines(StringUtills.wrapText((List<String>) orig.getLines(), this.columnWidth));
         // rev.setLines(StringUtills.wrapText((List<String>) rev.getLines(), this.columnWidth));

         // catch the equal prefix for each chunk
         for (String line : original.subList(endPos, orig.getPosition())) {
             diffRows.add(new DiffRow(Tag.EQUAL, line, line));
         }

         // Inserted DiffRow
         if (delta.getClass().equals(InsertDelta.class)) {
             endPos = orig.last() + 1;
             for (String line : rev.getLines()) {
                 diffRows.add(new DiffRow(Tag.INSERT, "", line));
             }
             continue;
         }

         // Deleted DiffRow
         if (delta.getClass().equals(DeleteDelta.class)) {
             endPos = orig.last() + 1;
             for (String line : orig.getLines()) {
                 diffRows.add(new DiffRow(Tag.DELETE, line, ""));
             }
             continue;
         }

         if (showInlineDiffs) {
             addInlineDiffs(delta);
         }
         // the changed size is match
         if (orig.size() == rev.size()) {
             for (int j = 0; j < orig.size(); j++) {
                 diffRows.add(new DiffRow(Tag.CHANGE, orig.getLines().get(j),
                         rev.getLines().get(j)));
             }
         } else if (orig.size() > rev.size()) {
             for (int j = 0; j < orig.size(); j++) {
                 diffRows.add(new DiffRow(Tag.CHANGE, orig.getLines().get(j), rev
                         .getLines().size() > j ? rev.getLines().get(j) : ""));
             }
         } else {
             for (int j = 0; j < rev.size(); j++) {
                 diffRows.add(new DiffRow(Tag.CHANGE, orig.getLines().size() > j ? orig
                         .getLines().get(j) : "", rev.getLines().get(j)));
             }
         }
         endPos = orig.last() + 1;
     }

     // Copy the final matching chunk if any.
     for (String line : original.subList(endPos, original.size())) {
         diffRows.add(new DiffRow(Tag.EQUAL, line, line));
     }
     return diffRows;
 }

 /**
  * Add the inline diffs for given delta
  * @param delta the given delta
  */
 private void addInlineDiffs(Delta<String> delta) {
     List<String> orig = delta.getOriginal().getLines();
     List<String> rev = delta.getRevised().getLines();
     LinkedList<String> origList = new LinkedList<>();
     for (Character character : join(orig, "\n").toCharArray()) {
         origList.add(character.toString());
     }
     LinkedList<String> revList = new LinkedList<>();
     for (Character character : join(rev, "\n").toCharArray()) {
         revList.add(character.toString());
     }
     List<Delta<String>> inlineDeltas = DiffUtils.diff(origList, revList).getDeltas();
     if (inlineDeltas.size() < 3) {
         Collections.reverse(inlineDeltas);
         for (Delta<String> inlineDelta : inlineDeltas) {
             Chunk<String> inlineOrig = inlineDelta.getOriginal();
             Chunk<String> inlineRev = inlineDelta.getRevised();
             if (inlineDelta.getClass().equals(DeleteDelta.class)) {
                 origList = wrapInTag(origList, inlineOrig.getPosition(), inlineOrig
                         .getPosition()
                         + inlineOrig.size() + 1, this.InlineOldTag, this.InlineOldCssClass);
             } else if (inlineDelta.getClass().equals(InsertDelta.class)) {
                 revList = wrapInTag(revList, inlineRev.getPosition(), inlineRev.getPosition()
                         + inlineRev.size() + 1, this.InlineNewTag, this.InlineNewCssClass);
             } else if (inlineDelta.getClass().equals(ChangeDelta.class)) {
                 origList = wrapInTag(origList, inlineOrig.getPosition(), inlineOrig
                         .getPosition()
                         + inlineOrig.size() + 1, this.InlineOldTag, this.InlineOldCssClass);
                 revList = wrapInTag(revList, inlineRev.getPosition(), inlineRev.getPosition()
                         + inlineRev.size() + 1, this.InlineNewTag, this.InlineNewCssClass);
             }
         }
         StringBuilder origResult = new StringBuilder(), revResult = new StringBuilder();
         for (String character : origList) {
             origResult.append(character);
         }
         for (String character : revList) {
             revResult.append(character);
         }
         delta.getOriginal().setLines(Arrays.asList(origResult.toString().split("\n")));
         delta.getRevised().setLines(Arrays.asList(revResult.toString().split("\n")));
     }
 }

 /**
  * Wrap the elements in the sequence with the given tag
  * @param startPosition the position from which tag should start. The counting start from a zero.
  * @param endPosition the position before which tag should be closed.
  * @param tag the tag name without angle brackets, just a word
  * @param cssClass the optional css class
  */
 public static LinkedList<String> wrapInTag(LinkedList<String> sequence, int startPosition,
         int endPosition, String tag, String cssClass) {
     LinkedList<String> result = new LinkedList<>(sequence);
     StringBuilder tagBuilder = new StringBuilder();
     tagBuilder.append("<");
     tagBuilder.append(tag);
     if (cssClass != null) {
         tagBuilder.append(" class=\"");
         tagBuilder.append(cssClass);
         tagBuilder.append("\"");
     }
     tagBuilder.append(">");
     String startTag = tagBuilder.toString();

     tagBuilder.delete(0, tagBuilder.length());

     tagBuilder.append("</");
     tagBuilder.append(tag);
     tagBuilder.append(">");
     String endTag = tagBuilder.toString();

     result.add(startPosition, startTag);
     result.add(endPosition, endTag);
     return result;
 }

 /**
  * Wrap the given line with the given tag
  * @param line the given line
  * @param tag the tag name without angle brackets, just a word
  * @param cssClass the optional css class
  * @return the wrapped string
  */
 public static String wrapInTag(String line, String tag, String cssClass) {
     StringBuilder tagBuilder = new StringBuilder();
     tagBuilder.append("<");
     tagBuilder.append(tag);
     if (cssClass != null) {
         tagBuilder.append(" class=\"");
         tagBuilder.append(cssClass);
         tagBuilder.append("\"");
     }
     tagBuilder.append(">");
     String startTag = tagBuilder.toString();

     tagBuilder.delete(0, tagBuilder.length());

     tagBuilder.append("</");
     tagBuilder.append(tag);
     tagBuilder.append(">");
     String endTag = tagBuilder.toString();

     return startTag + line + endTag;
 }

 /**
  * The helper method for joining collections
  * @param <T>
  * @param objs the collection to join
  * @param delimiter the delimiter to use
  * @return the joined string
  */
 private static <T> String join(final Iterable<T> objs, final String delimiter) {
     Iterator<T> iter = objs.iterator();
     if (!iter.hasNext()) {
         return "";
     }
     StringBuffer buffer = new StringBuffer(String.valueOf(iter.next()));
     while (iter.hasNext()) {
         buffer.append(delimiter).append(String.valueOf(iter.next()));
     }
     return buffer.toString();
 }
}
