/*
 * Copyright (c) Facebook, Inc. and its affiliates.
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

package com.facebook.buck.android;

import com.android.common.sdklib.build.ApkBuilder;
import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.rules.coercer.ManifestEntries;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.util.MoreIterables;
import com.facebook.buck.util.string.MoreStrings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.UnmodifiableIterator;
import java.nio.file.Path;

/**
 * Runs the Android Asset Packaging Tool ({@code aapt}), which creates an {@code .apk} file.
 * Frequently, the {@code pathsToRawFilesDirs} excludes {@code classes.dex}, as {@code classes.dex}
 * will be added separately to the final APK via {@link ApkBuilder}.
 */
public class AaptStep extends ShellStep {

  // aapt, unless specified a pattern, ignores certain files and directories. We follow the same
  // logic as the default pattern found at http://goo.gl/OTTK88 and line 61.
  private static final String DEFAULT_IGNORE_ASSETS_PATTERN =
      "!.gitkeep:!.svn:!.git:" + "!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~";

  // Ignore *.orig files generated by mercurial.
  public static final String IGNORE_ASSETS_PATTERN = DEFAULT_IGNORE_ASSETS_PATTERN + ":*.orig";
  /**
   * Determines whether the default AAPT ignore pattern in {@link
   * AaptStep#DEFAULT_IGNORE_ASSETS_PATTERN} would silently ignore a file.
   *
   * @param path The path of the file we are interested in.
   * @return Whether the file would be silently ignored.
   */
  public static boolean isSilentlyIgnored(Path path) {
    String fileName = path.getFileName().toString();
    return ".gitkeep".equalsIgnoreCase(fileName)
        || ".svn".equalsIgnoreCase(fileName)
        || ".git".equalsIgnoreCase(fileName)
        || ".ds_store".equalsIgnoreCase(fileName)
        || MoreStrings.endsWithIgnoreCase(fileName, ".scc")
        || "cvs".equalsIgnoreCase(fileName)
        || "thumbs.db".equalsIgnoreCase(fileName)
        || "picasa.ini".equalsIgnoreCase(fileName)
        || fileName.endsWith("~");
  }

  private final SourcePathResolverAdapter pathResolver;

  private final AndroidPlatformTarget androidPlatformTarget;
  private final Path androidManifest;
  private final ImmutableList<Path> resDirectories;
  private final ImmutableSortedSet<Path> assetsDirectories;
  private final Path pathToOutputApkFile;
  private final Path pathToRDotTxtDir;
  private final Path pathToGeneratedProguardConfig;
  private final ImmutableList<Path> pathToDependecyResourceApks;

  private final boolean isCrunchPngFiles;
  private final boolean includesVectorDrawables;
  private final ManifestEntries manifestEntries;

  private final ImmutableList<String> additionalAaptParams;

  public AaptStep(
      SourcePathResolverAdapter pathResolver,
      AndroidPlatformTarget androidPlatformTarget,
      AbsPath workingDirectory,
      Path androidManifest,
      ImmutableList<Path> resDirectories,
      ImmutableSortedSet<Path> assetsDirectories,
      Path pathToOutputApkFile,
      Path pathToRDotTxtDir,
      Path pathToGeneratedProguardConfig,
      ImmutableList<Path> pathToDependecyResourceApks,
      boolean isCrunchPngFiles,
      boolean includesVectorDrawables,
      ManifestEntries manifestEntries,
      ImmutableList<String> additionalAaptParams,
      boolean withDownwardApi) {
    super(workingDirectory, withDownwardApi);
    this.pathResolver = pathResolver;
    this.androidPlatformTarget = androidPlatformTarget;
    this.androidManifest = androidManifest;
    this.resDirectories = resDirectories;
    this.assetsDirectories = assetsDirectories;
    this.pathToOutputApkFile = pathToOutputApkFile;
    this.pathToRDotTxtDir = pathToRDotTxtDir;
    this.pathToGeneratedProguardConfig = pathToGeneratedProguardConfig;
    this.pathToDependecyResourceApks = pathToDependecyResourceApks;
    this.isCrunchPngFiles = isCrunchPngFiles;
    this.includesVectorDrawables = includesVectorDrawables;
    this.manifestEntries = manifestEntries;
    this.additionalAaptParams = additionalAaptParams;
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(StepExecutionContext context) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    builder.addAll(androidPlatformTarget.getAaptExecutable().get().getCommandPrefix(pathResolver));
    builder.add("package");

    // verbose flag, if appropriate.
    if (context.getVerbosity().shouldUseVerbosityFlagIfAvailable()) {
      builder.add("-v");
    }

    // Force overwrite of existing files.
    builder.add("-f");

    builder.add("-G", pathToGeneratedProguardConfig.toString());

    // --no-crunch, if appropriate.
    if (!isCrunchPngFiles) {
      builder.add("--no-crunch");
    }

    // Include all of the res/ directories.
    builder.add("--auto-add-overlay");
    for (Path res : MoreIterables.dedupKeepLast(resDirectories)) {
      builder.add("-S", res.toString());
    }

    // Include the assets/ directory, if any.
    for (Path assetDir : assetsDirectories) {
      builder.add("-A", assetDir.toString());
    }

    builder.add("--output-text-symbols").add(pathToRDotTxtDir.toString());
    builder.add("-J").add(pathToRDotTxtDir.toString());

    builder.add("-M").add(androidManifest.toString());
    builder.add("-I", androidPlatformTarget.getAndroidJar().toString());
    builder.add("-F", pathToOutputApkFile.toString());

    builder.add("--ignore-assets", IGNORE_ASSETS_PATTERN);

    if (manifestEntries.getMinSdkVersion().isPresent()) {
      builder.add("--min-sdk-version", manifestEntries.getMinSdkVersion().get().toString());
    }

    if (manifestEntries.getTargetSdkVersion().isPresent()) {
      builder.add("--target-sdk-version", manifestEntries.getTargetSdkVersion().get().toString());
    }

    if (manifestEntries.getVersionCode().isPresent()) {
      builder.add("--version-code", manifestEntries.getVersionCode().get().toString());
    }

    if (manifestEntries.getVersionName().isPresent()) {
      builder.add("--version-name", manifestEntries.getVersionName().get());
    }

    if (manifestEntries.getDebugMode().orElse(false)) {
      builder.add("--debug-mode");
    }

    if (manifestEntries.hasAny()) {
      // Force AAPT to error if the command line version clashes with the hardcoded manifest
      builder.add("--error-on-failed-insert");
    }

    if (includesVectorDrawables) {
      builder.add("--no-version-vectors");
    }

    UnmodifiableIterator<Path> iterator = pathToDependecyResourceApks.iterator();
    if (iterator.hasNext()) {
      builder.add("--feature-of", iterator.next().toString());
    }

    while (iterator.hasNext()) {
      builder.add("--feature-after", iterator.next().toString());
    }

    builder.addAll(additionalAaptParams);

    return builder.build();
  }

  @Override
  public String getShortName() {
    return "aapt_package";
  }
}
