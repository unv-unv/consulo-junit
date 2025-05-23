/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.java.execution.impl.testframework.AbstractInClassConfigurationProducer;
import consulo.annotation.access.RequiredReadAction;
import consulo.execution.action.ConfigurationContext;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.SimpleReference;

//to be deleted in 2018
@Deprecated
public class TestClassConfigurationProducer extends AbstractInClassConfigurationProducer<JUnitConfiguration> {
    public TestClassConfigurationProducer() {
        super(JUnitConfigurationType.getInstance());
    }

    @Override
    @RequiredReadAction
    @SuppressWarnings("RedundantMethodOverride") // binary compatibility
    public boolean isConfigurationFromContext(JUnitConfiguration configuration, ConfigurationContext context) {
        return super.isConfigurationFromContext(configuration, context);
    }

    @Override
    @RequiredReadAction
    @SuppressWarnings("RedundantMethodOverride") // binary compatibility
    protected boolean setupConfigurationFromContext(
        JUnitConfiguration configuration,
        ConfigurationContext context,
        SimpleReference<PsiElement> sourceElement
    ) {
        return super.setupConfigurationFromContext(configuration, context, sourceElement);
    }
}
