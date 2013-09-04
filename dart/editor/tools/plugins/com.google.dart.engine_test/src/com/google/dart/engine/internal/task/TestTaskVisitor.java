/*
 * Copyright (c) 2013, the Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.dart.engine.internal.task;

import com.google.dart.engine.context.AnalysisException;

import junit.framework.Assert;

/**
 * Instances of the class {@code TestTaskVisitor} implement a task visitor that fails if any of its
 * methods are invoked. Subclasses typically override the expected methods to not cause a test
 * failure.
 */
public class TestTaskVisitor<E> implements AnalysisTaskVisitor<E> {
  @Override
  public E visitParseDartTask(ParseDartTask task) throws AnalysisException {
    Assert.fail("Unexpectedly invoked visitParseDartTask");
    return null;
  }

  @Override
  public E visitParseHtmlTask(ParseHtmlTask task) throws AnalysisException {
    Assert.fail("Unexpectedly invoked visitParseHtmlTask");
    return null;
  }

  @Override
  public E visitResolveDartLibraryTask(ResolveDartLibraryTask task) throws AnalysisException {
    Assert.fail("Unexpectedly invoked visitResolveDartLibraryTask");
    return null;
  }

  @Override
  public E visitResolveDartUnitTask(ResolveDartUnitTask task) throws AnalysisException {
    Assert.fail("Unexpectedly invoked visitResolveDartUnitTask");
    return null;
  }

  @Override
  public E visitResolveHtmlTask(ResolveHtmlTask task) throws AnalysisException {
    Assert.fail("Unexpectedly invoked visitResolveHtmlTask");
    return null;
  }
}