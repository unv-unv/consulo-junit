/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import com.intellij.java.language.projectRoots.roots.ExternalLibraryDescriptor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.testIntegration.JavaTestFramework;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.configuration.ConfigurationType;
import consulo.fileTemplate.FileTemplateDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class JUnit3Framework extends JavaTestFramework
{
    @Nonnull
    @Override
	public String getName()
	{
		return "JUnit3";
	}

	@Override
	public char getMnemonic()
	{
		return '3';
	}

	@Override
	public FileTemplateDescriptor getTestClassFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit3 Test Class.java");
	}

	@Override
	public boolean isSingleConfig()
	{
		return true;
	}

	@Override
	public boolean isSuiteClass(PsiClass psiClass)
	{
		return JUnitUtil.findSuiteMethod(psiClass) != null;
	}

	@Override
	public boolean isTestMethod(PsiMethod method, PsiClass myClass)
	{
		return JUnitUtil.isTestMethod(MethodLocation.elementInClass(method, myClass));
	}

	@Override
	public boolean isMyConfigurationType(ConfigurationType type)
	{
		return type instanceof JUnitConfigurationType;
	}

	@Nonnull
	@Override
	public Image getIcon()
	{
		return PlatformIconGroup.runconfigurationsJunit();
	}

    @Override
	protected String getMarkerClassFQName()
	{
		return "junit.framework.TestCase";
	}

	@Nullable
	@Override
	public ExternalLibraryDescriptor getFrameworkLibraryDescriptor()
	{
		return JUnitExternalLibraryDescriptor.JUNIT3;
	}

	@Nullable
    @Override
	public String getDefaultSuperClass()
	{
		return "junit.framework.TestCase";
	}

    @Override
	public boolean isTestClass(PsiClass clazz, boolean canBePotential)
	{
		if(JUnitUtil.isJUnit3TestClass(clazz))
		{
			return true;
		}
		return JUnitUtil.findSuiteMethod(clazz) != null;
	}

	@Override
	@Nullable
	protected PsiMethod findSetUpMethod(@Nonnull PsiClass clazz)
	{
		if(!JUnitUtil.isJUnit3TestClass(clazz))
		{
			return null;
		}

		for(PsiMethod each : clazz.getMethods())
		{
			if(each.getName().equals("setUp"))
			{
				return each;
			}
		}
		return null;
	}

	@Override
	@Nullable
	protected PsiMethod findTearDownMethod(@Nonnull PsiClass clazz)
	{
		if(!JUnitUtil.isJUnit3TestClass(clazz))
		{
			return null;
		}

		for(PsiMethod each : clazz.getMethods())
		{
			if(each.getName().equals("tearDown"))
			{
				return each;
			}
		}
		return null;
	}

    @Override
	@Nullable
    @RequiredWriteAction
	protected PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException
	{
		final PsiManager manager = clazz.getManager();
		final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

		final PsiMethod patternMethod = createSetUpPatternMethod(factory);

		final PsiClass baseClass = clazz.getSuperClass();
		if(baseClass != null)
		{
			final PsiMethod baseMethod = baseClass.findMethodBySignature(patternMethod, false);
			if(baseMethod != null && baseMethod.isPublic())
			{
				PsiUtil.setModifierProperty(patternMethod, PsiModifier.PROTECTED, false);
				PsiUtil.setModifierProperty(patternMethod, PsiModifier.PUBLIC, true);
			}
		}

		PsiMethod inClass = clazz.findMethodBySignature(patternMethod, false);
		if(inClass == null)
		{
			PsiMethod testMethod = JUnitUtil.findFirstTestMethod(clazz);
			if(testMethod != null)
			{
				return (PsiMethod) clazz.addBefore(patternMethod, testMethod);
			}
			return (PsiMethod) clazz.add(patternMethod);
		}
		else if(inClass.getBody() == null)
		{
			return (PsiMethod) inClass.replace(patternMethod);
		}
		return inClass;
	}

    @Override
	public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit3 SetUp Method.java");
	}

    @Override
	public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit3 TearDown Method.java");
	}

	@Nonnull
    @Override
	public FileTemplateDescriptor getTestMethodFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit3 Test Method.java");
	}

	@Override
	public boolean isTestMethod(PsiElement element, boolean checkAbstract)
	{
		return element instanceof PsiMethod && JUnitUtil.getTestMethod(element, checkAbstract) != null;
	}
}
