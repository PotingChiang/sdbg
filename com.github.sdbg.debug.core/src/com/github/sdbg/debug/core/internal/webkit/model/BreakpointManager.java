/*
 * Copyright (c) 2012, the Dart project authors.
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

package com.github.sdbg.debug.core.internal.webkit.model;

import com.github.sdbg.debug.core.SDBGDebugCorePlugin;
import com.github.sdbg.debug.core.internal.webkit.protocol.WebkitBreakpoint;
import com.github.sdbg.debug.core.internal.webkit.protocol.WebkitCallback;
import com.github.sdbg.debug.core.internal.webkit.protocol.WebkitLocation;
import com.github.sdbg.debug.core.internal.webkit.protocol.WebkitResult;
import com.github.sdbg.debug.core.internal.webkit.protocol.WebkitScript;
import com.github.sdbg.debug.core.model.IResourceResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointListener;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.ILineBreakpoint;

/**
 * Handle adding a removing breakpoints to the WebKit connection for the WebkitDebugTarget class.
 */
public class BreakpointManager implements IBreakpointListener {

//&&&  
//  private static String PACKAGES_DIRECTORY_PATH = "/packages/";
//  private static String LIB_DIRECTORY_PATH = "/lib/";
//
  private WebkitDebugTarget debugTarget;

  private Map<IBreakpoint, List<String>> breakpointToIdMap = new HashMap<IBreakpoint, List<String>>();
  private Map<String, IBreakpoint> breakpointsToUpdateMap = new HashMap<String, IBreakpoint>();

  private List<IBreakpoint> ignoredBreakpoints = new ArrayList<IBreakpoint>();

  public BreakpointManager(WebkitDebugTarget debugTarget) {
    this.debugTarget = debugTarget;
  }

  @Override
  public void breakpointAdded(IBreakpoint breakpoint) {
    if (debugTarget.supportsBreakpoint(breakpoint)) {
      try {
        addBreakpoint(breakpoint);
      } catch (IOException exception) {
        if (!debugTarget.isTerminated()) {
          SDBGDebugCorePlugin.logError(exception);
        }
      }
    }
  }

  @Override
  public void breakpointChanged(IBreakpoint breakpoint, IMarkerDelta delta) {
    // We generate this change event in the handleBreakpointResolved() method - ignore one
    // instance of the event.
    if (ignoredBreakpoints.contains(breakpoint)) {
      ignoredBreakpoints.remove(breakpoint);
      return;
    }

    if (debugTarget.supportsBreakpoint(breakpoint)) {
      breakpointRemoved(breakpoint, delta);
      breakpointAdded(breakpoint);
    }
  }

  @Override
  public void breakpointRemoved(IBreakpoint breakpoint, IMarkerDelta delta) {
    if (debugTarget.supportsBreakpoint(breakpoint)) {
      List<String> breakpointIds = breakpointToIdMap.remove(breakpoint);

      if (breakpointIds != null) {
        for (String breakpointId : breakpointIds) {
          breakpointsToUpdateMap.remove(breakpointId);

          try {
            debugTarget.getWebkitConnection().getDebugger().removeBreakpoint(breakpointId);
          } catch (IOException exception) {
            if (!debugTarget.isTerminated()) {
              SDBGDebugCorePlugin.logError(exception);
            }
          }
        }
      }
    }
  }

  public void connect() throws IOException {
    IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager().getBreakpoints();
    // &&&!!! DartDebugCorePlugin.DEBUG_MODEL_ID);

    for (IBreakpoint breakpoint : breakpoints) {
      if (debugTarget.supportsBreakpoint(breakpoint)) {
        addBreakpoint(breakpoint);
      }
    }

    DebugPlugin.getDefault().getBreakpointManager().addBreakpointListener(this);
  }

  public void dispose(boolean deleteAll) {
    // Null check for when the editor is shutting down.
    if (DebugPlugin.getDefault() != null) {
      if (deleteAll) {
        try {
          for (List<String> ids : breakpointToIdMap.values()) {
            if (ids != null) {
              for (String id : ids) {
                debugTarget.getWebkitConnection().getDebugger().removeBreakpoint(id);
              }
            }
          }
        } catch (IOException exception) {
          if (!debugTarget.isTerminated()) {
            SDBGDebugCorePlugin.logError(exception);
          }
        }
      }

      DebugPlugin.getDefault().getBreakpointManager().removeBreakpointListener(this);
    }
  }

  public IBreakpoint getBreakpointFor(WebkitLocation location) {
    try {
      IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager().getBreakpoints(
          SDBGDebugCorePlugin.DEBUG_MODEL_ID);

      WebkitScript script = debugTarget.getWebkitConnection().getDebugger().getScript(
          location.getScriptId());

      if (script == null) {
        return null;
      }

      String url = script.getUrl();
      int line = WebkitLocation.webkitToElipseLine(location.getLineNumber());

      for (IBreakpoint bp : breakpoints) {
        if (debugTarget.supportsBreakpoint(bp) && bp instanceof ILineBreakpoint) {
          ILineBreakpoint breakpoint = (ILineBreakpoint) bp;

          if (breakpoint.getLineNumber() == line) {
            String bpUrl = getResourceResolver().getUrlForResource(
                breakpoint.getMarker().getResource());

            if (bpUrl != null && bpUrl.equals(url)) {
              return breakpoint;
            }
          }
        }
      }

      return null;
    } catch (CoreException e) {
      throw new RuntimeException(e);
    }
  }

//&&&  
//  @VisibleForTesting
//  public String getPackagePath(String regex, IFile resource) {
//    Path path = new Path(regex);
//    int i = 0;
//    if (regex.indexOf(LIB_DIRECTORY_PATH) != -1) {
//      // remove all segments after "lib", they show path in the package
//      while (i < path.segmentCount() && !path.segment(i).equals("lib")) {
//        i++;
//      }
//    } else {
//      i = 1;
//    }
//    String filePath = regex;
//    if (path.segmentCount() > i + 1) {
//      filePath = new Path(regex).removeLastSegments(path.segmentCount() - (i + 1)).toString();
//    }
//
//    String packagePath = resolvePathToPackage(resource, filePath);
//    if (packagePath != null) {
//      packagePath += "/" + path.removeFirstSegments(i + 1);
//    }
//    return packagePath;
//  }

//&&&  
//  @VisibleForTesting
//  protected String resolvePathToPackage(IFile resource, String filePath) {
//    return DartCore.getProjectManager().resolvePathToPackage(resource, filePath);
//  }

  void addToBreakpointMap(IBreakpoint breakpoint, String id, boolean trackChanges) {
    synchronized (breakpointToIdMap) {
      if (breakpointToIdMap.get(breakpoint) == null) {
        breakpointToIdMap.put(breakpoint, new ArrayList<String>());
      }

      breakpointToIdMap.get(breakpoint).add(id);

      if (trackChanges) {
        breakpointsToUpdateMap.put(id, breakpoint);
      }
    }
  }

  void handleBreakpointResolved(WebkitBreakpoint webkitBreakpoint) {
    try {
      IBreakpoint bp = breakpointsToUpdateMap.get(webkitBreakpoint.getBreakpointId());

      if (bp != null && bp instanceof ILineBreakpoint) {
        ILineBreakpoint breakpoint = (ILineBreakpoint) bp;

        int eclipseLine = WebkitLocation.webkitToElipseLine(webkitBreakpoint.getLocation().getLineNumber());

        if (breakpoint.getLineNumber() != eclipseLine) {
          ignoredBreakpoints.add(breakpoint);

          String message = "[breakpoint in " + breakpoint.getMarker().getResource().getName()
              + " moved from line " + breakpoint.getLineNumber() + " to " + eclipseLine + "]";
          debugTarget.writeToStdout(message);

          breakpoint.getMarker().setAttribute(IMarker.LINE_NUMBER, eclipseLine);
        }
      }
    } catch (CoreException e) {
      throw new RuntimeException(e);
    }
  }

  void handleGlobalObjectCleared() {
    //&&&!!! Breakpoints' cleanup code should be present here?!
    System.out.println("!!!");
  }

  void updateBreakpointsConcerningScript(IStorage script) {
    SourceMapManager sourceMapManager = debugTarget.getSourceMapManager();
    for (IStorage source : sourceMapManager.getSources(script)) {
      for (IBreakpoint breakpoint : new ArrayList<IBreakpoint>(breakpointToIdMap.keySet())) {
        if (source.equals(breakpoint.getMarker().getResource())) {
          breakpointRemoved(breakpoint, null/*delta*/);
          breakpointAdded(breakpoint);
        }
      }
    }
  }

  private void addBreakpoint(final IBreakpoint bp) throws IOException {
    try {
      if (bp.isEnabled() && bp instanceof ILineBreakpoint) {
        final ILineBreakpoint breakpoint = (ILineBreakpoint) bp;

        String regex = getResourceResolver().getUrlRegexForResource(
            breakpoint.getMarker().getResource());

        if (regex == null) {
          return;
        }

        int line = WebkitLocation.eclipseToWebkitLine(breakpoint.getLineNumber());

//&&&      
//      int packagesIndex = regex.indexOf(PACKAGES_DIRECTORY_PATH);
//
//      if (packagesIndex != -1) {
//        // convert xxx/packages/foo/foo.dart to *foo/foo.dart
//        regex = regex.substring(packagesIndex + PACKAGES_DIRECTORY_PATH.length());
//      } else if (isInSelfLinkedLib(breakpoint.getFile())) {
//        // Check if source is located in the "lib" directory; if there is a link to it from the 
//        // packages directory breakpoint should be /packages/...
//        String packageName = DartCore.getSelfLinkedPackageName(breakpoint.getFile());
//
//        if (packageName != null) {
//          // Create a breakpoint for self-links.
//          int libIndex = regex.lastIndexOf(LIB_DIRECTORY_PATH);
//          String packageRegex = packageName + "/"
//              + regex.substring(libIndex + LIB_DIRECTORY_PATH.length());
//
//          debugTarget.getWebkitConnection().getDebugger().setBreakpointByUrl(
//              null,
//              packageRegex,
//              line,
//              -1,
//              new WebkitCallback<String>() {
//                @Override
//                public void handleResult(WebkitResult<String> result) {
//                  if (!result.isError()) {
//                    addToBreakpointMap(breakpoint, result.getResult(), false);
//                  }
//                }
//              });
//        }
//      }
//
        debugTarget.getWebkitConnection().getDebugger().setBreakpointByUrl(
            null,
            regex,
            line,
            -1,
            new WebkitCallback<String>() {
              @Override
              public void handleResult(WebkitResult<String> result) {
                if (!result.isError()) {
                  addToBreakpointMap(breakpoint, result.getResult(), true);
                }
              }
            });

        // Handle source mapped breakpoints - set an additional breakpoint if the file is under
        // source mapping.
        SourceMapManager sourceMapManager = debugTarget.getSourceMapManager();

        if (sourceMapManager.isMapTarget((IFile) breakpoint.getMarker().getResource())) {
          List<SourceMapManager.SourceLocation> locations = sourceMapManager.getReverseMappingsFor(
              (IFile) breakpoint.getMarker().getResource(),
              line);

          for (SourceMapManager.SourceLocation location : locations) {
            String mappedRegex;
            // &&&!!!
            if (location.getStorage() instanceof IFile) {
              mappedRegex = getResourceResolver().getUrlRegexForResource(
                  (IFile) location.getStorage());
            } else if (location.getStorage() != null) {
              mappedRegex = location.getStorage().getFullPath().toPortableString();
            } else {
              mappedRegex = location.getPath();
            }

            if (mappedRegex != null) {
              if (SDBGDebugCorePlugin.LOGGING) {
                System.out.println("breakpoint [" + regex + ","
                    + (breakpoint instanceof ILineBreakpoint ? breakpoint.getLineNumber() : "")
                    + ",-1] ==> mapped to [" + mappedRegex + "," + location.getLine() + ","
                    + location.getColumn() + "]");
              }

              debugTarget.getWebkitConnection().getDebugger().setBreakpointByUrl(
                  null,
                  mappedRegex,
                  location.getLine(),
                  location.getColumn(),
                  new WebkitCallback<String>() {
                    @Override
                    public void handleResult(WebkitResult<String> result) {
                      if (!result.isError()) {
                        addToBreakpointMap(breakpoint, result.getResult(), false);
                      }
                    }
                  });
            }
          }
        }
      }
    } catch (CoreException e) {
      throw new IOException(e);
    }
  }

  private IResourceResolver getResourceResolver() {
    return debugTarget.getResourceResolver();
  }

//&&&  
//  private boolean isInSelfLinkedLib(IFile file) {
//    if (file == null) {
//      return false;
//    }
//
//    return isPubLib(file.getParent());
//  }
//
//  private boolean isPubLib(IContainer container) {
//    if (container.getParent() == null) {
//      return false;
//    }
//
//    if (container.getName().equals("lib")) {
//      if (DartCore.isApplicationDirectory(container.getParent())) {
//        return true;
//      }
//    }
//
//    if (container.getParent() instanceof IWorkspaceRoot) {
//      return false;
//    }
//
//    return isPubLib(container.getParent());
//  }
//
}
