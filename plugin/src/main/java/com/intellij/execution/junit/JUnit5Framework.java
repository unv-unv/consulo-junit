// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationFix;
import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.projectRoots.roots.ExternalLibraryDescriptor;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.testIntegration.JavaTestFramework;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.execution.configuration.ConfigurationType;
import consulo.fileTemplate.FileTemplateDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.CommonLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class JUnit5Framework extends JavaTestFramework
{
	@Nonnull
    @Override
	public String getName()
	{
		return "JUnit5";
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
		return JUnitUtil.TEST5_ANNOTATION;
	}

	@Nullable
	@Override
	public ExternalLibraryDescriptor getFrameworkLibraryDescriptor()
	{
		return JUnitExternalLibraryDescriptor.JUNIT5;
	}

	@Nullable
    @Override
	public String getDefaultSuperClass()
	{
		return null;
	}

	@Override
    @RequiredReadAction
    public boolean isTestClass(PsiClass clazz, boolean canBePotential)
	{
		if(canBePotential)
		{
			return isUnderTestSources(clazz);
		}
		return JUnitUtil.isJUnit5TestClass(clazz, false);
	}

	@Nullable
	@Override
	protected PsiMethod findSetUpMethod(@Nonnull PsiClass clazz)
	{
		for(PsiMethod each : clazz.getMethods())
		{
			if(AnnotationUtil.isAnnotated(each, JUnitUtil.BEFORE_EACH_ANNOTATION_NAME, 0))
			{
				return each;
			}
		}
		return null;
	}

	@Nullable
	@Override
	protected PsiMethod findTearDownMethod(@Nonnull PsiClass clazz)
	{
		for(PsiMethod each : clazz.getMethods())
		{
			if(AnnotationUtil.isAnnotated(each, JUnitUtil.AFTER_EACH_ANNOTATION_NAME, 0))
			{
				return each;
			}
		}
		return null;
	}

    @Override
	@Nullable
    @RequiredUIAccess
    @RequiredWriteAction
	protected PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException
	{
		PsiMethod method = findSetUpMethod(clazz);
		if(method != null)
		{
			return method;
		}

		PsiManager manager = clazz.getManager();
		PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

		method = createSetUpPatternMethod(factory);
		PsiMethod existingMethod = clazz.findMethodBySignature(method, false);
		if(existingMethod != null)
		{
			if(AnnotationUtil.isAnnotated(existingMethod, JUnitUtil.BEFORE_ALL_ANNOTATION_NAME, 0))
			{
				return existingMethod;
			}
            int exit = Application.get().isUnitTestMode() ? Messages.OK :
                Messages.showOkCancelDialog(
                    "Method setUp already exist but is not annotated as @BeforeEach. Annotate?",
                    CommonLocalize.titleWarning().get(),
                    UIUtil.getWarningIcon()
                );
			if(exit == Messages.OK)
			{
				new AddAnnotationFix(JUnitUtil.BEFORE_EACH_ANNOTATION_NAME, existingMethod)
                    .invoke(existingMethod.getProject(), null, existingMethod.getContainingFile());
				return existingMethod;
			}
		}
		final PsiMethod testMethod = JUnitUtil.findFirstTestMethod(clazz);
		if(testMethod != null)
		{
			method = (PsiMethod) clazz.addBefore(method, testMethod);
		}
		else
		{
			method = (PsiMethod) clazz.add(method);
		}
		JavaCodeStyleManager.getInstance(manager.getProject()).shortenClassReferences(method);

		return method;
	}

	@Override
	public boolean isIgnoredMethod(PsiElement element)
	{
		final PsiMethod testMethod = element instanceof PsiMethod ? JUnitUtil.getTestMethod(element) : null;
		return testMethod != null && AnnotationUtil.isAnnotated(testMethod, JUnitUtil.IGNORE_ANNOTATION, 0);
	}

	@Override
	public boolean isTestMethod(PsiElement element, boolean checkAbstract)
	{
		return element instanceof PsiMethod && JUnitUtil.getTestMethod(element, checkAbstract) != null;
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

	@Override
	public boolean acceptNestedClasses()
	{
		return true;
	}

    @Override
	public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit5 SetUp Method.java");
	}

    @Override
	public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit5 TearDown Method.java");
	}

    @Nonnull
    @Override
	public FileTemplateDescriptor getTestMethodFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit5 Test Method.java");
	}

	@Override
	public char getMnemonic()
	{
		return '5';
	}

	@Override
	public FileTemplateDescriptor getTestClassFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit5 Test Class.java");
	}
}
