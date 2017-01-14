/*
 * Copyright 2017-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.rules;

import static com.facebook.buck.rules.BuildableProperties.Kind.LIBRARY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.step.Step;
import com.facebook.buck.testutil.FakeFileHashCache;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.util.sha1.Sha1HashCode;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class RuleKeyBuilderTest {

  private static final BuildTarget TARGET_1 =
      BuildTargetFactory.newInstance(Paths.get("/root"), "//example/base:one");
  private static final BuildTarget TARGET_2 =
      BuildTargetFactory.newInstance(Paths.get("/root"), "//example/base:one#flavor");
  private static final BuildRule RULE_1 = new EmptyRule(TARGET_1);
  private static final BuildRule RULE_2 = new EmptyRule(TARGET_2);
  private static final RuleKey RULE_KEY_1 = new RuleKey("a002b39af204cdfaa5fdb67816b13867c32ac52c");
  private static final RuleKey RULE_KEY_2 = new RuleKey("b67816b13867c32ac52ca002b39af204cdfaa5fd");

  private static final RuleKeyAppendable APPENDABLE_1 = new FakeRuleKeyAppendable("");
  private static final RuleKeyAppendable APPENDABLE_2 = new FakeRuleKeyAppendable("42");

  private static final Path PATH_1 = Paths.get("path1");
  private static final Path PATH_2 = Paths.get("path2");
  private static final ProjectFilesystem FILESYSTEM = new FakeProjectFilesystem();
  private static final SourcePath SOURCE_PATH_1 = new PathSourcePath(FILESYSTEM, PATH_1);
  private static final SourcePath SOURCE_PATH_2 = new PathSourcePath(FILESYSTEM, PATH_2);
  private static final ArchiveMemberSourcePath ARCHIVE_PATH_1 =
      new ArchiveMemberSourcePath(SOURCE_PATH_1, Paths.get("member"));
  private static final ArchiveMemberSourcePath ARCHIVE_PATH_2 =
      new ArchiveMemberSourcePath(SOURCE_PATH_2, Paths.get("member"));
  private static final BuildTargetSourcePath TARGET_PATH_1 =
      new BuildTargetSourcePath(TARGET_1);
  private static final BuildTargetSourcePath TARGET_PATH_2 =
      new BuildTargetSourcePath(TARGET_2);

  @Test
  public void testUniqueness() {
    // TODO(plamenko): at the moment some types collide with each other, uncomment once fixed.
    String[] fieldKeys = new String[] {"key1", "key2"};
    Object[] fieldValues = new Object[] {
        // Java types
//        null,
        true,
        false,
        0,
        42,
        (long) 0,
        (long) 42,
        (short) 0,
        (short) 42,
//        (byte) 0,
//        (byte) 42,
//        (float) 0,
//        (float) 42,
//        (double) 0,
//        (double) 42,
        "",
        "42",
//        new byte[0],
        new byte[] {42},
        new byte[] {42, 42},
        DummyEnum.BLACK,
        DummyEnum.WHITE,
        //Pattern.compile(""),
        //Pattern.compile("42"),

        // Buck simple types
        Sha1HashCode.of("a002b39af204cdfaa5fdb67816b13867c32ac52c"),
        Sha1HashCode.of("b67816b13867c32ac52ca002b39af204cdfaa5fd"),
        //new SourceRoot(""),
        //new SourceRoot("42"),
        //RULE_KEY_1,
        //RULE_KEY_2,
        //BuildRuleType.of(""),
        //BuildRuleType.of("42"),
        TARGET_1,
        TARGET_2,

        // Buck paths
        new NonHashableSourcePathContainer(SOURCE_PATH_1),
        new NonHashableSourcePathContainer(SOURCE_PATH_2),
        SOURCE_PATH_1,
        SOURCE_PATH_2,
        ARCHIVE_PATH_1,
        ARCHIVE_PATH_2,
        TARGET_PATH_1,
        TARGET_PATH_2,
        SourceWithFlags.of(SOURCE_PATH_1, ImmutableList.of("42")),
        SourceWithFlags.of(SOURCE_PATH_2, ImmutableList.of("42")),

        // Buck rules & appendables
        RULE_1,
        RULE_2,
        APPENDABLE_1,
        APPENDABLE_2,

        // Wrappers
        Suppliers.ofInstance(42),
        //Optional.of(42),
        //Either.ofLeft(42),
        //Either.ofRight(42),

        // Containers & nesting
        ImmutableList.of(42),
        ImmutableList.of(42, 42),
        ImmutableMap.of(42, 42),
        ImmutableList.of(ImmutableList.of(1, 2, 3, 4)),
        ImmutableList.of(ImmutableList.of(1, 2), ImmutableList.of(3, 4)),
    };

    List<RuleKey> ruleKeys = new ArrayList<>();
    List<String> desc = new ArrayList<>();
    ruleKeys.add(newBuilder().build());
    desc.add("<empty>");
    for (String key : fieldKeys) {
      for (Object val : fieldValues) {
        ruleKeys.add(newBuilder().setReflectively(key, val).build());
        desc.add(String.format("{key=%s, val=%s{%s}}", key, val.getClass().getSimpleName(), val));
      }
    }
    // all of the rule keys should be different
    for (int i = 0; i < ruleKeys.size(); i++) {
      for (int j = 0; j < i; j++) {
        assertNotEquals(
            String.format("Collision: %s == %s", desc.get(i), desc.get(j)),
            ruleKeys.get(i),
            ruleKeys.get(j));
      }
    }
  }

  @Test
  public void testNoOp() {
    RuleKey noop = newBuilder().build();
    List<String> list = ImmutableList.of();
    //Map<String, String> map = ImmutableMap.of();
    assertEquals(noop, newBuilder().setReflectively("key", list).build());
    assertEquals(noop, newBuilder().setReflectively("key", list.iterator()).build());
    //assertEquals(noop, newBuilder().setReflectively("key", map).build());
  }

  private RuleKeyBuilder<RuleKey> newBuilder() {
    Map<BuildTarget, BuildRule> ruleMap = ImmutableMap.of(TARGET_1, RULE_1, TARGET_2, RULE_2);
    Map<BuildRule, RuleKey> ruleKeyMap = ImmutableMap.of(RULE_1, RULE_KEY_1, RULE_2, RULE_KEY_2);
    Map<RuleKeyAppendable, RuleKey> appendableKeys =
        ImmutableMap.of(APPENDABLE_1, RULE_KEY_1, APPENDABLE_2, RULE_KEY_2);
    BuildRuleResolver ruleResolver = new FakeBuildRuleResolver(ruleMap);
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(ruleResolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);
    FakeFileHashCache hashCache = new FakeFileHashCache(
        ImmutableMap.of(
            FILESYSTEM.resolve(PATH_1), HashCode.fromInt(0),
            FILESYSTEM.resolve(PATH_2), HashCode.fromInt(42)
        ),
        ImmutableMap.of(
            pathResolver.getAbsoluteArchiveMemberPath(ARCHIVE_PATH_1), HashCode.fromInt(0),
            pathResolver.getAbsoluteArchiveMemberPath(ARCHIVE_PATH_2), HashCode.fromInt(42)
        ),
        ImmutableMap.of());
    RuleKeyHasher<HashCode> hasher = new GuavaRuleKeyHasher(Hashing.sha1().newHasher());
    RuleKeyLogger logger = new NullRuleKeyLogger();
    return new RuleKeyBuilder<RuleKey>(ruleFinder, pathResolver, hashCache, hasher, logger) {
      @Override
      protected RuleKeyBuilder<RuleKey> setBuildRule(BuildRule rule) {
        return setBuildRuleKey(ruleKeyMap.get(rule));
      }
      @Override
      public RuleKey build() {
        return buildRuleKey();
      }
      @Override
      public RuleKeyObjectSink setAppendableRuleKey(String key, RuleKeyAppendable appendable) {
        return setAppendableRuleKey(key, appendableKeys.get(appendable));
      }
    };
  }

  // This ugliness is necessary as we don't have mocks in Buck unit tests.
  private static class FakeBuildRuleResolver extends BuildRuleResolver {
    private final Map<BuildTarget, BuildRule> ruleMap;
    public FakeBuildRuleResolver(Map<BuildTarget, BuildRule> ruleMap) {
      super(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
      this.ruleMap = ruleMap;
    }
    @Override
    public BuildRule getRule(BuildTarget target) {
      return Preconditions.checkNotNull(ruleMap.get(target), "No rule for target: " + target);
    }
  }

  private static class FakeRuleKeyAppendable implements RuleKeyAppendable {

    private final String field;

    public FakeRuleKeyAppendable(String field) {
      this.field = field;
    }

    @Override
    public void appendToRuleKey(RuleKeyObjectSink sink) {
      sink.setReflectively("field", field);
    }

  }

  private static class EmptyRule implements BuildRule {

    private final BuildTarget target;

    public EmptyRule(BuildTarget target) {
      this.target = target;
    }

    @Override
    public BuildTarget getBuildTarget() {
      return target;
    }

    @Override
    public String getFullyQualifiedName() {
      return target.getFullyQualifiedName();
    }

    @Override
    public String getType() {
      return "empty";
    }

    @Override
    public BuildableProperties getProperties() {
      return new BuildableProperties(LIBRARY);
    }

    @Override
    public ImmutableSortedSet<BuildRule> getDeps() {
      return ImmutableSortedSet.of();
    }

    @Override
    public ProjectFilesystem getProjectFilesystem() {
      return new FakeProjectFilesystem();
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext context, BuildableContext buildableContext) {
      throw new UnsupportedOperationException("getBuildSteps");
    }

    @Nullable
    @Override
    public Path getPathToOutput() {
      return null;
    }

    @Override
    public int compareTo(BuildRule o) {
      throw new UnsupportedOperationException("compareTo");
    }

    @Override
    public boolean isCacheable() {
      return true;
    }
  }

  private enum DummyEnum {
    BLACK,
    WHITE,
  }
}
