/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.junit;

import com.intellij.java.language.projectRoots.LibrariesHelper;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.Library;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.module.content.layer.orderEntry.LibraryDependencyScopeSuggester;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author nik
 */
@ExtensionImpl
public class JUnitDependencyScopeSuggester extends LibraryDependencyScopeSuggester {
    private static final String[] JUNIT_JAR_MARKERS = {
        "org.junit.Test",
        "junit.framework.TestCase",
        "org.hamcrest.Matcher",
        "org.hamcrest.Matchers"
    };

    @Nullable
    @Override
    public DependencyScope getDefaultDependencyScope(@Nonnull Library library) {
        VirtualFile[] files = library.getFiles(BinariesOrderRootType.ID);
        if (files.length == 0) {
            return null;
        }
        for (VirtualFile file : files) {
            if (!isTestJarRoot(file)) {
                return null;
            }
        }
        return DependencyScope.TEST;
    }

    private static boolean isTestJarRoot(VirtualFile file) {
        for (String marker : JUNIT_JAR_MARKERS) {
            if (LibrariesHelper.getInstance().isClassAvailable(new String[]{file.getUrl()}, marker)) {
                return true;
            }
        }
        return false;
    }
}
