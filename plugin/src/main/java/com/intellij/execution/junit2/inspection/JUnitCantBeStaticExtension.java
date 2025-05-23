/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 30-Nov-2009
 */
package com.intellij.execution.junit2.inspection;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.analysis.codeInspection.CantBeStaticCondition;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JUnitCantBeStaticExtension implements CantBeStaticCondition {
  @Override
  public boolean cantBeStatic(@Nonnull PsiElement member) {
    if (member instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)member;
      if (JUnitUtil.isTestMethodOrConfig(method)) {
        return true;
      }
    }
    return false;
  }
}