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

package com.facebook.buck.core.rules.pipeline;

import java.util.Optional;

/** Holds rule pipelining state */
public class StateHolder<State extends RulePipelineState> implements AutoCloseable {

  private final Optional<State> state;
  private boolean isFirstStage = false;

  public StateHolder(Optional<State> state) {
    this.state = state;
  }

  private boolean isStateCreated() {
    return state.isPresent();
  }

  /** Returns build rule's pipelining state. */
  public State getState() {
    return state.orElseThrow(
        () -> new IllegalStateException("State could not be created in the current process"));
  }

  @Override
  public void close() {
    if (isStateCreated()) {
      getState().close();
    }
  }

  public boolean isFirstStage() {
    return isFirstStage;
  }

  public void setFirstStage(boolean isFirstStage) {
    this.isFirstStage = isFirstStage;
  }
}
