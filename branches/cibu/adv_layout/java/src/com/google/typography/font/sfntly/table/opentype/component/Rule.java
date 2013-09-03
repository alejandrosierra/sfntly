package com.google.typography.font.sfntly.table.opentype.component;

import com.google.typography.font.sfntly.Font;
import com.google.typography.font.sfntly.Tag;
import com.google.typography.font.sfntly.table.core.CMap;
import com.google.typography.font.sfntly.table.core.CMapTable;
import com.google.typography.font.sfntly.table.core.PostScriptTable;
import com.google.typography.font.sfntly.table.opentype.FeatureListTable;
import com.google.typography.font.sfntly.table.opentype.GSubTable;
import com.google.typography.font.sfntly.table.opentype.LangSysTable;
import com.google.typography.font.sfntly.table.opentype.LookupListTable;
import com.google.typography.font.sfntly.table.opentype.ScriptListTable;
import com.google.typography.font.sfntly.table.opentype.ScriptTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Rule {
  public final RuleSegment backtrack;
  public final RuleSegment input;
  public final RuleSegment lookAhead;
  public final RuleSegment subst;

  public Rule(RuleSegment backtrack, RuleSegment input, RuleSegment lookAhead, RuleSegment subst) {
    this.backtrack = backtrack;
    this.input = input;
    this.lookAhead = lookAhead;
    this.subst = subst;
  }

  public Rule(Rule other, RuleSegment subst) {
    this.backtrack = other.backtrack;
    this.input = other.input;
    this.lookAhead = other.lookAhead;
    this.subst = subst;
  }

  // Closure related
  public static GlyphGroup charGlyphClosure(String txt, Font font) {
    PostScriptTable post = font.getTable(Tag.post);
    CMapTable cmapTable = font.getTable(Tag.cmap);
    GlyphGroup glyphGroup = glyphGroupForText(txt, cmapTable);

    Set<Rule> featuredRules = featuredRules(font);
    Map<Integer, Set<Rule>> glyphRuleMap = createGlyphRuleMap(featuredRules);
    GlyphGroup ruleClosure = closure(glyphRuleMap, glyphGroup);
    System.out.println("Closure: " + toString(ruleClosure, post));
    return ruleClosure;
  }

  public static GlyphGroup closure(Map<Integer, Set<Rule>> glyphRuleMap, GlyphGroup glyphs) {
    int prevSize = 0;
    while (glyphs.size() > prevSize) {
      prevSize = glyphs.size();
      for (Rule rule : rulesForGlyph(glyphRuleMap, glyphs)) {
        rule.addMatchingTargetGlyphs(glyphs);
      }
    }
    return glyphs;
  }

  private void addMatchingTargetGlyphs(GlyphGroup glyphs) {
    for (RuleSegment seg : new RuleSegment[] { input, backtrack, lookAhead }) {
      if (seg == null) {
        continue;
      }
      for (GlyphGroup g : seg) {
        if (!g.intersects(glyphs)) {
          return;
        }
      }
    }

    for (GlyphGroup glyphGroup : subst) {
      glyphs.addAll(glyphGroup);
    }
  }

  public static Map<Integer, Set<Rule>> glyphRulesMap(Font font) {
    Set<Rule> featuredRules = Rule.featuredRules(font);
    if (featuredRules == null) {
      return null;
    }
    return createGlyphRuleMap(featuredRules);
  }

  private static Map<Integer, Set<Rule>> createGlyphRuleMap(Set<Rule> lookupRules) {
    Map<Integer, Set<Rule>> map = new HashMap<Integer, Set<Rule>>();

    for (Rule rule : lookupRules) {
      for (int glyph : rule.input.get(0)) {
        if (!map.containsKey(glyph)) {
          map.put(glyph, new HashSet<Rule>());
        }
        map.get(glyph).add(rule);
      }
    }
    return map;
  }

  private static Set<Rule> rulesForGlyph(Map<Integer, Set<Rule>> glyphRuleMap, GlyphGroup glyphs) {
    Set<Rule> set = new HashSet<Rule>();
    for(int glyph : glyphs) {
      if (glyphRuleMap.containsKey(glyph)) {
        set.addAll(glyphRuleMap.get(glyph));
      }
    }
    return set;
  }

  private static Set<Rule> featuredRules(
      Set<Integer> lookupIds, Map<Integer, Set<Rule>> ruleMap) {
    Set<Rule> rules = new LinkedHashSet<Rule>();
    for (int lookupId : lookupIds) {
      Set<Rule> ruleForLookup = ruleMap.get(lookupId);
      if (ruleForLookup == null) {
        System.err.printf("Lookup ID %d is used in features but not defined.\n", lookupId);
        continue;
      }
      rules.addAll(ruleForLookup);
    }
    return rules;
  }

  private static Set<Rule> featuredRules(Font font) {
    GSubTable gsub = font.getTable(Tag.GSUB);
    if (gsub == null) {
      return null;
    }

    ScriptListTable scripts = gsub.scriptList();
    FeatureListTable featureList = gsub.featureList();
    LookupListTable lookupList = gsub.lookupList();
    Map<Integer, Set<Rule>> ruleMap = RuleExtractor.extract(lookupList);

    Set<Integer> features = new HashSet<Integer>();
    Set<Integer> lookupIds = new HashSet<Integer>();

    for (ScriptTable script : scripts.map().values()) {
      for (LangSysTable langSys : script.map().values()) {
        // We are assuming if required feature exists, it will be in the list
        // of features as well.
        for (NumRecord feature : langSys) {
          if (!features.contains(feature.value)) {
            features.add(feature.value);
            for (NumRecord lookup : featureList.subTableAt(feature.value)) {
              lookupIds.add(lookup.value);
            }
          }
        }
      }
    }
    Set<Rule> featuredRules = Rule.featuredRules(lookupIds, ruleMap);
    return featuredRules;
  }


  // Utility method for glyphs for text

  public static GlyphGroup glyphGroupForText(String str, CMapTable cmapTable) {
    GlyphGroup glyphGroup = new GlyphGroup();
    Set<Integer> codes = codepointsFromStr(str);
    for (int code : codes) {
      for (CMap cmap : cmapTable) {
        if (cmap.platformId() == 3 && cmap.encodingId() == 1 || // Unicode BMP
            cmap.platformId() == 3 && cmap.encodingId() == 10 || // UCS2
            cmap.platformId() == 0 && cmap.encodingId() == 5) { // Variation
          int glyph = cmap.glyphId(code);
          if (glyph != CMapTable.NOTDEF) {
            glyphGroup.add(glyph);
          }
          // System.out.println("code: " + code + " glyph: " + glyph + " platform: " + cmap.platformId() + " encodingId: " + cmap.encodingId() + " format: " + cmap.format());

        }
      }
    }
    return glyphGroup;
  }

  // Rule operation

  public void applyRuleOnRuleWithSubst(Rule targetRule, int at, Set<Rule> accumulateTo) {
    RuleSegment matchSegment = targetRule.match(this, at);
    if (matchSegment == null) {
      return;
    }

    if (at < 0) {
      throw new IllegalStateException();
    }

    int backtrackSize = targetRule.backtrack != null ? targetRule.backtrack.size() : 0;
    RuleSegment newBacktrack = new RuleSegment();
    newBacktrack.addAll(matchSegment.subList(0, backtrackSize));

    if (at <= targetRule.subst.size()) {
      RuleSegment newInput = new RuleSegment();
      newInput.addAll(targetRule.input);
      newInput.addAll(matchSegment.subList(backtrackSize + targetRule.subst.size(), backtrackSize + at + input.size()));

      RuleSegment newLookAhead = new RuleSegment();
      newLookAhead.addAll(matchSegment.subList(backtrackSize + at + input.size(), matchSegment.size()));

      RuleSegment newSubst = new RuleSegment();
      newSubst.addAll(targetRule.subst.subList(0, at));
      newSubst.addAll(subst);
      if (at + input.size() < targetRule.subst.size()) {
        newSubst.addAll(targetRule.subst.subList(at + input.size(), targetRule.subst.size()));
      }

      Rule newTargetRule = new Rule(newBacktrack, newInput, newLookAhead, newSubst);
      accumulateTo.add(newTargetRule);
      return;
    }

    if (at >= targetRule.subst.size()) {
      List<GlyphGroup> skippedLookAheadPart = matchSegment.subList(backtrackSize + targetRule.subst.size(), at);
      Set<RuleSegment> intermediateSegments = permuteToSegments(skippedLookAheadPart);

      RuleSegment newLookAhead = new RuleSegment();
      List<GlyphGroup> remainingLookAhead = matchSegment.subList(backtrackSize + at + input.size(), matchSegment.size());
      newLookAhead.addAll(remainingLookAhead);

      for (RuleSegment interRuleSegment : intermediateSegments) {

        RuleSegment newInput = new RuleSegment();
        newInput.addAll(targetRule.input);
        newInput.addAll(interRuleSegment);
        newInput.addAll(input);

        RuleSegment newSubst = new RuleSegment();
        newSubst.addAll(targetRule.subst);
        newInput.addAll(interRuleSegment);
        newSubst.addAll(subst);

        Rule newTargetRule = new Rule(newBacktrack, newInput, newLookAhead, newSubst);
        accumulateTo.add(newTargetRule);
      }
    }
  }

  static Set<RuleSegment> permuteToSegments(List<GlyphGroup> glyphGroups) {
    Set<RuleSegment> result = new LinkedHashSet<RuleSegment>();
    result.add(new RuleSegment());

    for (GlyphGroup glyphGroup : glyphGroups) {
      Set<RuleSegment> newResult = new LinkedHashSet<RuleSegment>();
      for (Integer glyphId : glyphGroup) {
        for (RuleSegment segment : result) {
          RuleSegment newSegment = new RuleSegment();
          newSegment.addAll(segment);
          newSegment.add(new GlyphGroup(glyphId));
          newResult.add(newSegment);
        }
      }
      result = newResult;
    }
    return result;
  }

  static Rule applyRuleOnRuleWithoutSubst(Rule ruleToApply, Rule targetRule, int at) {

    RuleSegment matchSegment = targetRule.match(ruleToApply, at);
    if (matchSegment == null) {
      return null;
    }

    int backtrackSize = targetRule.backtrack != null ? targetRule.backtrack.size() : 0;

    RuleSegment newBacktrack =  new RuleSegment();
    newBacktrack.addAll(matchSegment.subList(0, backtrackSize + at));

    RuleSegment newLookAhead = new RuleSegment();
    newLookAhead.addAll(matchSegment.subList(backtrackSize + at + ruleToApply.input.size(), matchSegment.size()));

    return new Rule(newBacktrack, ruleToApply.input, newLookAhead, ruleToApply.subst);
  }

  static void applyRulesOnRuleWithSubst(Set<Rule> rulesToApply, Rule targetRule, int at, Set<Rule> accumulateTo) {
    for (Rule ruleToApply : rulesToApply) {
      ruleToApply.applyRuleOnRuleWithSubst(targetRule, at, accumulateTo);
    }
  }

  static void applyRulesOnRuleWithoutSubst(Set<Rule> rulesToApply, Rule targetRule, int at, Set<Rule> accumulateTo) {
    for (Rule ruleToApply : rulesToApply) {
      Rule newRule = applyRuleOnRuleWithoutSubst(ruleToApply, targetRule, at);
      if (newRule != null) {
        accumulateTo.add(newRule);
      }
    }
  }

  static Set<Rule> applyRulesOnRules(Set<Rule> rulesToApply, Set<Rule> targetRules, int at) {
    Set<Rule> result = new LinkedHashSet<Rule>();
    for (Rule targetRule : targetRules) {
      if (targetRule.subst != null) {
        applyRulesOnRuleWithSubst(rulesToApply, targetRule, at, result);
      } else {
        applyRulesOnRuleWithoutSubst(rulesToApply, targetRule, at, result);
      }
    }
    return result;
  }

  private RuleSegment match(Rule other, int at) {
    if (at < 0) {
      throw new IllegalStateException();
    }

    RuleSegment thisAllSegments = new RuleSegment();
    if (backtrack != null) {
      thisAllSegments.addAll(backtrack);
    }
    if (subst != null) {
      thisAllSegments.addAll(subst);
    } else {
      thisAllSegments.addAll(input);
    }
    if (lookAhead != null) {
      thisAllSegments.addAll(lookAhead);
    }

    RuleSegment otherAllSegments = new RuleSegment();
    if (other.backtrack != null) {
      otherAllSegments.addAll(other.backtrack);
    }
    otherAllSegments.addAll(other.input);
    if (other.lookAhead != null) {
      otherAllSegments.addAll(other.lookAhead);
    }

    int backtrackSize = backtrack != null ? backtrack.size() : 0;
    int otherBacktrackSize = other.backtrack != null ? other.backtrack.size() : 0;
    int initialPos = backtrackSize + at - otherBacktrackSize;

    if (initialPos < 0) {
      return null;
    }

    if (thisAllSegments.size() - initialPos < otherAllSegments.size()) {
      return null;
    }

    for(int i = 0; i < otherAllSegments.size(); i++) {
      GlyphGroup thisGlyphs = thisAllSegments.get(i + initialPos);
      GlyphGroup otherGlyphs = otherAllSegments.get(i);

      GlyphGroup intersection = thisGlyphs.intersection(otherGlyphs);
      if (intersection.isEmpty()) {
        return null;
      }
      thisAllSegments.set(i+initialPos, intersection);
    }

    return thisAllSegments;
  }

  static Rule prependToInput(int prefix, Rule other) {
    RuleSegment input = new RuleSegment(prefix);
    input.addAll(other.input);

    return new Rule(other.backtrack, input, other.lookAhead, other.subst);
  }

  static List<Rule> prependToInput(int prefix, List<Rule> rules) {
    List<Rule> result = new ArrayList<Rule>();
    for (Rule rule : rules) {
      result.add(prependToInput(prefix, rule));
    }
    return result;
  }

  static Set<Rule> deltaRules(List<Integer> glyphIds, int delta) {
    Set<Rule> result = new LinkedHashSet<Rule>();
    for (int glyphId : glyphIds) {
      RuleSegment input = new RuleSegment(glyphId);
      RuleSegment subst = new RuleSegment(glyphId + delta);
      result.add(new Rule(null, input, null, subst));
    }
    return result;
  }

  static Set<Rule> oneToOneRules(RuleSegment backtrack, List<Integer> inputs, RuleSegment lookAhead, List<Integer> substs) {
    if (inputs.size() != substs.size()) {
      throw new IllegalArgumentException("input - subst should have same count");
    }

    Set<Rule> result = new LinkedHashSet<Rule>();
    for (int i = 0; i < inputs.size(); i++) {
      RuleSegment input = new RuleSegment(inputs.get(i));
      RuleSegment subst = new RuleSegment(substs.get(i));
      result.add(new Rule(backtrack, input, lookAhead, subst));
    }
    return result;
  }

  static Set<Rule> oneToOneRules(List<Integer> inputs, List<Integer> substs) {
    return oneToOneRules(null, inputs, null, substs);
  }

  static List<Rule> addContextToInputs(
      RuleSegment backtrack, List<RuleSegment> inputs, RuleSegment lookAhead) {
    List<Rule> result = new ArrayList<Rule>();
    for (RuleSegment input : inputs) {
      result.add(new Rule(backtrack, input, lookAhead, null));
    }
    return result;
  }

  // Dump routines

  private static Set<Integer> codepointsFromStr(String s) {
    Set<Integer> list = new HashSet<Integer>();
    for (int cp, i = 0; i < s.length(); i += Character.charCount(cp)) {
      cp = s.codePointAt(i);
      list.add(cp);
    }
    return list;
  }

  public static void dumpRuleMap(Map<Integer, Set<Rule>> rulesList, PostScriptTable post) {
    for (int index : rulesList.keySet()) {
      Set<Rule> rules = rulesList.get(index);
      System.out.println(
          "------------------------------ " + index + " --------------------------------");
      for (Rule rule : rules) {
        System.out.println(toString(rule, post));
      }
    }
  }


  public static void dumpLookups(Font font) {
    GSubTable gsub = font.getTable(Tag.GSUB);
    Map<Integer, Set<Rule>> ruleMap = RuleExtractor.extract(gsub.lookupList());
    PostScriptTable post = font.getTable(Tag.post);
    dumpRuleMap(ruleMap, post);
  }

  static String toString(RuleSegment context, PostScriptTable post) {
    StringBuilder sb = new StringBuilder();
    for (GlyphGroup glyphGroup : context) {
      sb.append(toString(glyphGroup, post));
      sb.append(" ");
    }
    return sb.toString();
  }

  static String toString(GlyphGroup glyphIds, PostScriptTable post) {
    StringBuilder sb = new StringBuilder();
    if (glyphIds.isInverse()) {
      sb.append("not-");
    }
    int glyphCount = glyphIds.size();
    if (glyphCount > 1) {
      sb.append("[ ");
    }
    for (int glyphId : glyphIds) {
      sb.append(glyphId);

      String glyphName = post.glyphName(glyphId);
      if (glyphName != null) {
        sb.append("-");
        sb.append(glyphName);
      }
      sb.append(" ");
    }
    if (glyphCount > 1) {
      sb.append("] ");
    }
    return sb.toString();
  }

  static String toString(Rule rule, PostScriptTable post) {
    StringBuilder sb = new StringBuilder();
    if (rule.backtrack != null && rule.backtrack.size() > 0) {
      sb.append(toString(rule.backtrack, post));
      sb.append("} ");
    }
    sb.append(toString(rule.input, post));
    if (rule.lookAhead != null && rule.lookAhead.size() > 0) {
      sb.append("{ ");
      sb.append(toString(rule.lookAhead, post));
    }
    sb.append("=> ");
    sb.append(toString(rule.subst, post));
    return sb.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (backtrack != null && backtrack.size() > 0) {
      sb.append(backtrack.toString());
      sb.append("} ");
    }
    sb.append(input.toString());
    if (lookAhead != null && lookAhead.size() > 0) {
      sb.append("{ ");
      sb.append(lookAhead.toString());
    }
    sb.append("=> ");
    sb.append(subst.toString());
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Rule)) {
      return false;
    }
    Rule that = (Rule) o;
    RuleSegment[] these = new RuleSegment[] {backtrack, input, lookAhead};
    RuleSegment[] those = new RuleSegment[] {that.backtrack, that.input, that.lookAhead};
    for (int i = 0; i < 3; i++) {
      RuleSegment thisSeg = these[i];
      RuleSegment otherSeg = those[i];
      if (thisSeg != null) {
        if (!thisSeg.equals(otherSeg)) {
          return false;
        }
      } else if (otherSeg != null){
        return false;
      }
    }
    return true;
  }

  // No clue why this hashCode does not work.
  //
  //  @Override
  //  public int hashCode() {
  //    int hashCode = 1;
  //    for (RuleSegment e : new RuleSegment[] {backtrack, input, lookAhead}) {
  //      hashCode = 31*hashCode + (e==null ? 0 : e.hashCode());
  //    }
  //    return hashCode;
  //  }
}