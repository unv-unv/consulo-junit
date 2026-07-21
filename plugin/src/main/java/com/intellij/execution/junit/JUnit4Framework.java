// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationFix;
import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.projectRoots.roots.ExternalLibraryDescriptor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.testIntegration.JavaTestFramework;
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
public class JUnit4Framework extends JavaTestFramework
{
	@Nonnull
    @Override
	public String getName()
	{
		return "JUnit4";
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
		return JUnitUtil.TEST_ANNOTATION;
	}

	@Nullable
	@Override
	public ExternalLibraryDescriptor getFrameworkLibraryDescriptor()
	{
		return JUnitExternalLibraryDescriptor.JUNIT4;
	}

    @Nullable
    @Override
	public String getDefaultSuperClass()
	{
		return null;
	}

    @Override
	public boolean isTestClass(PsiClass clazz, boolean canBePotential)
	{
		if(canBePotential)
		{
			return isUnderTestSources(clazz);
		}
		return JUnitUtil.isJUnit4TestClass(clazz, false);
	}

	@Nullable
	@Override
	protected PsiMethod findSetUpMethod(@Nonnull PsiClass clazz)
	{
		for(PsiMethod each : clazz.getMethods())
		{
			if(AnnotationUtil.isAnnotated(each, JUnitUtil.BEFORE_ANNOTATION_NAME, 0))
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
			if(AnnotationUtil.isAnnotated(each, JUnitUtil.AFTER_ANNOTATION_NAME, 0))
			{
				return each;
			}
		}
		return null;
	}

	@Nullable
    @Override
	protected PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException
	{
		String beforeClassAnnotationName = JUnitUtil.BEFORE_CLASS_ANNOTATION_NAME;
		String beforeAnnotationName = JUnitUtil.BEFORE_ANNOTATION_NAME;
		return findOrCreateSetUpMethod(clazz, beforeClassAnnotationName, beforeAnnotationName);
	}

    private PsiMethod findOrCreateSetUpMethod(PsiClass clazz, String beforeClassAnnotationName, String beforeAnnotationName)
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
			if(AnnotationUtil.isAnnotated(existingMethod, beforeClassAnnotationName, 0))
			{
				return existingMethod;
			}
            int exit = Application.get().isUnitTestMode() ? Messages.OK :
                Messages.showOkCancelDialog(
                    "Method setUp already exist but is not annotated as @Before. Annotate?",
                    CommonLocalize.titleWarning().get(),
                    UIUtil.getWarningIcon()
                );
			if(exit == Messages.OK)
			{
				new AddAnnotationFix(beforeAnnotationName, existingMethod).invoke(existingMethod.getProject(), null, existingMethod.getContainingFile());
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
	public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit4 SetUp Method.java");
	}

    @Override
	public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit4 TearDown Method.java");
	}

	@Nonnull
    @Override
	public FileTemplateDescriptor getTestMethodFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit4 Test Method.java");
	}

	@Override
	public FileTemplateDescriptor getParametersMethodFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit4 Parameters Method.java");
	}

	@Override
	public char getMnemonic()
	{
		return '4';
	}

	@Override
	public FileTemplateDescriptor getTestClassFileTemplateDescriptor()
	{
		return new FileTemplateDescriptor("JUnit4 Test Class.java");
	}

	@Override
	public boolean isSuiteClass(PsiClass psiClass)
	{
		PsiAnnotation annotation = JUnitUtil.getRunWithAnnotation(psiClass);
		return annotation != null && JUnitUtil.isInheritorOrSelfRunner(annotation, "org.junit.runners.Suite");
	}

	@Override
	public boolean isParameterized(PsiClass clazz)
	{
		PsiAnnotation annotation = JUnitUtil.getRunWithAnnotation(clazz);
		return annotation != null && JUnitUtil.isParameterized(annotation);
	}

	@Override
	public PsiMethod findParametersMethod(PsiClass clazz)
	{
		final PsiMethod[] methods = clazz.getAllMethods();
		for(PsiMethod method : methods)
		{
			if(method.isPublic() && method.isStatic() && AnnotationUtil.isAnnotated(method, "org.junit.runners.Parameterized.Parameters", 0))
			{
				//todo check return value
				return method;
			}
		}
		return null;
	}
}
