/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.ClassesWithAnnotatedMembersSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.application.util.ReadActionProcessor;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

public class ConfigurationUtil {
    // return true if there is JUnit4 test
    public static boolean findAllTestClasses(final TestClassFilter testClassFilter, @Nullable final Module module, final Set<PsiClass> found) {
        final PsiManager manager = testClassFilter.getPsiManager();

        final Project project = manager.getProject();
        GlobalSearchScope projectScopeWithoutLibraries = GlobalSearchScope.projectScope(project);
        final GlobalSearchScope scope = projectScopeWithoutLibraries.intersectWith(testClassFilter.getScope());

        final PsiClass base = testClassFilter.getBase();
        if (base != null) {
            ClassInheritorsSearch.search(base, scope, true, true, false).forEach(new ReadActionProcessor<PsiClass>() {
                @Override
                public boolean processInReadAction(PsiClass aClass) {
                    if (testClassFilter.isAccepted(aClass)) {
                        found.add(aClass);
                    }
                    return true;
                }
            });
        }

        // classes having suite() method
        final PsiMethod[] suiteMethods = ReadAction.compute(() -> PsiShortNamesCache.getInstance(project).getMethodsByName(JUnitUtil.SUITE_METHOD_NAME, scope));
        for (final PsiMethod method : suiteMethods) {
            ApplicationManager.getApplication().runReadAction(() ->
            {
                final PsiClass containingClass = method.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                if (containingClass instanceof PsiAnonymousClass) {
                    return;
                }
                if (containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }
                if (containingClass.getContainingClass() != null && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
                    return;
                }
                if (JUnitUtil.isSuiteMethod(method) && testClassFilter.isAccepted(containingClass)) {
                    found.add(containingClass);
                }
            });
        }

        Set<PsiClass> processed = new HashSet<>();
        boolean hasJunit4 = addAnnotatedMethodsAnSubclasses(scope, testClassFilter, module, found, processed, JUnitUtil.TEST_ANNOTATION, manager.getProject());
        hasJunit4 |= addAnnotatedMethodsAnSubclasses(scope, testClassFilter, module, found, processed, JUnitUtil.RUN_WITH, manager.getProject());
        return hasJunit4;
    }

    private static boolean addAnnotatedMethodsAnSubclasses(final GlobalSearchScope scope,
                                                           final TestClassFilter testClassFilter,
                                                           @Nullable final Module module,
                                                           final Set<PsiClass> found,
                                                           final Set<PsiClass> processed,
                                                           final String annotation,
                                                           final Project project) {
        // annotated with @Test
        final PsiClass testAnnotation = ReadAction.compute(() -> JavaPsiFacade.getInstance(project).findClass(annotation, GlobalSearchScope.allScope(project)));

        if (testAnnotation == null) {
            return false;
        }

        final SimpleReference<Boolean> isJUnit4 = new SimpleReference<>(Boolean.FALSE);

        GlobalSearchScope allScope = module == null ? GlobalSearchScope.allScope(project) : GlobalSearchScope.moduleRuntimeScope(module, true);
        ClassesWithAnnotatedMembersSearch.search(testAnnotation, allScope).forEach(annotated -> {
            Boolean result = ReadAction.compute(() -> {
                if (!processed.add(annotated)) { // don't process the same class twice regardless of it being in the scope
                    return true;
                }
                final VirtualFile file = PsiUtilCore.getVirtualFile(annotated);
                if (file != null && scope.contains(file) && testClassFilter.isAccepted(annotated)) {
                    if (!found.add(annotated)) {
                        return true;
                    }
                    isJUnit4.set(Boolean.TRUE);
                }

                return null;
            });

            if (result != null) {
                return result;
            }

            ClassInheritorsSearch.search(annotated, scope, true, true, false).forEach(new ReadActionProcessor<PsiClass>() {
                @Override
                public boolean processInReadAction(PsiClass aClass) {
                    if (testClassFilter.isAccepted(aClass)) {
                        found.add(aClass);
                        processed.add(aClass);
                        isJUnit4.set(Boolean.TRUE);
                    }
                    return true;
                }
            });
            return true;
        });

        return isJUnit4.get();
    }
}
